use crate::{
    error::AppError,
    models::{DraftTransactionPreview, IngestResponse},
    routes::AppState,
    services::{
        auth::AuthUser,
        csv_parser::try_parse_bank_csv,
        gemini::MediaPart,
        pdf::decrypt_pdf_if_needed,
        reconcile::{compute_dedup_hash, compute_file_hash, normalize_text, reconcile_statement},
    },
};
use axum::{
    extract::{Multipart, State},
    Json,
};
use chrono::{Duration, NaiveDate, Utc};
use std::collections::HashMap;
use uuid::Uuid;

pub async fn ingest_media(
    State(state): State<AppState>,
    auth: AuthUser,
    mut multipart: Multipart,
) -> Result<Json<IngestResponse>, AppError> {
    let mut raw_files: Vec<(String, Vec<u8>)> = Vec::new();
    let mut pdf_password: Option<String> = None;
    let mut source_type_hint: Option<String> = None;

    while let Some(field) = multipart
        .next_field()
        .await
        .map_err(|e| AppError::BadRequest(format!("Invalid multipart payload: {e}")))?
    {
        let field_name = field.name().unwrap_or_default().to_string();

        match field_name.as_str() {
            "pdf_password" => {
                let txt = field
                    .text()
                    .await
                    .map_err(|e| AppError::BadRequest(format!("Invalid pdf_password field: {e}")))?;
                if !txt.trim().is_empty() {
                    pdf_password = Some(txt);
                }
            }
            "source_type" => {
                let txt = field
                    .text()
                    .await
                    .map_err(|e| AppError::BadRequest(format!("Invalid source_type field: {e}")))?;
                if !txt.trim().is_empty() {
                    source_type_hint = Some(txt.trim().to_string());
                }
            }
            "files" | "files[]" | "file" => {
                let file_name = field.file_name().unwrap_or_default().to_string();
                let content_type = field.content_type().map(|s| s.to_string());
                let bytes = field
                    .bytes()
                    .await
                    .map_err(|e| AppError::BadRequest(format!("Failed reading uploaded file: {e}")))?
                    .to_vec();

                if bytes.is_empty() {
                    continue;
                }

                let mime = resolve_mime_type(content_type.as_deref(), &file_name);
                raw_files.push((mime, bytes));
            }
            _ => {
                // Ignore unknown form fields
            }
        }
    }

    if raw_files.is_empty() {
        return Err(AppError::BadRequest(
            "At least one file must be uploaded in field `files` or `files[]`".to_string(),
        ));
    }

    // Compute deterministic SHA-256 hash of uploaded file(s) before decryption
    let byte_slices: Vec<&[u8]> = raw_files.iter().map(|(_, b)| b.as_slice()).collect();
    let file_hash = compute_file_hash(&byte_slices);

    // Reject duplicate file uploads early (scoped per user)
    let existing_doc: Option<Uuid> =
        sqlx::query_scalar("SELECT id FROM documents WHERE user_id = $1 AND file_hash = $2")
            .bind(auth.user_id)
            .bind(&file_hash)
            .fetch_optional(&state.db)
            .await?;

    if let Some(doc_id) = existing_doc {
        return Err(AppError::Conflict(format!(
            "Identical file(s) have already been ingested (document_id={doc_id})"
        )));
    }

    // Determine source_type & transaction source
    let source_type = resolve_source_type(source_type_hint.as_deref(), &raw_files)?;
    let tx_source = map_source_type_to_tx_source(&source_type);

    // Pre-process files (decrypt PDFs in-memory if password-protected)
    let mut media_parts: Vec<MediaPart> = Vec::with_capacity(raw_files.len());
    for (mime, bytes) in raw_files {
        let processed_bytes = if mime == "application/pdf" {
            decrypt_pdf_if_needed(&bytes, pdf_password.as_deref())?
        } else {
            bytes
        };
        media_parts.push(MediaPart {
            mime_type: mime,
            bytes: processed_bytes,
        });
    }

    // Fetch allowed categories from DB
    let category_rows: Vec<(Uuid, String)> =
        sqlx::query_as("SELECT id, name FROM categories ORDER BY name")
            .fetch_all(&state.db)
            .await?;

    let mut category_lookup: HashMap<String, (Uuid, String)> = HashMap::new();
    let mut allowed_categories: Vec<String> = Vec::with_capacity(category_rows.len());
    for (id, name) in category_rows {
        allowed_categories.push(name.clone());
        category_lookup.insert(name.to_lowercase(), (id, name));
    }

    let fallback_category = category_lookup
        .get("uncategorized")
        .cloned()
        .ok_or_else(|| {
            AppError::Internal("Default 'Uncategorized' category missing from database".to_string())
        })?;

    // Load user's Smart Merchant Memory rules (merchant_pattern -> (category_id, category_name))
    let rule_rows: Vec<(String, Uuid, String)> = sqlx::query_as(
        r#"
        SELECT r.merchant_pattern, r.category_id, c.name
        FROM merchant_rules r
        JOIN categories c ON c.id = r.category_id
        WHERE r.user_id = $1
        "#,
    )
    .bind(auth.user_id)
    .fetch_all(&state.db)
    .await?;
    let merchant_rule_map: HashMap<String, (Uuid, String)> = rule_rows
        .into_iter()
        .map(|(pat, cid, cname)| (pat, (cid, cname)))
        .collect();

    // Fast path: parse standard bank CSV directly in Rust (0 Gemini quota used!)
    let local_csv = if media_parts.len() == 1 && media_parts[0].mime_type == "text/csv" {
        try_parse_bank_csv(&media_parts[0].bytes, &state.config.default_currency)
    } else {
        None
    };

    // Otherwise extract structured transactions via Gemini (images -> Flash-Lite, PDFs -> Flash)
    let (extraction, model_used) = if let Some(parsed) = local_csv {
        (parsed, "csv-parser-local".to_string())
    } else {
        state
            .gemini
            .extract_transactions(&media_parts, &source_type, &allowed_categories)
            .await?
    };

    // Statement math reconciliation check
    let reconciliation = reconcile_statement(
        extraction.opening_balance,
        extraction.closing_balance,
        &extraction.transactions,
    );

    // Persist document and draft transactions atomically
    let mut db_tx = state.db.begin().await?;

    let document_id: Uuid = sqlx::query_scalar(
        r#"
        INSERT INTO documents (user_id, source_type, file_hash, opening_balance, closing_balance, reconciled)
        VALUES ($1, $2, $3, $4, $5, $6)
        RETURNING id
        "#,
    )
    .bind(auth.user_id)
    .bind(&source_type)
    .bind(&file_hash)
    .bind(extraction.opening_balance)
    .bind(extraction.closing_balance)
    .bind(reconciliation.reconciled)
    .fetch_one(&mut *db_tx)
    .await?;

    let mut previews: Vec<DraftTransactionPreview> = Vec::new();
    let mut skipped_exact_duplicates: usize = 0;

    for extracted in &extraction.transactions {
        let tx_type = if extracted.tx_type.eq_ignore_ascii_case("credit") {
            "credit"
        } else {
            "debit"
        };

        let tx_date = NaiveDate::parse_from_str(extracted.date.trim(), "%Y-%m-%d")
            .unwrap_or_else(|_| {
                tracing::warn!(raw_date = %extracted.date, "Unparseable date from AI; defaulting to today");
                Utc::now().date_naive()
            });

        let amount = extracted.amount.abs();
        let currency = extracted
            .currency
            .as_deref()
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .map(|s| s.to_uppercase())
            .unwrap_or_else(|| state.config.default_currency.clone());

        let desc_for_hash = extracted
            .description
            .as_deref()
            .filter(|s| !s.trim().is_empty())
            .unwrap_or(&extracted.vendor);

        let dedup_hash = compute_dedup_hash(tx_date, desc_for_hash, amount, tx_type, tx_source);

        // 1. Exact duplicate check via deterministic dedup_hash (scoped per user)
        let exact_exists: Option<Uuid> = sqlx::query_scalar(
            "SELECT id FROM transactions WHERE user_id = $1 AND dedup_hash = $2",
        )
        .bind(auth.user_id)
        .bind(&dedup_hash)
        .fetch_optional(&mut *db_tx)
        .await?;

        if exact_exists.is_some() {
            skipped_exact_duplicates += 1;
            continue;
        }

        // 2. Fuzzy duplicate check (±2 days, exact same amount & type, scoped per user)
        let window_start = tx_date - Duration::days(2);
        let window_end = tx_date + Duration::days(2);
        let fuzzy_matches: Vec<Uuid> = sqlx::query_scalar(
            r#"
            SELECT id
            FROM transactions
            WHERE user_id = $1
              AND tx_type = $2
              AND amount = $3
              AND tx_date BETWEEN $4 AND $5
            "#,
        )
        .bind(auth.user_id)
        .bind(tx_type)
        .bind(amount)
        .bind(window_start)
        .bind(window_end)
        .fetch_all(&mut *db_tx)
        .await?;

        let status = if fuzzy_matches.is_empty() {
            "draft"
        } else {
            "duplicate_flagged"
        };

        let base_amount = state
            .fx
            .convert_to_base(amount, &currency, &state.config.default_currency)
            .await;

        let norm_vendor = normalize_text(&extracted.vendor);
        let (category_id, category_name) = merchant_rule_map
            .get(&norm_vendor)
            .cloned()
            .or_else(|| {
                category_lookup
                    .get(&extracted.category.trim().to_lowercase())
                    .cloned()
            })
            .unwrap_or_else(|| fallback_category.clone());

        let tx_id: Uuid = sqlx::query_scalar(
            r#"
            INSERT INTO transactions (
                user_id, document_id, tx_date, vendor, description, category_id,
                amount, base_amount, tx_type, currency, source, status, dedup_hash
            )
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
            RETURNING id
            "#,
        )
        .bind(auth.user_id)
        .bind(document_id)
        .bind(tx_date)
        .bind(extracted.vendor.trim())
        .bind(extracted.description.as_deref().map(str::trim))
        .bind(category_id)
        .bind(amount)
        .bind(base_amount)
        .bind(tx_type)
        .bind(&currency)
        .bind(tx_source)
        .bind(status)
        .bind(&dedup_hash)
        .fetch_one(&mut *db_tx)
        .await?;

        previews.push(DraftTransactionPreview {
            id: tx_id,
            tx_date,
            vendor: extracted.vendor.trim().to_string(),
            description: extracted.description.clone(),
            category_id,
            category_name,
            amount,
            tx_type: tx_type.to_string(),
            currency,
            source: tx_source.to_string(),
            status: status.to_string(),
            confidence: extracted.confidence,
            potential_duplicate_ids: fuzzy_matches,
        });
    }

    db_tx.commit().await?;

    Ok(Json(IngestResponse {
        document_id,
        source_type,
        model_used,
        opening_balance: extraction.opening_balance,
        closing_balance: extraction.closing_balance,
        reconciled: reconciliation.reconciled,
        reconciliation_diff: reconciliation.diff,
        transactions: previews,
        skipped_exact_duplicates,
    }))
}

