use crate::{
    error::AppError,
    models::{
        BackupBudget, BackupGoal, BackupImportResponse, BackupIou, BackupPayload, BackupRule,
        BackupTemplate, BackupTransaction, BackupWallet, BulkTransactionsRequest,
        BulkTransactionsResponse, CategoryMomChange, CategorySpending,
        CreateDraftNotificationRequest, CreateTemplateRequest, CreateWalletRequest,
        MerchantRuleRecord, MonthlyComparisonResponse, MonthlyQuery, QuickTemplateRecord,
        RangeSpendingQuery, RangeSpendingResponse, SplitTransactionRequest, TagSpending,
        TopMerchantItem, TransactionRecord, TransferWalletRequest, TransferWalletResponse,
        UpsertMerchantRuleRequest, WalletRecord,
    },
    routes::AppState,
    services::{
        auth::AuthUser,
        reconcile::{compute_dedup_hash, normalize_text},
    },
};
use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    Json,
};
use chrono::Utc;
use rust_decimal::Decimal;
use uuid::Uuid;

pub fn clean_tags(tags: &[String]) -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    for raw in tags {
        let cleaned = raw
            .trim()
            .trim_start_matches('#')
            .to_lowercase()
            .chars()
            .filter(|c| c.is_alphanumeric() || *c == '-' || *c == '_')
            .collect::<String>();
        if !cleaned.is_empty() && !out.contains(&cleaned) {
            out.push(cleaned);
        }
    }
    out
}

async fn resolve_category_id(
    executor: &mut sqlx::PgConnection,
    category_name: Option<&str>,
) -> Result<Uuid, AppError> {
    let name = category_name
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .unwrap_or("Uncategorized");

    if let Some(id) =
        sqlx::query_scalar::<_, Uuid>("SELECT id FROM categories WHERE LOWER(name) = LOWER($1)")
            .bind(name)
            .fetch_optional(&mut *executor)
            .await?
    {
        return Ok(id);
    }

    sqlx::query_scalar::<_, Uuid>("SELECT id FROM categories WHERE LOWER(name) = 'uncategorized'")
        .fetch_optional(&mut *executor)
        .await?
        .ok_or_else(|| AppError::Internal("Default 'Uncategorized' category missing".to_string()))
}

// ================================
// Wallets & Internal Transfers (#2)
// ================================

pub async fn list_wallets(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<Vec<WalletRecord>>, AppError> {
    let rows: Vec<WalletRecord> = sqlx::query_as(
        r#"
        SELECT
            w.id,
            w.name,
            w.wallet_type,
            w.initial_balance,
            w.initial_balance + COALESCE(
                SUM(
                    CASE
                        WHEN t.id IS NULL THEN 0
                        WHEN t.tx_type = 'credit' THEN COALESCE(t.base_amount, t.amount)
                        ELSE -COALESCE(t.base_amount, t.amount)
                    END
                ),
                0
            ) AS current_balance,
            w.currency,
            COUNT(t.id) AS transaction_count
        FROM wallets w
        LEFT JOIN transactions t
          ON t.wallet_id = w.id
         AND t.user_id = w.user_id
         AND t.status = 'confirmed'
        WHERE w.user_id = $1
        GROUP BY w.id
        ORDER BY w.created_at ASC
        "#,
    )
    .bind(auth.user_id)
    .fetch_all(&state.db)
    .await?;

    Ok(Json(rows))
}

pub async fn create_wallet(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<CreateWalletRequest>,
) -> Result<(StatusCode, Json<WalletRecord>), AppError> {
    let name = payload.name.trim();
    if name.is_empty() {
        return Err(AppError::BadRequest("Wallet name is required".to_string()));
    }
    let wallet_type = match payload.wallet_type.as_deref().map(str::trim) {
        Some("cash") | None | Some("") => "cash",
        Some("bank") => "bank",
        Some("credit_card") => "credit_card",
        Some("ewallet") => "ewallet",
        Some("other") => "other",
        Some(other) => {
            return Err(AppError::BadRequest(format!(
                "Invalid wallet_type '{other}'. Expected cash, bank, credit_card, ewallet, or other"
            )));
        }
    };
    let initial_balance = payload.initial_balance.unwrap_or(Decimal::ZERO);
    let currency = payload
        .currency
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(|s| s.to_uppercase())
        .unwrap_or_else(|| state.config.default_currency.clone());

    let row: WalletRecord = sqlx::query_as(
        r#"
        INSERT INTO wallets (user_id, name, wallet_type, initial_balance, currency)
        VALUES ($1, $2, $3, $4, $5)
        RETURNING
            id, name, wallet_type, initial_balance,
            initial_balance AS current_balance,
            currency,
            0::BIGINT AS transaction_count
        "#,
    )
    .bind(auth.user_id)
    .bind(name)
    .bind(wallet_type)
    .bind(initial_balance)
    .bind(currency)
    .fetch_one(&state.db)
    .await
    .map_err(|e| {
        if let sqlx::Error::Database(ref db_err) = e {
            if db_err.is_unique_violation() {
                return AppError::BadRequest(format!("Wallet '{name}' already exists"));
            }
        }
        AppError::from(e)
    })?;

    Ok((StatusCode::CREATED, Json(row)))
}

pub async fn delete_wallet(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, AppError> {
    let res = sqlx::query("DELETE FROM wallets WHERE id = $1 AND user_id = $2")
        .bind(id)
        .bind(auth.user_id)
        .execute(&state.db)
        .await?;
    if res.rows_affected() == 0 {
        return Err(AppError::NotFound(format!("Wallet {id} not found")));
    }
    Ok(StatusCode::NO_CONTENT)
}

pub async fn transfer_wallet(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<TransferWalletRequest>,
) -> Result<(StatusCode, Json<TransferWalletResponse>), AppError> {
    if payload.from_wallet_id == payload.to_wallet_id {
        return Err(AppError::BadRequest(
            "Source and destination wallets must be different".to_string(),
        ));
    }
    if payload.amount <= Decimal::ZERO {
        return Err(AppError::BadRequest(
            "Transfer amount must be greater than zero".to_string(),
        ));
    }

    let mut db_tx = state.db.begin().await?;

    let from_wallet: Option<(String, String)> =
        sqlx::query_as("SELECT name, currency FROM wallets WHERE id = $1 AND user_id = $2")
            .bind(payload.from_wallet_id)
            .bind(auth.user_id)
            .fetch_optional(&mut *db_tx)
            .await?;
    let Some((from_name, from_currency)) = from_wallet else {
        return Err(AppError::NotFound("Source wallet not found".to_string()));
    };

    let to_wallet: Option<(String, String)> =
        sqlx::query_as("SELECT name, currency FROM wallets WHERE id = $1 AND user_id = $2")
            .bind(payload.to_wallet_id)
            .bind(auth.user_id)
            .fetch_optional(&mut *db_tx)
            .await?;
    let Some((to_name, _)) = to_wallet else {
        return Err(AppError::NotFound(
            "Destination wallet not found".to_string(),
        ));
    };

    let tx_date = payload.tx_date.unwrap_or_else(|| Utc::now().date_naive());
    let category_id = resolve_category_id(&mut db_tx, Some("Transfer")).await?;
    let base_amount = state
        .fx
        .convert_to_base(payload.amount, &from_currency, &state.config.default_currency)
        .await;
    let note = payload
        .note
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty());

    let debit_tx: TransactionRecord = sqlx::query_as(
        r#"
        WITH inserted AS (
            INSERT INTO transactions (
                user_id, tx_date, vendor, description, category_id, wallet_id,
                amount, base_amount, tx_type, currency, source, status, is_recurring, is_transfer, tags
            )
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, 'debit', $9, 'manual', 'confirmed', FALSE, TRUE, $10)
            RETURNING *
        )
        SELECT
            i.id, i.document_id, i.tx_date, i.vendor, i.description,
            i.category_id, c.name AS category_name,
            i.wallet_id, w.name AS wallet_name,
            i.amount, i.base_amount, i.tx_type, i.currency, i.source, i.status,
            i.is_recurring, i.is_transfer, i.tags, i.created_at
        FROM inserted i
        LEFT JOIN categories c ON c.id = i.category_id
        LEFT JOIN wallets w ON w.id = i.wallet_id
        "#,
    )
    .bind(auth.user_id)
    .bind(tx_date)
    .bind(format!("Transfer → {to_name}"))
    .bind(note)
    .bind(category_id)
    .bind(payload.from_wallet_id)
    .bind(payload.amount)
    .bind(base_amount)
    .bind(&from_currency)
    .bind(vec!["transfer".to_string()])
    .fetch_one(&mut *db_tx)
    .await?;

    let credit_tx: TransactionRecord = sqlx::query_as(
        r#"
        WITH inserted AS (
            INSERT INTO transactions (
                user_id, tx_date, vendor, description, category_id, wallet_id,
                amount, base_amount, tx_type, currency, source, status, is_recurring, is_transfer, tags
            )
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, 'credit', $9, 'manual', 'confirmed', FALSE, TRUE, $10)
            RETURNING *
        )
        SELECT
            i.id, i.document_id, i.tx_date, i.vendor, i.description,
            i.category_id, c.name AS category_name,
            i.wallet_id, w.name AS wallet_name,
            i.amount, i.base_amount, i.tx_type, i.currency, i.source, i.status,
            i.is_recurring, i.is_transfer, i.tags, i.created_at
        FROM inserted i
        LEFT JOIN categories c ON c.id = i.category_id
        LEFT JOIN wallets w ON w.id = i.wallet_id
        "#,
    )
    .bind(auth.user_id)
    .bind(tx_date)
    .bind(format!("Transfer ← {from_name}"))
    .bind(note)
    .bind(category_id)
    .bind(payload.to_wallet_id)
    .bind(payload.amount)
    .bind(base_amount)
    .bind(&from_currency)
    .bind(vec!["transfer".to_string()])
    .fetch_one(&mut *db_tx)
    .await?;

    db_tx.commit().await?;

    Ok((
        StatusCode::CREATED,
        Json(TransferWalletResponse {
            debit_tx,
            credit_tx,
        }),
    ))
}

