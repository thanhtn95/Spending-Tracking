use crate::{
    error::AppError,
    models::{
        BudgetRecord, CategoryRecord, CategorySpending, ConfirmTransactionsRequest,
        ConfirmTransactionsResponse, CreateCategoryRequest, CreateTransactionRequest,
        ListTransactionsQuery, MonthlyQuery, MonthlySpendingResponse, MonthlyTrendPoint,
        RecurringBillItem, RecurringSummaryResponse, TransactionRecord, TrendQuery,
        UpdateTransactionRequest, UpsertBudgetRequest, ZeroBalanceRequest, ZeroBalanceResponse,
    },
    routes::AppState,
    services::{auth::AuthUser, reconcile::normalize_text},
};
use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    Json,
};
use chrono::{Datelike, NaiveDate, Utc};
use rust_decimal::Decimal;
use uuid::Uuid;

async fn save_merchant_rule(
    executor: &mut sqlx::PgConnection,
    user_id: Uuid,
    vendor: &str,
    category_id: Uuid,
) -> Result<(), sqlx::Error> {
    let pattern = normalize_text(vendor);
    if pattern.is_empty() {
        return Ok(());
    }
    sqlx::query(
        r#"
        INSERT INTO merchant_rules (user_id, merchant_pattern, category_id, updated_at)
        VALUES ($1, $2, $3, NOW())
        ON CONFLICT (user_id, merchant_pattern)
        DO UPDATE SET category_id = EXCLUDED.category_id, updated_at = NOW()
        "#,
    )
    .bind(user_id)
    .bind(pattern)
    .bind(category_id)
    .execute(executor)
    .await?;
    Ok(())
}

pub async fn confirm_transactions(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<ConfirmTransactionsRequest>,
) -> Result<Json<ConfirmTransactionsResponse>, AppError> {
    if payload.transactions.is_empty() && payload.discard_ids.is_empty() {
        return Err(AppError::BadRequest(
            "Provide at least one transaction to confirm or discard".to_string(),
        ));
    }

    let mut db_tx = state.db.begin().await?;

    let mut discarded_count: usize = 0;
    if !payload.discard_ids.is_empty() {
        let result = sqlx::query(
            r#"
            DELETE FROM transactions
            WHERE user_id = $1
              AND id = ANY($2)
              AND status IN ('draft', 'duplicate_flagged')
            "#,
        )
        .bind(auth.user_id)
        .bind(&payload.discard_ids)
        .execute(&mut *db_tx)
        .await?;
        discarded_count = result.rows_affected() as usize;
    }

    let mut confirmed_records: Vec<TransactionRecord> =
        Vec::with_capacity(payload.transactions.len());

    for item in payload.transactions {
        if let Some(amt) = item.amount {
            if amt < Decimal::ZERO {
                return Err(AppError::BadRequest(format!(
                    "Transaction {} amount must be non-negative",
                    item.id
                )));
            }
        }

        let normalized_tx_type = match item.tx_type.as_deref().map(str::trim) {
            Some(t) if t.eq_ignore_ascii_case("debit") => Some("debit"),
            Some(t) if t.eq_ignore_ascii_case("credit") => Some("credit"),
            Some("") | None => None,
            Some(other) => {
                return Err(AppError::BadRequest(format!(
                    "Invalid tx_type '{other}'. Expected 'debit' or 'credit'"
                )));
            }
        };

        let resolved_category_id: Option<Uuid> = if let Some(cid) = item.category_id {
            Some(cid)
        } else if let Some(ref cname) = item.category_name {
            sqlx::query_scalar("SELECT id FROM categories WHERE LOWER(name) = LOWER($1)")
                .bind(cname.trim())
                .fetch_optional(&mut *db_tx)
                .await?
        } else {
            None
        };

        let new_currency = item
            .currency
            .as_deref()
            .map(|c| c.trim().to_uppercase())
            .filter(|s| !s.is_empty());

        let new_base_amount: Option<Decimal> = if let Some(amt) = item.amount {
            let cur = new_currency
                .as_deref()
                .unwrap_or(&state.config.default_currency);
            Some(
                state
                    .fx
                    .convert_to_base(amt, cur, &state.config.default_currency)
                    .await,
            )
        } else {
            None
        };

        let updated: Option<TransactionRecord> = sqlx::query_as(
            r#"
            WITH updated AS (
                UPDATE transactions
                SET tx_date     = COALESCE($3, tx_date),
                    vendor      = COALESCE($4, vendor),
                    description = COALESCE($5, description),
                    category_id = COALESCE($6, category_id),
                    amount      = COALESCE($7, amount),
                    base_amount = COALESCE($8, base_amount),
                    tx_type     = COALESCE($9, tx_type),
                    currency    = COALESCE($10, currency),
                    status      = 'confirmed'
                WHERE id = $1
                  AND user_id = $2
                RETURNING *
            )
            SELECT
                u.id,
                u.document_id,
                u.tx_date,
                u.vendor,
                u.description,
                u.category_id,
                c.name AS category_name,
                u.wallet_id,
                w.name AS wallet_name,
                u.amount,
                u.base_amount,
                u.tx_type,
                u.currency,
                u.source,
                u.status,
                u.is_recurring,
                u.is_transfer,
                u.tags,
                u.created_at
            FROM updated u
            LEFT JOIN categories c ON c.id = u.category_id
            LEFT JOIN wallets w ON w.id = u.wallet_id
            "#,
        )
        .bind(item.id)
        .bind(auth.user_id)
        .bind(item.tx_date)
        .bind(item.vendor.as_deref().map(str::trim))
        .bind(item.description.as_deref().map(str::trim))
        .bind(resolved_category_id)
        .bind(item.amount)
        .bind(new_base_amount)
        .bind(normalized_tx_type)
        .bind(new_currency)
        .fetch_optional(&mut *db_tx)
        .await?;

        match updated {
            Some(rec) => {
                // Learn merchant -> category mapping for future uploads
                if let Some(cid) = rec.category_id {
                    save_merchant_rule(&mut db_tx, auth.user_id, &rec.vendor, cid).await?;
                }
                confirmed_records.push(rec);
            }
            None => {
                return Err(AppError::NotFound(format!(
                    "Transaction {} not found",
                    item.id
                )));
            }
        }
    }

    db_tx.commit().await?;

    Ok(Json(ConfirmTransactionsResponse {
        confirmed_count: confirmed_records.len(),
        discarded_count,
        transactions: confirmed_records,
    }))
}