fn resolve_mime_type(content_type: Option<&str>, file_name: &str) -> String {
    if let Some(ct) = content_type {
        let base = ct.split(';').next().unwrap_or(ct).trim().to_lowercase();
        if !base.is_empty() && base != "application/octet-stream" {
            return base;
        }
    }

    let lower = file_name.to_lowercase();
    if lower.ends_with(".pdf") {
        "application/pdf".to_string()
    } else if lower.ends_with(".csv") {
        "text/csv".to_string()
    } else if lower.ends_with(".png") {
        "image/png".to_string()
    } else if lower.ends_with(".webp") {
        "image/webp".to_string()
    } else {
        "image/jpeg".to_string()
    }
}

fn resolve_source_type(
    hint: Option<&str>,
    files: &[(String, Vec<u8>)],
) -> Result<String, AppError> {
    if let Some(h) = hint {
        return match h {
            "bill_image" | "screenshot" | "bank_statement_pdf" | "bank_statement_csv" => {
                Ok(h.to_string())
            }
            other => Err(AppError::BadRequest(format!(
                "Unsupported source_type '{other}'. Expected one of: bill_image, screenshot, bank_statement_pdf, bank_statement_csv"
            ))),
        };
    }

    if files.iter().any(|(m, _)| m == "application/pdf") {
        Ok("bank_statement_pdf".to_string())
    } else if files.iter().any(|(m, _)| m == "text/csv") {
        Ok("bank_statement_csv".to_string())
    } else {
        Ok("bill_image".to_string())
    }
}

fn map_source_type_to_tx_source(source_type: &str) -> &'static str {
    match source_type {
        "screenshot" => "screenshot",
        "bank_statement_pdf" | "bank_statement_csv" => "bank_statement",
        _ => "bill",
    }
}