// ================================
// Quick-Add Templates (#1)
// ================================

pub async fn list_templates(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<Vec<QuickTemplateRecord>>, AppError> {
    let rows: Vec<QuickTemplateRecord> = sqlx::query_as(
        r#"
        SELECT
            qt.id, qt.label, qt.vendor, qt.amount, qt.tx_type, qt.currency,
            qt.category_id, c.name AS category_name,
            qt.wallet_id, w.name AS wallet_name,
            qt.tags
        FROM quick_templates qt
        LEFT JOIN categories c ON c.id = qt.category_id
        LEFT JOIN wallets w ON w.id = qt.wallet_id
        WHERE qt.user_id = $1
        ORDER BY qt.created_at ASC
        "#,
    )
    .bind(auth.user_id)
    .fetch_all(&state.db)
    .await?;

    Ok(Json(rows))
}

pub async fn create_template(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<CreateTemplateRequest>,
) -> Result<(StatusCode, Json<QuickTemplateRecord>), AppError> {
    let label = payload.label.trim();
    let vendor = payload.vendor.trim();
    if label.is_empty() || vendor.is_empty() {
        return Err(AppError::BadRequest(
            "Template label and vendor are required".to_string(),
        ));
    }
    if payload.amount <= Decimal::ZERO {
        return Err(AppError::BadRequest(
            "Amount must be greater than zero".to_string(),
        ));
    }
    let tx_type = match payload.tx_type.as_deref().map(str::trim) {
        Some(t) if t.eq_ignore_ascii_case("credit") => "credit",
        _ => "debit",
    };
    let currency = payload
        .currency
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(|s| s.to_uppercase())
        .unwrap_or_else(|| state.config.default_currency.clone());
    let tags = clean_tags(&payload.tags);

    let mut db_tx = state.db.begin().await?;
    let category_id = resolve_category_id(&mut db_tx, payload.category_name.as_deref()).await?;

    let row: QuickTemplateRecord = sqlx::query_as(
        r#"
        WITH inserted AS (
            INSERT INTO quick_templates (
                user_id, label, vendor, amount, tx_type, currency, category_id, wallet_id, tags
            )
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
            RETURNING *
        )
        SELECT
            i.id, i.label, i.vendor, i.amount, i.tx_type, i.currency,
            i.category_id, c.name AS category_name,
            i.wallet_id, w.name AS wallet_name,
            i.tags
        FROM inserted i
        LEFT JOIN categories c ON c.id = i.category_id
        LEFT JOIN wallets w ON w.id = i.wallet_id
        "#,
    )
    .bind(auth.user_id)
    .bind(label)
    .bind(vendor)
    .bind(payload.amount)
    .bind(tx_type)
    .bind(currency)
    .bind(category_id)
    .bind(payload.wallet_id)
    .bind(&tags)
    .fetch_one(&mut *db_tx)
    .await?;

    db_tx.commit().await?;
    Ok((StatusCode::CREATED, Json(row)))
}

pub async fn delete_template(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, AppError> {
    let res = sqlx::query("DELETE FROM quick_templates WHERE id = $1 AND user_id = $2")
        .bind(id)
        .bind(auth.user_id)
        .execute(&state.db)
        .await?;
    if res.rows_affected() == 0 {
        return Err(AppError::NotFound(format!("Template {id} not found")));
    }
    Ok(StatusCode::NO_CONTENT)
}

pub async fn use_template(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
) -> Result<(StatusCode, Json<TransactionRecord>), AppError> {
    let tpl: Option<QuickTemplateRecord> = sqlx::query_as(
        r#"
        SELECT
            qt.id, qt.label, qt.vendor, qt.amount, qt.tx_type, qt.currency,
            qt.category_id, c.name AS category_name,
            qt.wallet_id, w.name AS wallet_name,
            qt.tags
        FROM quick_templates qt
        LEFT JOIN categories c ON c.id = qt.category_id
        LEFT JOIN wallets w ON w.id = qt.wallet_id
        WHERE qt.id = $1 AND qt.user_id = $2
        "#,
    )
    .bind(id)
    .bind(auth.user_id)
    .fetch_optional(&state.db)
    .await?;

    let Some(tpl) = tpl else {
        return Err(AppError::NotFound(format!("Template {id} not found")));
    };

    let base_amount = state
        .fx
        .convert_to_base(tpl.amount, &tpl.currency, &state.config.default_currency)
        .await;
    let today = Utc::now().date_naive();

    let record: TransactionRecord = sqlx::query_as(
        r#"
        WITH inserted AS (
            INSERT INTO transactions (
                user_id, tx_date, vendor, description, category_id, wallet_id,
                amount, base_amount, tx_type, currency, source, status, is_recurring, is_transfer, tags
            )
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, 'manual', 'confirmed', FALSE, FALSE, $11)
            RETURNING *
        )
        SELECT
            i.id, i.document_id, i.tx_date, i.vendor, i.description,
            i.category_id, c.name AS category_name,
            i.wallet_id, w.name AS wallet_name,
            i.amount, i.base_amount, i.tx_type, i.currency, i.source, i.status,
            i.is_recurring, i.is_transfer, i.tags, i.created_at
        FROM inserted i
        LEFT JOIN categories c ON c.id = i.category_id
        LEFT JOIN wallets w ON w.id = i.wallet_id
        "#,
    )
    .bind(auth.user_id)
    .bind(today)
    .bind(&tpl.vendor)
    .bind(format!("Quick add: {}", tpl.label))
    .bind(tpl.category_id)
    .bind(tpl.wallet_id)
    .bind(tpl.amount)
    .bind(base_amount)
    .bind(&tpl.tx_type)
    .bind(&tpl.currency)
    .bind(&tpl.tags)
    .fetch_one(&state.db)
    .await?;

    Ok((StatusCode::CREATED, Json(record)))
}

// ================================
// Merchant Auto-Categorization Rules (#6)
// ================================

pub async fn list_rules(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<Vec<MerchantRuleRecord>>, AppError> {
    let rows: Vec<MerchantRuleRecord> = sqlx::query_as(
        r#"
        SELECT
            r.id,
            r.merchant_pattern,
            r.category_id,
            c.name AS category_name,
            r.updated_at
        FROM merchant_rules r
        JOIN categories c ON c.id = r.category_id
        WHERE r.user_id = $1
        ORDER BY r.updated_at DESC
        "#,
    )
    .bind(auth.user_id)
    .fetch_all(&state.db)
    .await?;

    Ok(Json(rows))
}

pub async fn upsert_rule(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<UpsertMerchantRuleRequest>,
) -> Result<Json<MerchantRuleRecord>, AppError> {
    let pattern = normalize_text(&payload.merchant_pattern);
    if pattern.is_empty() {
        return Err(AppError::BadRequest(
            "Merchant pattern is required".to_string(),
        ));
    }

    let mut db_tx = state.db.begin().await?;
    let category_id = resolve_category_id(&mut db_tx, Some(&payload.category_name)).await?;

    let row: MerchantRuleRecord = sqlx::query_as(
        r#"
        WITH upserted AS (
            INSERT INTO merchant_rules (user_id, merchant_pattern, category_id, updated_at)
            VALUES ($1, $2, $3, NOW())
            ON CONFLICT (user_id, merchant_pattern)
            DO UPDATE SET category_id = EXCLUDED.category_id, updated_at = NOW()
            RETURNING *
        )
        SELECT
            u.id, u.merchant_pattern, u.category_id, c.name AS category_name, u.updated_at
        FROM upserted u
        JOIN categories c ON c.id = u.category_id
        "#,
    )
    .bind(auth.user_id)
    .bind(pattern)
    .bind(category_id)
    .fetch_one(&mut *db_tx)
    .await?;

    db_tx.commit().await?;
    Ok(Json(row))
}

pub async fn delete_rule(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, AppError> {
    let res = sqlx::query("DELETE FROM merchant_rules WHERE id = $1 AND user_id = $2")
        .bind(id)
        .bind(auth.user_id)
        .execute(&state.db)
        .await?;
    if res.rows_affected() == 0 {
        return Err(AppError::NotFound(format!("Rule {id} not found")));
    }
    Ok(StatusCode::NO_CONTENT)
}

// ================================
// Tags & Custom Date-Range / YTD Analytics (#5)
// ================================

pub async fn list_tags(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<Vec<TagSpending>>, AppError> {
    let rows: Vec<TagSpending> = sqlx::query_as(
        r#"
        SELECT
            tag,
            COALESCE(SUM(CASE WHEN t.tx_type = 'debit' THEN COALESCE(t.base_amount, t.amount) ELSE 0 END), 0) AS total_spent,
            COUNT(*) AS transaction_count
        FROM transactions t,
             UNNEST(t.tags) AS tag
        WHERE t.user_id = $1
          AND t.status = 'confirmed'
          AND t.is_transfer = FALSE
        GROUP BY tag
        ORDER BY transaction_count DESC, total_spent DESC
        "#,
    )
    .bind(auth.user_id)
    .fetch_all(&state.db)
    .await?;

    Ok(Json(rows))
}

#[derive(sqlx::FromRow)]
struct RangeTotalsRow {
    total_spent: Decimal,
    total_credit: Decimal,
    transaction_count: i64,
}

pub async fn range_spending(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<RangeSpendingQuery>,
) -> Result<Json<RangeSpendingResponse>, AppError> {
    if params.end_date < params.start_date {
        return Err(AppError::BadRequest(
            "end_date must be on or after start_date".to_string(),
        ));
    }
    let tag_filter = params
        .tag
        .as_deref()
        .map(|s| s.trim().trim_start_matches('#').to_lowercase())
        .filter(|s| !s.is_empty());

    let totals: RangeTotalsRow = sqlx::query_as(
        r#"
        SELECT
            COALESCE(SUM(CASE WHEN tx_type = 'debit' THEN COALESCE(base_amount, amount) ELSE 0 END), 0) AS total_spent,
            COALESCE(SUM(CASE WHEN tx_type = 'credit' THEN COALESCE(base_amount, amount) ELSE 0 END), 0) AS total_credit,
            COUNT(*) AS transaction_count
        FROM transactions
        WHERE user_id = $1
          AND status = 'confirmed'
          AND is_transfer = FALSE
          AND tx_date >= $2
          AND tx_date <= $3
          AND ($4::TEXT IS NULL OR $4 = ANY(tags))
          AND ($5::UUID IS NULL OR wallet_id = $5)
        "#,
    )
    .bind(auth.user_id)
    .bind(params.start_date)
    .bind(params.end_date)
    .bind(&tag_filter)
    .bind(params.wallet_id)
    .fetch_one(&state.db)
    .await?;

    let by_category: Vec<CategorySpending> = sqlx::query_as(
        r#"
        SELECT
            t.category_id,
            COALESCE(c.name, 'Uncategorized') AS category_name,
            COALESCE(SUM(COALESCE(t.base_amount, t.amount)), 0) AS total_spent,
            COUNT(*) AS transaction_count
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.user_id = $1
          AND t.status = 'confirmed'
          AND t.is_transfer = FALSE
          AND t.tx_type = 'debit'
          AND t.tx_date >= $2
          AND t.tx_date <= $3
          AND ($4::TEXT IS NULL OR $4 = ANY(t.tags))
          AND ($5::UUID IS NULL OR t.wallet_id = $5)
        GROUP BY t.category_id, c.name
        ORDER BY total_spent DESC
        "#,
    )
    .bind(auth.user_id)
    .bind(params.start_date)
    .bind(params.end_date)
    .bind(&tag_filter)
    .bind(params.wallet_id)
    .fetch_all(&state.db)
    .await?;

    let by_tag: Vec<TagSpending> = sqlx::query_as(
        r#"
        SELECT
            tag,
            COALESCE(SUM(COALESCE(t.base_amount, t.amount)), 0) AS total_spent,
            COUNT(*) AS transaction_count
        FROM transactions t,
             UNNEST(t.tags) AS tag
        WHERE t.user_id = $1
          AND t.status = 'confirmed'
          AND t.is_transfer = FALSE
          AND t.tx_type = 'debit'
          AND t.tx_date >= $2
          AND t.tx_date <= $3
          AND ($4::TEXT IS NULL OR $4 = ANY(t.tags))
          AND ($5::UUID IS NULL OR t.wallet_id = $5)
        GROUP BY tag
        ORDER BY total_spent DESC
        "#,
    )
    .bind(auth.user_id)
    .bind(params.start_date)
    .bind(params.end_date)
    .bind(&tag_filter)
    .bind(params.wallet_id)
    .fetch_all(&state.db)
    .await?;

    Ok(Json(RangeSpendingResponse {
        start_date: params.start_date,
        end_date: params.end_date,
        base_currency: state.config.default_currency.clone(),
        total_spent: totals.total_spent,
        total_credit: totals.total_credit,
        net_balance: totals.total_credit - totals.total_spent,
        transaction_count: totals.transaction_count,
        by_category,
        by_tag,
    }))
}

// ================================
// Full Backup & Restore (JSON) (#7)
// ================================

pub async fn export_backup(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<BackupPayload>, AppError> {
    let categories: Vec<String> =
        sqlx::query_scalar("SELECT name FROM categories ORDER BY name ASC")
            .fetch_all(&state.db)
            .await?;

    let wallets: Vec<BackupWallet> = sqlx::query_as::<_, (String, String, Decimal, String)>(
        "SELECT name, wallet_type, initial_balance, currency FROM wallets WHERE user_id = $1 ORDER BY created_at ASC",
    )
    .bind(auth.user_id)
    .fetch_all(&state.db)
    .await?
    .into_iter()
    .map(|(name, wallet_type, initial_balance, currency)| BackupWallet {
        name,
        wallet_type,
        initial_balance,
        currency,
    })
    .collect();

    let templates: Vec<BackupTemplate> = sqlx::query_as::<_, (String, String, Decimal, String, String, Option<String>, Option<String>, Vec<String>)>(
        r#"
        SELECT qt.label, qt.vendor, qt.amount, qt.tx_type, qt.currency,
               c.name, w.name, qt.tags
        FROM quick_templates qt
        LEFT JOIN categories c ON c.id = qt.category_id
        LEFT JOIN wallets w ON w.id = qt.wallet_id
        WHERE qt.user_id = $1
        ORDER BY qt.created_at ASC
        "#,
    )
    .bind(auth.user_id)
    .fetch_all(&state.db)
    .await?
    .into_iter()
    .map(|(label, vendor, amount, tx_type, currency, category_name, wallet_name, tags)| {
        BackupTemplate {
            label,
            vendor,
            amount,
            tx_type,
            currency,
            category_name,
            wallet_name,
            tags,
        }
    })
    .collect();

    let rules: Vec<BackupRule> = sqlx::query_as::<_, (String, String)>(
        r#"
        SELECT r.merchant_pattern, c.name
        FROM merchant_rules r
        JOIN categories c ON c.id = r.category_id
        WHERE r.user_id = $1
        ORDER BY r.merchant_pattern ASC
        "#,
    )
    .bind(auth.user_id)
    .fetch_all(&state.db)
    .await?
    .into_iter()
    .map(|(merchant_pattern, category_name)| BackupRule {
        merchant_pattern,
        category_name,
    })
    .collect();

    let budgets: Vec<BackupBudget> = sqlx::query_as::<_, (String, Decimal, String)>(
        r#"
        SELECT c.name, b.monthly_limit, b.currency
        FROM budgets b
        JOIN categories c ON c.id = b.category_id
        WHERE b.user_id = $1
        ORDER BY c.name ASC
        "#,
    )
    .bind(auth.user_id)
    .fetch_all(&state.db)
    .await?
    .into_iter()
    .map(|(category_name, monthly_limit, currency)| BackupBudget {
        category_name,
        monthly_limit,
        currency,
    })
    .collect();

    let goals: Vec<BackupGoal> =
        sqlx::query_as::<_, (String, String, Decimal, Decimal, String, Option<chrono::NaiveDate>)>(
            r#"
            SELECT name, goal_type, target_amount, current_amount, currency, target_date
            FROM goals
            WHERE user_id = $1
            ORDER BY created_at ASC
            "#,
        )
        .bind(auth.user_id)
        .fetch_all(&state.db)
        .await?
        .into_iter()
        .map(
            |(name, goal_type, target_amount, current_amount, currency, target_date)| BackupGoal {
                name,
                goal_type,
                target_amount,
                current_amount,
                currency,
                target_date,
            },
        )
        .collect();

    let ious: Vec<BackupIou> =
        sqlx::query_as::<_, (String, String, Decimal, String, Option<String>, bool)>(
            r#"
            SELECT person_name, direction, amount, currency, note, settled
            FROM ious
            WHERE user_id = $1
            ORDER BY created_at ASC
            "#,
        )
        .bind(auth.user_id)
        .fetch_all(&state.db)
        .await?
        .into_iter()
        .map(|(person_name, direction, amount, currency, note, settled)| BackupIou {
            person_name,
            direction,
            amount,
            currency,
            note,
            settled,
        })
        .collect();

    let transactions: Vec<BackupTransaction> = sqlx::query_as::<_, (
        chrono::NaiveDate,
        String,
        Option<String>,
        Option<String>,
        Option<String>,
        Decimal,
        Option<Decimal>,
        String,
        String,
        String,
        bool,
        bool,
        Vec<String>,
    )>(
        r#"
        SELECT t.tx_date, t.vendor, t.description, c.name, w.name,
               t.amount, t.base_amount, t.tx_type, t.currency, t.source,
               t.is_recurring, t.is_transfer, t.tags
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN wallets w ON w.id = t.wallet_id
        WHERE t.user_id = $1
          AND t.status = 'confirmed'
        ORDER BY t.tx_date ASC, t.created_at ASC
        "#,
    )
    .bind(auth.user_id)
    .fetch_all(&state.db)
    .await?
    .into_iter()
    .map(
        |(
            tx_date,
            vendor,
            description,
            category_name,
            wallet_name,
            amount,
            base_amount,
            tx_type,
            currency,
            source,
            is_recurring,
            is_transfer,
            tags,
        )| BackupTransaction {
            tx_date,
            vendor,
            description,
            category_name,
            wallet_name,
            amount,
            base_amount,
            tx_type,
            currency,
            source,
            is_recurring,
            is_transfer,
            tags,
        },
    )
    .collect();

    Ok(Json(BackupPayload {
        version: 1,
        exported_at: Utc::now(),
        categories,
        wallets,
        templates,
        rules,
        budgets,
        goals,
        ious,
        transactions,
    }))
}

pub async fn import_backup(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<BackupPayload>,
) -> Result<Json<BackupImportResponse>, AppError> {
    let mut db_tx = state.db.begin().await?;

    let mut categories_added = 0usize;
    for cat in &payload.categories {
        let trimmed = cat.trim();
        if trimmed.is_empty() {
            continue;
        }
        let exists: Option<Uuid> =
            sqlx::query_scalar("SELECT id FROM categories WHERE LOWER(name) = LOWER($1)")
                .bind(trimmed)
                .fetch_optional(&mut *db_tx)
                .await?;
        if exists.is_none() {
            sqlx::query("INSERT INTO categories (name) VALUES ($1)")
                .bind(trimmed)
                .execute(&mut *db_tx)
                .await?;
            categories_added += 1;
        }
    }

    let mut wallets_added = 0usize;
    for w in &payload.wallets {
        let name = w.name.trim();
        if name.is_empty() {
            continue;
        }
        let res = sqlx::query(
            r#"
            INSERT INTO wallets (user_id, name, wallet_type, initial_balance, currency)
            VALUES ($1, $2, $3, $4, $5)
            ON CONFLICT (user_id, name) DO NOTHING
            "#,
        )
        .bind(auth.user_id)
        .bind(name)
        .bind(&w.wallet_type)
        .bind(w.initial_balance)
        .bind(&w.currency)
        .execute(&mut *db_tx)
        .await?;
        wallets_added += res.rows_affected() as usize;
    }

    let mut rules_upserted = 0usize;
    for r in &payload.rules {
        let pattern = normalize_text(&r.merchant_pattern);
        if pattern.is_empty() {
            continue;
        }
        let cid = resolve_category_id(&mut db_tx, Some(&r.category_name)).await?;
        sqlx::query(
            r#"
            INSERT INTO merchant_rules (user_id, merchant_pattern, category_id, updated_at)
            VALUES ($1, $2, $3, NOW())
            ON CONFLICT (user_id, merchant_pattern)
            DO UPDATE SET category_id = EXCLUDED.category_id, updated_at = NOW()
            "#,
        )
        .bind(auth.user_id)
        .bind(pattern)
        .bind(cid)
        .execute(&mut *db_tx)
        .await?;
        rules_upserted += 1;
    }

    let mut budgets_upserted = 0usize;
    for b in &payload.budgets {
        if b.monthly_limit <= Decimal::ZERO {
            continue;
        }
        let cid = resolve_category_id(&mut db_tx, Some(&b.category_name)).await?;
        sqlx::query(
            r#"
            INSERT INTO budgets (user_id, category_id, monthly_limit, currency)
            VALUES ($1, $2, $3, $4)
            ON CONFLICT (user_id, category_id)
            DO UPDATE SET monthly_limit = EXCLUDED.monthly_limit, currency = EXCLUDED.currency
            "#,
        )
        .bind(auth.user_id)
        .bind(cid)
        .bind(b.monthly_limit)
        .bind(&b.currency)
        .execute(&mut *db_tx)
        .await?;
        budgets_upserted += 1;
    }

    let mut templates_added = 0usize;
    for t in &payload.templates {
        if t.amount <= Decimal::ZERO {
            continue;
        }
        let cid = resolve_category_id(&mut db_tx, t.category_name.as_deref()).await?;
        let wid: Option<Uuid> = if let Some(ref wn) = t.wallet_name {
            sqlx::query_scalar("SELECT id FROM wallets WHERE user_id = $1 AND LOWER(name) = LOWER($2)")
                .bind(auth.user_id)
                .bind(wn.trim())
                .fetch_optional(&mut *db_tx)
                .await?
        } else {
            None
        };
        let exists: Option<Uuid> = sqlx::query_scalar(
            "SELECT id FROM quick_templates WHERE user_id = $1 AND LOWER(label) = LOWER($2)",
        )
        .bind(auth.user_id)
        .bind(t.label.trim())
        .fetch_optional(&mut *db_tx)
        .await?;
        if exists.is_none() {
            sqlx::query(
                r#"
                INSERT INTO quick_templates (
                    user_id, label, vendor, amount, tx_type, currency, category_id, wallet_id, tags
                )
                VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
                "#,
            )
            .bind(auth.user_id)
            .bind(t.label.trim())
            .bind(t.vendor.trim())
            .bind(t.amount)
            .bind(&t.tx_type)
            .bind(&t.currency)
            .bind(cid)
            .bind(wid)
            .bind(clean_tags(&t.tags))
            .execute(&mut *db_tx)
            .await?;
            templates_added += 1;
        }
    }

    let mut goals_added = 0usize;
    for g in &payload.goals {
        if g.target_amount <= Decimal::ZERO {
            continue;
        }
        let exists: Option<Uuid> = sqlx::query_scalar(
            "SELECT id FROM goals WHERE user_id = $1 AND LOWER(name) = LOWER($2)",
        )
        .bind(auth.user_id)
        .bind(g.name.trim())
        .fetch_optional(&mut *db_tx)
        .await?;
        if exists.is_none() {
            sqlx::query(
                r#"
                INSERT INTO goals (user_id, name, goal_type, target_amount, current_amount, currency, target_date)
                VALUES ($1, $2, $3, $4, $5, $6, $7)
                "#,
            )
            .bind(auth.user_id)
            .bind(g.name.trim())
            .bind(&g.goal_type)
            .bind(g.target_amount)
            .bind(g.current_amount)
            .bind(&g.currency)
            .bind(g.target_date)
            .execute(&mut *db_tx)
            .await?;
            goals_added += 1;
        }
    }

    let mut ious_added = 0usize;
    for iou in &payload.ious {
        if iou.amount <= Decimal::ZERO {
            continue;
        }
        let exists: Option<Uuid> = sqlx::query_scalar(
            r#"
            SELECT id FROM ious
            WHERE user_id = $1 AND LOWER(person_name) = LOWER($2) AND amount = $3 AND direction = $4
            "#,
        )
        .bind(auth.user_id)
        .bind(iou.person_name.trim())
        .bind(iou.amount)
        .bind(&iou.direction)
        .fetch_optional(&mut *db_tx)
        .await?;
        if exists.is_none() {
            sqlx::query(
                r#"
                INSERT INTO ious (user_id, person_name, direction, amount, currency, note, settled)
                VALUES ($1, $2, $3, $4, $5, $6, $7)
                "#,
            )
            .bind(auth.user_id)
            .bind(iou.person_name.trim())
            .bind(&iou.direction)
            .bind(iou.amount)
            .bind(&iou.currency)
            .bind(&iou.note)
            .bind(iou.settled)
            .execute(&mut *db_tx)
            .await?;
            ious_added += 1;
        }
    }

    let mut transactions_added = 0usize;
    let mut transactions_skipped = 0usize;
    for tx in &payload.transactions {
        let source = match tx.source.as_str() {
            "receipt_image" | "screenshot" | "bank_statement_pdf" | "manual" => tx.source.as_str(),
            _ => "manual",
        };
        let dedup = compute_dedup_hash(tx.tx_date, &tx.vendor, tx.amount, &tx.tx_type, source);
        let exists: Option<Uuid> = sqlx::query_scalar(
            r#"
            SELECT id FROM transactions
            WHERE user_id = $1
              AND (dedup_hash = $2 OR (tx_date = $3 AND LOWER(vendor) = LOWER($4) AND amount = $5 AND tx_type = $6))
            LIMIT 1
            "#,
        )
        .bind(auth.user_id)
        .bind(&dedup)
        .bind(tx.tx_date)
        .bind(tx.vendor.trim())
        .bind(tx.amount)
        .bind(&tx.tx_type)
        .fetch_optional(&mut *db_tx)
        .await?;

        if exists.is_some() {
            transactions_skipped += 1;
            continue;
        }

        let cid = resolve_category_id(&mut db_tx, tx.category_name.as_deref()).await?;
        let wid: Option<Uuid> = if let Some(ref wn) = tx.wallet_name {
            sqlx::query_scalar(
                "SELECT id FROM wallets WHERE user_id = $1 AND LOWER(name) = LOWER($2)",
            )
            .bind(auth.user_id)
            .bind(wn.trim())
            .fetch_optional(&mut *db_tx)
            .await?
        } else {
            None
        };
        let source = match tx.source.as_str() {
            "receipt_image" | "screenshot" | "bank_statement_pdf" | "manual" => tx.source.as_str(),
            _ => "manual",
        };
        sqlx::query(
            r#"
            INSERT INTO transactions (
                user_id, tx_date, vendor, description, category_id, wallet_id,
                amount, base_amount, tx_type, currency, source, status,
                is_recurring, is_transfer, tags, dedup_hash
            )
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, 'confirmed', $12, $13, $14, $15)
            "#,
        )
        .bind(auth.user_id)
        .bind(tx.tx_date)
        .bind(tx.vendor.trim())
        .bind(&tx.description)
        .bind(cid)
        .bind(wid)
        .bind(tx.amount)
        .bind(tx.base_amount.unwrap_or(tx.amount))
        .bind(&tx.tx_type)
        .bind(&tx.currency)
        .bind(source)
        .bind(tx.is_recurring)
        .bind(tx.is_transfer)
        .bind(clean_tags(&tx.tags))
        .bind(&dedup)
        .execute(&mut *db_tx)
        .await?;
        transactions_added += 1;
    }

    db_tx.commit().await?;

    Ok(Json(BackupImportResponse {
        categories_added,
        wallets_added,
        templates_added,
        rules_upserted,
        budgets_upserted,
        goals_added,
        ious_added,
        transactions_added,
        transactions_skipped,
    }))
}

// ================================
// Split Transaction Across Categories (#5)
// ================================

pub async fn split_transaction(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
    Json(payload): Json<SplitTransactionRequest>,
) -> Result<Json<Vec<TransactionRecord>>, AppError> {
    if payload.items.len() < 2 {
        return Err(AppError::BadRequest(
            "Provide at least 2 split items".to_string(),
        ));
    }
    let mut split_sum = Decimal::ZERO;
    for item in &payload.items {
        if item.amount <= Decimal::ZERO {
            return Err(AppError::BadRequest(
                "Each split amount must be greater than zero".to_string(),
            ));
        }
        split_sum += item.amount;
    }

    let mut db_tx = state.db.begin().await?;

    let orig: Option<TransactionRecord> = sqlx::query_as(
        r#"
        SELECT
            t.id, t.document_id, t.tx_date, t.vendor, t.description,
            t.category_id, c.name AS category_name,
            t.wallet_id, w.name AS wallet_name,
            t.amount, t.base_amount, t.tx_type, t.currency, t.source, t.status,
            t.is_recurring, t.is_transfer, t.tags, t.created_at
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN wallets w ON w.id = t.wallet_id
        WHERE t.id = $1 AND t.user_id = $2
        "#,
    )
    .bind(id)
    .bind(auth.user_id)
    .fetch_optional(&mut *db_tx)
    .await?;

    let Some(orig) = orig else {
        return Err(AppError::NotFound(format!("Transaction {id} not found")));
    };

    let diff = (split_sum - orig.amount).abs();
    if diff > Decimal::new(1, 2) {
        return Err(AppError::BadRequest(format!(
            "Split amounts ({split_sum}) must sum to original amount ({})",
            orig.amount
        )));
    }

    let fx_ratio = if orig.amount > Decimal::ZERO {
        orig.base_amount.unwrap_or(orig.amount) / orig.amount
    } else {
        Decimal::ONE
    };

    let mut result_records: Vec<TransactionRecord> = Vec::with_capacity(payload.items.len());

    // Update original row with items[0]
    let first = &payload.items[0];
    let first_cid = resolve_category_id(&mut db_tx, Some(&first.category_name)).await?;
    let first_base = (first.amount * fx_ratio).round_dp(2);
    let first_desc = first
        .description
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(String::from)
        .or_else(|| orig.description.clone());

    let updated_first: TransactionRecord = sqlx::query_as(
        r#"
        WITH updated AS (
            UPDATE transactions
            SET category_id = $3,
                amount = $4,
                base_amount = $5,
                description = $6
            WHERE id = $1 AND user_id = $2
            RETURNING *
        )
        SELECT
            u.id, u.document_id, u.tx_date, u.vendor, u.description,
            u.category_id, c.name AS category_name,
            u.wallet_id, w.name AS wallet_name,
            u.amount, u.base_amount, u.tx_type, u.currency, u.source, u.status,
            u.is_recurring, u.is_transfer, u.tags, u.created_at
        FROM updated u
        LEFT JOIN categories c ON c.id = u.category_id
        LEFT JOIN wallets w ON w.id = u.wallet_id
        "#,
    )
    .bind(orig.id)
    .bind(auth.user_id)
    .bind(first_cid)
    .bind(first.amount)
    .bind(first_base)
    .bind(&first_desc)
    .fetch_one(&mut *db_tx)
    .await?;
    result_records.push(updated_first);

    // Insert remaining items[1..]
    for item in payload.items.iter().skip(1) {
        let cid = resolve_category_id(&mut db_tx, Some(&item.category_name)).await?;
        let base_amt = (item.amount * fx_ratio).round_dp(2);
        let desc = item
            .description
            .as_deref()
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .map(String::from)
            .or_else(|| orig.description.clone());

        let inserted: TransactionRecord = sqlx::query_as(
            r#"
            WITH inserted AS (
                INSERT INTO transactions (
                    user_id, document_id, tx_date, vendor, description, category_id, wallet_id,
                    amount, base_amount, tx_type, currency, source, status, is_recurring, is_transfer, tags
                )
                VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16)
                RETURNING *
            )
            SELECT
                i.id, i.document_id, i.tx_date, i.vendor, i.description,
                i.category_id, c.name AS category_name,
                i.wallet_id, w.name AS wallet_name,
                i.amount, i.base_amount, i.tx_type, i.currency, i.source, i.status,
                i.is_recurring, i.is_transfer, i.tags, i.created_at
            FROM inserted i
            LEFT JOIN categories c ON c.id = i.category_id
            LEFT JOIN wallets w ON w.id = i.wallet_id
            "#,
        )
        .bind(auth.user_id)
        .bind(orig.document_id)
        .bind(orig.tx_date)
        .bind(&orig.vendor)
        .bind(&desc)
        .bind(cid)
        .bind(orig.wallet_id)
        .bind(item.amount)
        .bind(base_amt)
        .bind(&orig.tx_type)
        .bind(&orig.currency)
        .bind(&orig.source)
        .bind(&orig.status)
        .bind(orig.is_recurring)
        .bind(orig.is_transfer)
        .bind(&orig.tags)
        .fetch_one(&mut *db_tx)
        .await?;
        result_records.push(inserted);
    }

    db_tx.commit().await?;
    Ok(Json(result_records))
}

// ================================
// On-Device Bank Notification Draft Capture (#1)
// ================================

pub async fn create_notification_draft(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<CreateDraftNotificationRequest>,
) -> Result<(StatusCode, Json<TransactionRecord>), AppError> {
    let vendor = payload.vendor.trim();
    if vendor.is_empty() || payload.amount <= Decimal::ZERO {
        return Err(AppError::BadRequest(
            "Valid vendor and positive amount are required".to_string(),
        ));
    }
    let tx_type = match payload.tx_type.as_deref().map(str::trim) {
        Some(t) if t.eq_ignore_ascii_case("credit") => "credit",
        _ => "debit",
    };
    let currency = payload
        .currency
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(|s| s.to_uppercase())
        .unwrap_or_else(|| state.config.default_currency.clone());
    let base_amount = state
        .fx
        .convert_to_base(payload.amount, &currency, &state.config.default_currency)
        .await;
    let dedup = compute_dedup_hash(payload.tx_date, vendor, payload.amount, tx_type, "screenshot");

    let mut db_tx = state.db.begin().await?;

    // Check merchant rule first, else requested category, else Uncategorized
    let norm_vendor = normalize_text(vendor);
    let rule_cat: Option<Uuid> = sqlx::query_scalar(
        "SELECT category_id FROM merchant_rules WHERE user_id = $1 AND merchant_pattern = $2",
    )
    .bind(auth.user_id)
    .bind(&norm_vendor)
    .fetch_optional(&mut *db_tx)
    .await?;

    let category_id = match rule_cat {
        Some(cid) => cid,
        None => resolve_category_id(&mut db_tx, payload.category_name.as_deref()).await?,
    };

    let record: TransactionRecord = sqlx::query_as(
        r#"
        WITH inserted AS (
            INSERT INTO transactions (
                user_id, tx_date, vendor, description, category_id,
                amount, base_amount, tx_type, currency, source, status,
                is_recurring, is_transfer, tags, dedup_hash
            )
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, 'screenshot', 'draft', FALSE, FALSE, $10, $11)
            RETURNING *
        )
        SELECT
            i.id, i.document_id, i.tx_date, i.vendor, i.description,
            i.category_id, c.name AS category_name,
            i.wallet_id, w.name AS wallet_name,
            i.amount, i.base_amount, i.tx_type, i.currency, i.source, i.status,
            i.is_recurring, i.is_transfer, i.tags, i.created_at
        FROM inserted i
        LEFT JOIN categories c ON c.id = i.category_id
        LEFT JOIN wallets w ON w.id = i.wallet_id
        "#,
    )
    .bind(auth.user_id)
    .bind(payload.tx_date)
    .bind(vendor)
    .bind(payload.description.as_deref().map(str::trim).filter(|s| !s.is_empty()))
    .bind(category_id)
    .bind(payload.amount)
    .bind(base_amount)
    .bind(tx_type)
    .bind(&currency)
    .bind(vec!["bank-alert".to_string()])
    .bind(&dedup)
    .fetch_one(&mut *db_tx)
    .await?;

    db_tx.commit().await?;
    Ok((StatusCode::CREATED, Json(record)))
}

// ================================
// Month-over-Month Comparison, Top Merchants, Savings Rate & No-Spend Streak (#2 & #3)
// ================================

pub async fn monthly_comparison(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<MonthlyQuery>,
) -> Result<Json<MonthlyComparisonResponse>, AppError> {
    use chrono::{Datelike, NaiveDate};
    use rust_decimal::prelude::ToPrimitive;
    use std::collections::{HashMap, HashSet};

    let cur_start = NaiveDate::from_ymd_opt(params.year, params.month, 1)
        .ok_or_else(|| AppError::BadRequest("Invalid year/month".to_string()))?;
    let (next_y, next_m) = if params.month == 12 {
        (params.year + 1, 1)
    } else {
        (params.year, params.month + 1)
    };
    let cur_end = NaiveDate::from_ymd_opt(next_y, next_m, 1)
        .ok_or_else(|| AppError::BadRequest("Invalid month rollover".to_string()))?;
    let (prev_y, prev_m) = if params.month == 1 {
        (params.year - 1, 12)
    } else {
        (params.year, params.month - 1)
    };
    let prev_start = NaiveDate::from_ymd_opt(prev_y, prev_m, 1)
        .ok_or_else(|| AppError::BadRequest("Invalid prev month".to_string()))?;

    // Savings rate for current month
    let (total_spent, total_credit): (Decimal, Decimal) = sqlx::query_as(
        r#"
        SELECT
            COALESCE(SUM(CASE WHEN tx_type = 'debit' THEN COALESCE(base_amount, amount) ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN tx_type = 'credit' THEN COALESCE(base_amount, amount) ELSE 0 END), 0)
        FROM transactions
        WHERE user_id = $1 AND status = 'confirmed' AND is_transfer = FALSE
          AND tx_date >= $2 AND tx_date < $3
        "#,
    )
    .bind(auth.user_id)
    .bind(cur_start)
    .bind(cur_end)
    .fetch_one(&state.db)
    .await?;

    let savings_rate_pct = if total_credit > Decimal::ZERO {
        let rate = ((total_credit - total_spent) / total_credit) * Decimal::from(100);
        rate.round_dp(1).to_f64()
    } else {
        None
    };

    // Days with any debit spend in current month (for no-spend days & streak)
    let spend_dates: Vec<NaiveDate> = sqlx::query_scalar(
        r#"
        SELECT DISTINCT tx_date
        FROM transactions
        WHERE user_id = $1 AND status = 'confirmed' AND is_transfer = FALSE
          AND tx_type = 'debit' AND tx_date >= $2 AND tx_date < $3
        "#,
    )
    .bind(auth.user_id)
    .bind(cur_start)
    .bind(cur_end)
    .fetch_all(&state.db)
    .await?;

    let spend_set: HashSet<NaiveDate> = spend_dates.into_iter().collect();
    let today = Utc::now().date_naive();
    let last_day_in_month = cur_end - chrono::Duration::days(1);
    let eval_end = if today < cur_start {
        cur_start - chrono::Duration::days(1)
    } else if today < last_day_in_month {
        today
    } else {
        last_day_in_month
    };

    let mut no_spend_days: i64 = 0;
    let mut current_no_spend_streak: i64 = 0;
    if eval_end >= cur_start {
        let mut d = cur_start;
        while d <= eval_end {
            if !spend_set.contains(&d) {
                no_spend_days += 1;
            }
            d += chrono::Duration::days(1);
        }
        let mut back = eval_end;
        while back >= cur_start && !spend_set.contains(&back) {
            current_no_spend_streak += 1;
            back -= chrono::Duration::days(1);
        }
    }

    // Category spending current vs previous month
    let cur_cats: Vec<(String, Decimal)> = sqlx::query_as(
        r#"
        SELECT COALESCE(c.name, 'Uncategorized') AS name,
               COALESCE(SUM(COALESCE(t.base_amount, t.amount)), 0) AS total
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.user_id = $1 AND t.status = 'confirmed' AND t.is_transfer = FALSE
          AND t.tx_type = 'debit' AND t.tx_date >= $2 AND t.tx_date < $3
        GROUP BY 1
        "#,
    )
    .bind(auth.user_id)
    .bind(cur_start)
    .bind(cur_end)
    .fetch_all(&state.db)
    .await?;

    let prev_cats: Vec<(String, Decimal)> = sqlx::query_as(
        r#"
        SELECT COALESCE(c.name, 'Uncategorized') AS name,
               COALESCE(SUM(COALESCE(t.base_amount, t.amount)), 0) AS total
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.user_id = $1 AND t.status = 'confirmed' AND t.is_transfer = FALSE
          AND t.tx_type = 'debit' AND t.tx_date >= $2 AND t.tx_date < $3
        GROUP BY 1
        "#,
    )
    .bind(auth.user_id)
    .bind(prev_start)
    .bind(cur_start)
    .fetch_all(&state.db)
    .await?;

    let mut map: HashMap<String, (Decimal, Decimal)> = HashMap::new();
    for (name, amt) in cur_cats {
        map.entry(name).or_insert((Decimal::ZERO, Decimal::ZERO)).0 = amt;
    }
    for (name, amt) in prev_cats {
        map.entry(name).or_insert((Decimal::ZERO, Decimal::ZERO)).1 = amt;
    }

    let mut category_changes: Vec<CategoryMomChange> = map
        .into_iter()
        .map(|(category_name, (cur, prev))| {
            let diff = cur - prev;
            let pct_change = if prev > Decimal::ZERO {
                ((diff / prev) * Decimal::from(100)).round_dp(1).to_f64()
            } else {
                None
            };
            CategoryMomChange {
                category_name,
                current_spent: cur,
                previous_spent: prev,
                diff,
                pct_change,
            }
        })
        .collect();
    category_changes.sort_by(|a, b| b.diff.abs().cmp(&a.diff.abs()));
    category_changes.truncate(8);

    // Top 5 debit merchants
    let top_merchants: Vec<TopMerchantItem> = sqlx::query_as(
        r#"
        SELECT
            MIN(vendor) AS vendor,
            COALESCE(SUM(COALESCE(base_amount, amount)), 0) AS total_spent,
            COUNT(*) AS transaction_count
        FROM transactions
        WHERE user_id = $1 AND status = 'confirmed' AND is_transfer = FALSE
          AND tx_type = 'debit' AND tx_date >= $2 AND tx_date < $3
        GROUP BY LOWER(TRIM(vendor))
        ORDER BY total_spent DESC
        LIMIT 5
        "#,
    )
    .bind(auth.user_id)
    .bind(cur_start)
    .bind(cur_end)
    .fetch_all(&state.db)
    .await?;

    let _ = today.year(); // keep Datelike used cleanly if needed
    Ok(Json(MonthlyComparisonResponse {
        year: params.year,
        month: params.month,
        base_currency: state.config.default_currency.clone(),
        savings_rate_pct,
        no_spend_days,
        current_no_spend_streak,
        category_changes,
        top_merchants,
    }))
}

// ================================
// Bulk Transaction Actions (#4)
// ================================

pub async fn bulk_transactions(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<BulkTransactionsRequest>,
) -> Result<Json<BulkTransactionsResponse>, AppError> {
    if payload.ids.is_empty() {
        return Err(AppError::BadRequest(
            "Provide at least one transaction ID".to_string(),
        ));
    }

    if payload.action.eq_ignore_ascii_case("delete") {
        let res = sqlx::query("DELETE FROM transactions WHERE user_id = $1 AND id = ANY($2)")
            .bind(auth.user_id)
            .bind(&payload.ids)
            .execute(&state.db)
            .await?;
        return Ok(Json(BulkTransactionsResponse {
            affected_count: res.rows_affected() as usize,
        }));
    }

    if !payload.action.eq_ignore_ascii_case("update") {
        return Err(AppError::BadRequest(
            "action must be 'update' or 'delete'".to_string(),
        ));
    }

    let mut db_tx = state.db.begin().await?;

    let resolved_cat: Option<Uuid> = if let Some(ref cname) = payload.category_name {
        if !cname.trim().is_empty() {
            Some(resolve_category_id(&mut db_tx, Some(cname)).await?)
        } else {
            None
        }
    } else {
        None
    };

    let cleaned_tag: Option<String> = payload
        .add_tag
        .as_deref()
        .and_then(|t| clean_tags(&[t.to_string()]).into_iter().next());

    let res = sqlx::query(
        r#"
        UPDATE transactions
        SET category_id = COALESCE($3, category_id),
            wallet_id   = COALESCE($4, wallet_id),
            tags        = CASE
                            WHEN $5::TEXT IS NOT NULL AND NOT ($5 = ANY(tags))
                            THEN array_append(tags, $5)
                            ELSE tags
                          END
        WHERE user_id = $1
          AND id = ANY($2)
        "#,
    )
    .bind(auth.user_id)
    .bind(&payload.ids)
    .bind(resolved_cat)
    .bind(payload.wallet_id)
    .bind(&cleaned_tag)
    .execute(&mut *db_tx)
    .await?;

    db_tx.commit().await?;

    Ok(Json(BulkTransactionsResponse {
        affected_count: res.rows_affected() as usize,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn clean_tags_normalizes_and_deduplicates() {
        let input = vec![
            "#Trip-Dalat".to_string(),
            " trip-dalat ".to_string(),
            "Work_2026!".to_string(),
            "   ".to_string(),
        ];
        assert_eq!(clean_tags(&input), vec!["trip-dalat", "work_2026"]);
    }
}