#[derive(sqlx::FromRow)]
struct TypeTotalsRow {
    total_spent: Decimal,
    total_credit: Decimal,
    debit_count: i64,
    credit_count: i64,
}

pub async fn user_cycle_bounds(
    db: &sqlx::PgPool,
    user_id: Uuid,
    year: i32,
    month: u32,
) -> Result<(NaiveDate, NaiveDate), AppError> {
    let start_day: i16 = sqlx::query_scalar("SELECT cycle_start_day FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_optional(db)
        .await?
        .unwrap_or(1)
        .clamp(1, 28);
    if start_day <= 1 {
        return month_bounds(year, month);
    }
    let start = NaiveDate::from_ymd_opt(year, month, start_day as u32)
        .ok_or_else(|| AppError::BadRequest(format!("Invalid cycle date {year}-{month}-{start_day}")))?;
    let (next_year, next_month) = if month == 12 {
        (year + 1, 1)
    } else {
        (year, month + 1)
    };
    let end = NaiveDate::from_ymd_opt(next_year, next_month, start_day as u32)
        .ok_or_else(|| AppError::BadRequest("Invalid next cycle date".to_string()))?;
    Ok((start, end))
}

pub async fn monthly_spending(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<MonthlyQuery>,
) -> Result<Json<MonthlySpendingResponse>, AppError> {
    let (start_date, end_date) = user_cycle_bounds(&state.db, auth.user_id, params.year, params.month).await?;

    let carry_forward_balance: Decimal = sqlx::query_scalar(
        r#"
        SELECT COALESCE(
            SUM(CASE WHEN tx_type = 'credit' THEN COALESCE(base_amount, amount) ELSE -COALESCE(base_amount, amount) END),
            0
        )
        FROM transactions
        WHERE user_id = $1
          AND status = 'confirmed'
          AND is_transfer = FALSE
          AND tx_date < $2
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .fetch_one(&state.db)
    .await?;

    let totals: TypeTotalsRow = sqlx::query_as(
        r#"
        SELECT
            COALESCE(SUM(CASE WHEN tx_type = 'debit' THEN COALESCE(base_amount, amount) ELSE 0 END), 0) AS total_spent,
            COALESCE(SUM(CASE WHEN tx_type = 'credit' THEN COALESCE(base_amount, amount) ELSE 0 END), 0) AS total_credit,
            COUNT(*) FILTER (WHERE tx_type = 'debit') AS debit_count,
            COUNT(*) FILTER (WHERE tx_type = 'credit') AS credit_count
        FROM transactions
        WHERE user_id = $1
          AND status = 'confirmed'
          AND is_transfer = FALSE
          AND tx_date >= $2
          AND tx_date < $3
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .bind(end_date)
    .fetch_one(&state.db)
    .await?;

    let pending_draft_count: i64 = sqlx::query_scalar(
        r#"
        SELECT COUNT(*)
        FROM transactions
        WHERE user_id = $1
          AND status IN ('draft', 'duplicate_flagged')
        "#,
    )
    .bind(auth.user_id)
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
          AND t.tx_date < $3
        GROUP BY t.category_id, c.name
        ORDER BY total_spent DESC
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .bind(end_date)
    .fetch_all(&state.db)
    .await?;

    let net_balance = totals.total_credit - totals.total_spent;
    let cumulative_balance = carry_forward_balance + net_balance;
    let transaction_count = totals.debit_count + totals.credit_count;

    Ok(Json(MonthlySpendingResponse {
        year: params.year,
        month: params.month,
        base_currency: state.config.default_currency.clone(),
        carry_forward_balance,
        total_spent: totals.total_spent,
        total_credit: totals.total_credit,
        net_balance,
        cumulative_balance,
        debit_count: totals.debit_count,
        credit_count: totals.credit_count,
        transaction_count,
        pending_draft_count,
        by_category,
    }))
}

/// Inserts a reversible, auditable balancing transaction so the user's running
/// cumulative balance through the selected month becomes exactly 0.
pub async fn zero_balance(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<ZeroBalanceRequest>,
) -> Result<Json<ZeroBalanceResponse>, AppError> {
    let (start_date, end_date) = month_bounds(payload.year, payload.month)?;

    let mut db_tx = state.db.begin().await?;

    let cumulative_balance: Decimal = sqlx::query_scalar(
        r#"
        SELECT COALESCE(
            SUM(CASE WHEN tx_type = 'credit' THEN COALESCE(base_amount, amount) ELSE -COALESCE(base_amount, amount) END),
            0
        )
        FROM transactions
        WHERE user_id = $1
          AND status = 'confirmed'
          AND is_transfer = FALSE
          AND tx_date < $2
        "#,
    )
    .bind(auth.user_id)
    .bind(end_date)
    .fetch_one(&mut *db_tx)
    .await?;

    if cumulative_balance.is_zero() {
        db_tx.commit().await?;
        return Ok(Json(ZeroBalanceResponse {
            already_zero: true,
            previous_balance: Decimal::ZERO,
            new_balance: Decimal::ZERO,
            adjustment: None,
        }));
    }

    let (tx_type, vendor, target_category) = if cumulative_balance < Decimal::ZERO {
        ("credit", "Balance Settlement", "Debt Repayment")
    } else {
        ("debit", "Balance Reset", "Transfer")
    };

    let amount = cumulative_balance.abs();
    let currency = state.config.default_currency.clone();

    // Place the adjustment on today's date if within the selected month, else last day of that month
    let today = Utc::now().date_naive();
    let last_day_of_month = end_date - chrono::Duration::days(1);
    let tx_date = if today >= start_date && today < end_date {
        today
    } else {
        last_day_of_month
    };

    let category_id: Uuid =
        sqlx::query_scalar("SELECT id FROM categories WHERE LOWER(name) = LOWER($1)")
            .bind(target_category)
            .fetch_optional(&mut *db_tx)
            .await?
            .or(
                sqlx::query_scalar("SELECT id FROM categories WHERE LOWER(name) = 'uncategorized'")
                    .fetch_optional(&mut *db_tx)
                    .await?,
            )
            .ok_or_else(|| AppError::Internal("Default category missing".to_string()))?;

    let note = payload
        .note
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .unwrap_or("Zeroed out running balance");

    let record: TransactionRecord = sqlx::query_as(
        r#"
        WITH inserted AS (
            INSERT INTO transactions (
                user_id, tx_date, vendor, description, category_id,
                amount, base_amount, tx_type, currency, source, status, is_recurring
            )
            VALUES ($1, $2, $3, $4, $5, $6, $6, $7, $8, 'manual', 'confirmed', FALSE)
            RETURNING *
        )
        SELECT
            i.id,
            i.document_id,
            i.tx_date,
            i.vendor,
            i.description,
            i.category_id,
            c.name AS category_name,
            i.wallet_id,
            w.name AS wallet_name,
            i.amount,
            i.base_amount,
            i.tx_type,
            i.currency,
            i.source,
            i.status,
            i.is_recurring,
            i.is_transfer,
            i.tags,
            i.created_at
        FROM inserted i
        LEFT JOIN categories c ON c.id = i.category_id
        LEFT JOIN wallets w ON w.id = i.wallet_id
        "#,
    )
    .bind(auth.user_id)
    .bind(tx_date)
    .bind(vendor)
    .bind(note)
    .bind(category_id)
    .bind(amount)
    .bind(tx_type)
    .bind(&currency)
    .fetch_one(&mut *db_tx)
    .await?;

    db_tx.commit().await?;

    Ok(Json(ZeroBalanceResponse {
        already_zero: false,
        previous_balance: cumulative_balance,
        new_balance: Decimal::ZERO,
        adjustment: Some(record),
    }))
}

pub async fn list_transactions(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<ListTransactionsQuery>,
) -> Result<Json<Vec<TransactionRecord>>, AppError> {
    let (start_date, end_date) = match (params.start_date, params.end_date) {
        (Some(s), Some(e)) => {
            if e < s {
                return Err(AppError::BadRequest(
                    "end_date must be on or after start_date".to_string(),
                ));
            }
            (s, e + chrono::Duration::days(1))
        }
        _ => {
            let today = Utc::now().date_naive();
            let y = params.year.unwrap_or(today.year());
            let m = params.month.unwrap_or(today.month());
            user_cycle_bounds(&state.db, auth.user_id, y, m).await?
        }
    };

    let status_filter = params
        .status
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .unwrap_or("confirmed");

    if !matches!(
        status_filter,
        "confirmed" | "draft" | "duplicate_flagged" | "all"
    ) {
        return Err(AppError::BadRequest(format!(
            "Invalid status '{status_filter}'. Expected: confirmed, draft, duplicate_flagged, or all"
        )));
    }

    let tag_filter = params
        .tag
        .as_deref()
        .map(|s| s.trim().trim_start_matches('#').to_lowercase())
        .filter(|s| !s.is_empty());

    let records: Vec<TransactionRecord> = sqlx::query_as(
        r#"
        SELECT
            t.id,
            t.document_id,
            t.tx_date,
            t.vendor,
            t.description,
            t.category_id,
            c.name AS category_name,
            t.wallet_id,
            w.name AS wallet_name,
            t.amount,
            t.base_amount,
            t.tx_type,
            t.currency,
            t.source,
            t.status,
            t.is_recurring,
            t.is_transfer,
            t.tags,
            t.created_at
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN wallets w ON w.id = t.wallet_id
        WHERE t.user_id = $1
          AND t.tx_date >= $2
          AND t.tx_date < $3
          AND ($4 = 'all' OR t.status = $4)
          AND ($5::TEXT IS NULL OR $5 = ANY(t.tags))
          AND ($6::UUID IS NULL OR t.wallet_id = $6)
        ORDER BY t.tx_date DESC, t.created_at DESC
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .bind(end_date)
    .bind(status_filter)
    .bind(&tag_filter)
    .bind(params.wallet_id)
    .fetch_all(&state.db)
    .await?;

    Ok(Json(records))
}

pub async fn list_pending_transactions(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<Vec<TransactionRecord>>, AppError> {
    let records: Vec<TransactionRecord> = sqlx::query_as(
        r#"
        SELECT
            t.id,
            t.document_id,
            t.tx_date,
            t.vendor,
            t.description,
            t.category_id,
            c.name AS category_name,
            t.wallet_id,
            w.name AS wallet_name,
            t.amount,
            t.base_amount,
            t.tx_type,
            t.currency,
            t.source,
            t.status,
            t.is_recurring,
            t.is_transfer,
            t.tags,
            t.created_at
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN wallets w ON w.id = t.wallet_id
        WHERE t.user_id = $1
          AND t.status IN ('draft', 'duplicate_flagged')
        ORDER BY t.tx_date DESC, t.created_at DESC
        "#,
    )
    .bind(auth.user_id)
    .fetch_all(&state.db)
    .await?;

    Ok(Json(records))
}

pub async fn create_transaction(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<CreateTransactionRequest>,
) -> Result<(StatusCode, Json<TransactionRecord>), AppError> {
    let vendor = payload.vendor.trim();
    if vendor.is_empty() {
        return Err(AppError::BadRequest("Vendor is required".to_string()));
    }
    if payload.amount <= Decimal::ZERO {
        return Err(AppError::BadRequest(
            "Amount must be greater than zero".to_string(),
        ));
    }

    let tx_type = match payload.tx_type.as_deref().map(str::trim) {
        Some(t) if t.eq_ignore_ascii_case("credit") => "credit",
        Some(t) if t.eq_ignore_ascii_case("debit") || t.is_empty() => "debit",
        None => "debit",
        Some(other) => {
            return Err(AppError::BadRequest(format!(
                "Invalid tx_type '{other}'. Expected 'debit' or 'credit'"
            )));
        }
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

    let category_name = payload
        .category_name
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .unwrap_or("Uncategorized");

    let category_id: Uuid =
        sqlx::query_scalar("SELECT id FROM categories WHERE LOWER(name) = LOWER($1)")
            .bind(category_name)
            .fetch_optional(&state.db)
            .await?
            .or(
                sqlx::query_scalar("SELECT id FROM categories WHERE LOWER(name) = 'uncategorized'")
                    .fetch_optional(&state.db)
                    .await?,
            )
            .ok_or_else(|| AppError::Internal("Default 'Uncategorized' category missing".to_string()))?;

    let is_recurring = payload.is_recurring.unwrap_or(false);
    let tags = crate::routes::extras::clean_tags(payload.tags.as_deref().unwrap_or(&[]));

    let mut db_tx = state.db.begin().await?;

    let record: TransactionRecord = sqlx::query_as(
        r#"
        WITH inserted AS (
            INSERT INTO transactions (
                user_id, tx_date, vendor, description, category_id, wallet_id,
                amount, base_amount, tx_type, currency, source, status, is_recurring, is_transfer, tags
            )
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, 'manual', 'confirmed', $11, FALSE, $12)
            RETURNING *
        )
        SELECT
            i.id,
            i.document_id,
            i.tx_date,
            i.vendor,
            i.description,
            i.category_id,
            c.name AS category_name,
            i.wallet_id,
            w.name AS wallet_name,
            i.amount,
            i.base_amount,
            i.tx_type,
            i.currency,
            i.source,
            i.status,
            i.is_recurring,
            i.is_transfer,
            i.tags,
            i.created_at
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
    .bind(payload.wallet_id)
    .bind(payload.amount)
    .bind(base_amount)
    .bind(tx_type)
    .bind(&currency)
    .bind(is_recurring)
    .bind(&tags)
    .fetch_one(&mut *db_tx)
    .await?;

    save_merchant_rule(&mut db_tx, auth.user_id, vendor, category_id).await?;
    db_tx.commit().await?;

    Ok((StatusCode::CREATED, Json(record)))
}

pub async fn update_transaction(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
    Json(payload): Json<UpdateTransactionRequest>,
) -> Result<Json<TransactionRecord>, AppError> {
    if let Some(amt) = payload.amount {
        if amt <= Decimal::ZERO {
            return Err(AppError::BadRequest(
                "Amount must be greater than zero".to_string(),
            ));
        }
    }

    let normalized_tx_type = match payload.tx_type.as_deref().map(str::trim) {
        Some(t) if t.eq_ignore_ascii_case("debit") => Some("debit"),
        Some(t) if t.eq_ignore_ascii_case("credit") => Some("credit"),
        Some("") | None => None,
        Some(other) => {
            return Err(AppError::BadRequest(format!(
                "Invalid tx_type '{other}'. Expected 'debit' or 'credit'"
            )));
        }
    };

    let mut db_tx = state.db.begin().await?;

    let existing: Option<(Decimal, String)> = sqlx::query_as(
        "SELECT amount, currency FROM transactions WHERE id = $1 AND user_id = $2",
    )
    .bind(id)
    .bind(auth.user_id)
    .fetch_optional(&mut *db_tx)
    .await?;

    let Some((old_amount, old_currency)) = existing else {
        return Err(AppError::NotFound(format!("Transaction {id} not found")));
    };

    let resolved_category_id: Option<Uuid> = if let Some(cid) = payload.category_id {
        Some(cid)
    } else if let Some(ref cname) = payload.category_name {
        sqlx::query_scalar("SELECT id FROM categories WHERE LOWER(name) = LOWER($1)")
            .bind(cname.trim())
            .fetch_optional(&mut *db_tx)
            .await?
    } else {
        None
    };

    let vendor_clean = payload
        .vendor
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty());

    let new_currency = payload
        .currency
        .as_deref()
        .map(|c| c.trim().to_uppercase())
        .filter(|s| !s.is_empty());

    let effective_amount = payload.amount.unwrap_or(old_amount);
    let effective_currency = new_currency.as_deref().unwrap_or(&old_currency);
    let recomputed_base = if payload.amount.is_some() || new_currency.is_some() {
        Some(
            state
                .fx
                .convert_to_base(effective_amount, effective_currency, &state.config.default_currency)
                .await,
        )
    } else {
        None
    };

    let cleaned_tags = payload
        .tags
        .as_deref()
        .map(crate::routes::extras::clean_tags);

    let updated: TransactionRecord = sqlx::query_as(
        r#"
        WITH updated AS (
            UPDATE transactions
            SET tx_date      = COALESCE($3, tx_date),
                vendor       = COALESCE($4, vendor),
                description  = COALESCE($5, description),
                category_id  = COALESCE($6, category_id),
                amount       = COALESCE($7, amount),
                base_amount  = COALESCE($8, base_amount),
                tx_type      = COALESCE($9, tx_type),
                currency     = COALESCE($10, currency),
                is_recurring = COALESCE($11, is_recurring),
                wallet_id    = COALESCE($12, wallet_id),
                tags         = COALESCE($13, tags)
            WHERE id = $1
              AND user_id = $2
            RETURNING *
        )
        SELECT
            u.id,
            u.document_id,
            u.tx_date,
            u.vendor,
            u.description,
            u.category_id,
            c.name AS category_name,
            u.wallet_id,
            w.name AS wallet_name,
            u.amount,
            u.base_amount,
            u.tx_type,
            u.currency,
            u.source,
            u.status,
            u.is_recurring,
            u.is_transfer,
            u.tags,
            u.created_at
        FROM updated u
        LEFT JOIN categories c ON c.id = u.category_id
        LEFT JOIN wallets w ON w.id = u.wallet_id
        "#,
    )
    .bind(id)
    .bind(auth.user_id)
    .bind(payload.tx_date)
    .bind(vendor_clean)
    .bind(payload.description.as_deref().map(str::trim))
    .bind(resolved_category_id)
    .bind(payload.amount)
    .bind(recomputed_base)
    .bind(normalized_tx_type)
    .bind(new_currency)
    .bind(payload.is_recurring)
    .bind(payload.wallet_id)
    .bind(&cleaned_tags)
    .fetch_one(&mut *db_tx)
    .await?;

    // Learn merchant -> category mapping whenever user corrects a category
    if let Some(cid) = updated.category_id {
        save_merchant_rule(&mut db_tx, auth.user_id, &updated.vendor, cid).await?;
    }

    db_tx.commit().await?;
    Ok(Json(updated))
}

pub async fn delete_transaction(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, AppError> {
    let result = sqlx::query("DELETE FROM transactions WHERE id = $1 AND user_id = $2")
        .bind(id)
        .bind(auth.user_id)
        .execute(&state.db)
        .await?;

    if result.rows_affected() == 0 {
        return Err(AppError::NotFound(format!("Transaction {id} not found")));
    }

    Ok(StatusCode::NO_CONTENT)
}

#[derive(sqlx::FromRow)]
struct RecurringQueryRow {
    vendor: String,
    category_name: String,
    avg_amount: Decimal,
    #[allow(dead_code)]
    currency: String,
    last_date: NaiveDate,
    occurrences: i64,
    is_marked_recurring: bool,
}

pub async fn list_recurring(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<RecurringSummaryResponse>, AppError> {
    let rows: Vec<RecurringQueryRow> = sqlx::query_as(
        r#"
        SELECT
            MIN(t.vendor) AS vendor,
            COALESCE(MAX(c.name), 'Uncategorized') AS category_name,
            ROUND(AVG(COALESCE(t.base_amount, t.amount)), 2) AS avg_amount,
            MAX(t.currency) AS currency,
            MAX(t.tx_date) AS last_date,
            COUNT(*) AS occurrences,
            BOOL_OR(t.is_recurring) AS is_marked_recurring
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.user_id = $1
          AND t.status = 'confirmed'
          AND t.is_transfer = FALSE
          AND t.tx_type = 'debit'
        GROUP BY LOWER(TRIM(t.vendor))
        HAVING BOOL_OR(t.is_recurring) = TRUE
            OR COUNT(DISTINCT DATE_TRUNC('month', t.tx_date)) >= 2
        ORDER BY MAX(t.tx_date) DESC
        LIMIT 20
        "#,
    )
    .bind(auth.user_id)
    .fetch_all(&state.db)
    .await?;

    let mut monthly_fixed_total = Decimal::ZERO;
    let items: Vec<RecurringBillItem> = rows
        .into_iter()
        .map(|r| {
            monthly_fixed_total += r.avg_amount;
            let next_expected_date = add_one_month(r.last_date);
            RecurringBillItem {
                vendor: r.vendor,
                category_name: r.category_name,
                avg_amount: r.avg_amount,
                currency: state.config.default_currency.clone(),
                last_date: r.last_date,
                next_expected_date,
                occurrences: r.occurrences,
                is_marked_recurring: r.is_marked_recurring,
            }
        })
        .collect();

    Ok(Json(RecurringSummaryResponse {
        monthly_fixed_total,
        base_currency: state.config.default_currency.clone(),
        items,
    }))
}

#[derive(sqlx::FromRow)]
struct TrendBucketRow {
    year: i32,
    month: i32,
    total_spent: Decimal,
    total_credit: Decimal,
}

pub async fn spending_trend(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<TrendQuery>,
) -> Result<Json<Vec<MonthlyTrendPoint>>, AppError> {
    let months = params.months.unwrap_or(6).clamp(1, 12);
    let today = Utc::now().date_naive();

    let mut buckets: Vec<(i32, u32)> = Vec::with_capacity(months as usize);
    let mut y = today.year();
    let mut m = today.month() as i32;
    for _ in 0..months {
        buckets.push((y, m as u32));
        m -= 1;
        if m == 0 {
            m = 12;
            y -= 1;
        }
    }
    buckets.reverse();

    let (first_year, first_month) = buckets[0];
    let (start_date, _) = month_bounds(first_year, first_month)?;
    let (_, end_date) = month_bounds(today.year(), today.month())?;

    let rows: Vec<TrendBucketRow> = sqlx::query_as(
        r#"
        SELECT
            EXTRACT(YEAR FROM tx_date)::INT AS year,
            EXTRACT(MONTH FROM tx_date)::INT AS month,
            COALESCE(SUM(CASE WHEN tx_type = 'debit' THEN COALESCE(base_amount, amount) ELSE 0 END), 0) AS total_spent,
            COALESCE(SUM(CASE WHEN tx_type = 'credit' THEN COALESCE(base_amount, amount) ELSE 0 END), 0) AS total_credit
        FROM transactions
        WHERE user_id = $1
          AND status = 'confirmed'
          AND is_transfer = FALSE
          AND tx_date >= $2
          AND tx_date < $3
        GROUP BY 1, 2
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .bind(end_date)
    .fetch_all(&state.db)
    .await?;

    let points = buckets
        .into_iter()
        .map(|(yr, mo)| {
            let found = rows.iter().find(|r| r.year == yr && r.month == mo as i32);
            let total_spent = found.map(|r| r.total_spent).unwrap_or(Decimal::ZERO);
            let total_credit = found.map(|r| r.total_credit).unwrap_or(Decimal::ZERO);
            MonthlyTrendPoint {
                year: yr,
                month: mo,
                total_spent,
                total_credit,
                net_balance: total_credit - total_spent,
            }
        })
        .collect();

    Ok(Json(points))
}

pub async fn list_budgets(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<MonthlyQuery>,
) -> Result<Json<Vec<BudgetRecord>>, AppError> {
    let (start_date, end_date) = user_cycle_bounds(&state.db, auth.user_id, params.year, params.month).await?;
    let (prev_y, prev_m) = if params.month == 1 {
        (params.year - 1, 12)
    } else {
        (params.year, params.month - 1)
    };
    let (prev_start, prev_end) = user_cycle_bounds(&state.db, auth.user_id, prev_y, prev_m).await?;

    let budgets: Vec<BudgetRecord> = sqlx::query_as(
        r#"
        SELECT
            b.id,
            b.category_id,
            c.name AS category_name,
            b.monthly_limit,
            CASE
                WHEN b.rollover THEN
                    GREATEST(
                        0,
                        b.monthly_limit + (
                            b.monthly_limit - COALESCE((
                                SELECT SUM(COALESCE(pt.base_amount, pt.amount))
                                FROM transactions pt
                                WHERE pt.user_id = b.user_id
                                  AND pt.category_id = b.category_id
                                  AND pt.status = 'confirmed'
                                  AND pt.is_transfer = FALSE
                                  AND pt.tx_type = 'debit'
                                  AND pt.tx_date >= $4
                                  AND pt.tx_date < $5
                            ), 0)
                        )
                    )
                ELSE b.monthly_limit
            END AS effective_limit,
            COALESCE(SUM(COALESCE(t.base_amount, t.amount)), 0) AS spent,
            b.currency,
            b.rollover
        FROM budgets b
        JOIN categories c ON c.id = b.category_id
        LEFT JOIN transactions t
            ON t.user_id = b.user_id
           AND t.category_id = b.category_id
           AND t.status = 'confirmed'
           AND t.is_transfer = FALSE
           AND t.tx_type = 'debit'
           AND t.tx_date >= $2
           AND t.tx_date < $3
        WHERE b.user_id = $1
        GROUP BY b.id, b.category_id, c.name, b.monthly_limit, b.currency, b.rollover
        ORDER BY c.name
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .bind(end_date)
    .bind(prev_start)
    .bind(prev_end)
    .fetch_all(&state.db)
    .await?;

    Ok(Json(budgets))
}

pub async fn upsert_budget(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<UpsertBudgetRequest>,
) -> Result<Json<BudgetRecord>, AppError> {
    if payload.monthly_limit <= Decimal::ZERO {
        return Err(AppError::BadRequest(
            "monthly_limit must be greater than zero".to_string(),
        ));
    }

    let category: Option<(Uuid, String)> =
        sqlx::query_as("SELECT id, name FROM categories WHERE LOWER(name) = LOWER($1)")
            .bind(payload.category_name.trim())
            .fetch_optional(&state.db)
            .await?;

    let (category_id, category_name) = category.ok_or_else(|| {
        AppError::BadRequest(format!("Unknown category '{}'", payload.category_name))
    })?;

    let currency = payload
        .currency
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(|s| s.to_uppercase())
        .unwrap_or_else(|| state.config.default_currency.clone());
    let rollover = payload.rollover.unwrap_or(false);

    let today = Utc::now().date_naive();
    let (start_date, end_date) = user_cycle_bounds(&state.db, auth.user_id, today.year(), today.month()).await?;

    let id: Uuid = sqlx::query_scalar(
        r#"
        INSERT INTO budgets (user_id, category_id, monthly_limit, currency, rollover, updated_at)
        VALUES ($1, $2, $3, $4, $5, NOW())
        ON CONFLICT (user_id, category_id)
        DO UPDATE SET
            monthly_limit = EXCLUDED.monthly_limit,
            currency      = EXCLUDED.currency,
            rollover      = EXCLUDED.rollover,
            updated_at    = NOW()
        RETURNING id
        "#,
    )
    .bind(auth.user_id)
    .bind(category_id)
    .bind(payload.monthly_limit)
    .bind(&currency)
    .bind(rollover)
    .fetch_one(&state.db)
    .await?;

    let spent: Decimal = sqlx::query_scalar(
        r#"
        SELECT COALESCE(SUM(COALESCE(base_amount, amount)), 0)
        FROM transactions
        WHERE user_id = $1
          AND category_id = $2
          AND status = 'confirmed'
          AND is_transfer = FALSE
          AND tx_type = 'debit'
          AND tx_date >= $3
          AND tx_date < $4
        "#,
    )
    .bind(auth.user_id)
    .bind(category_id)
    .bind(start_date)
    .bind(end_date)
    .fetch_one(&state.db)
    .await?;

    Ok(Json(BudgetRecord {
        id,
        category_id,
        category_name,
        monthly_limit: payload.monthly_limit,
        effective_limit: payload.monthly_limit,
        spent,
        currency,
        rollover,
    }))
}

pub async fn list_categories(
    State(state): State<AppState>,
    _auth: AuthUser,
) -> Result<Json<Vec<CategoryRecord>>, AppError> {
    let rows: Vec<CategoryRecord> = sqlx::query_as(
        r#"
        SELECT id, name
        FROM categories
        ORDER BY CASE WHEN name = 'Uncategorized' THEN 1 ELSE 0 END, name ASC
        "#,
    )
    .fetch_all(&state.db)
    .await?;

    Ok(Json(rows))
}

pub async fn create_category(
    State(state): State<AppState>,
    _auth: AuthUser,
    Json(payload): Json<CreateCategoryRequest>,
) -> Result<(StatusCode, Json<CategoryRecord>), AppError> {
    let name = payload.name.trim();
    if name.is_empty() || name.len() > 60 {
        return Err(AppError::BadRequest(
            "Category name must be between 1 and 60 characters".to_string(),
        ));
    }

    let existing: Option<CategoryRecord> =
        sqlx::query_as("SELECT id, name FROM categories WHERE LOWER(name) = LOWER($1)")
            .bind(name)
            .fetch_optional(&state.db)
            .await?;

    if let Some(cat) = existing {
        return Ok((StatusCode::OK, Json(cat)));
    }

    let created: CategoryRecord = sqlx::query_as(
        "INSERT INTO categories (name) VALUES ($1) RETURNING id, name",
    )
    .bind(name)
    .fetch_one(&state.db)
    .await?;

    Ok((StatusCode::CREATED, Json(created)))
}

fn add_one_month(date: NaiveDate) -> NaiveDate {
    let (ny, nm) = if date.month() == 12 {
        (date.year() + 1, 1)
    } else {
        (date.year(), date.month() + 1)
    };
    let max_day = match nm {
        2 => {
            if (ny % 4 == 0 && ny % 100 != 0) || (ny % 400 == 0) {
                29
            } else {
                28
            }
        }
        4 | 6 | 9 | 11 => 30,
        _ => 31,
    };
    NaiveDate::from_ymd_opt(ny, nm, date.day().min(max_day)).unwrap_or(date)
}

fn month_bounds(year: i32, month: u32) -> Result<(NaiveDate, NaiveDate), AppError> {
    if !(1..=12).contains(&month) {
        return Err(AppError::BadRequest(
            "Query parameter `month` must be between 1 and 12".to_string(),
        ));
    }

    let start = NaiveDate::from_ymd_opt(year, month, 1)
        .ok_or_else(|| AppError::BadRequest(format!("Invalid year/month: {year}-{month}")))?;

    let (next_year, next_month) = if month == 12 {
        (year + 1, 1)
    } else {
        (year, month + 1)
    };

    let end = NaiveDate::from_ymd_opt(next_year, next_month, 1)
        .ok_or_else(|| AppError::BadRequest(format!("Invalid year/month: {year}-{month}")))?;

    Ok((start, end))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn month_bounds_handles_december_rollover() {
        let (s, e) = month_bounds(2026, 12).unwrap();
        assert_eq!(s, NaiveDate::from_ymd_opt(2026, 12, 1).unwrap());
        assert_eq!(e, NaiveDate::from_ymd_opt(2027, 1, 1).unwrap());
    }

    #[test]
    fn month_bounds_rejects_invalid_month() {
        assert!(month_bounds(2026, 13).is_err());
        assert!(month_bounds(2026, 0).is_err());
    }

    #[test]
    fn add_one_month_clamps_end_of_month() {
        let jan31 = NaiveDate::from_ymd_opt(2026, 1, 31).unwrap();
        assert_eq!(add_one_month(jan31), NaiveDate::from_ymd_opt(2026, 2, 28).unwrap());
    }
}
