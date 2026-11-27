use crate::{
    error::AppError,
    models::{
        AnalyticsBundleQuery, BackupBudget, BackupGoal, BackupImportResponse, BackupIou,
        BackupPayload, BackupRule, BackupTemplate, BackupTransaction, BackupWallet,
        BootstrapQuery, BudgetSuggestionItem, BudgetSuggestionsResponse, BulkTransactionsRequest,
        BulkTransactionsResponse, CategoryHistoryQuery, CategoryHistoryResponse,
        CategoryMomChange, CategoryMonthPoint, CategorySpending, CreateDraftNotificationRequest,
        CreateTemplateRequest, CreateWalletRequest, DashboardAnalyticsResponse,
        DashboardBootstrapResponse, DuplicatePair, DuplicateScanResponse, EmergencyRunway,
        FinancialHealthScore, ForecastAndHealthResponse, FxRatesResponse, GlobalSearchQuery,
        GlobalSearchResponse, ListTransactionsQuery, MerchantMonthPoint, MerchantProfileQuery,
        MerchantProfileResponse, MerchantRuleRecord, MonthlyComparisonResponse, MonthlyQuery,
        NetWorthPoint, PaceComparison, QuickTemplateRecord, RangeSpendingQuery,
        RangeSpendingResponse, Rule503020Response, SpendingAlertItem, SpendingChallenge,
        SplitTransactionRequest, SubscriptionAuditItem, SubscriptionAuditResponse, TagSpending, TopMerchantItem,
        TransactionRecord, TransferWalletRequest, TransferWalletResponse, TrendQuery,
        UpdatePreferencesRequest, UpsertMerchantRuleRequest, UserPreferencesResponse,
        WalletRecord, WeekdayBreakdownResponse, WeekdayQuery, WeekdaySpendingItem,
        YearlyBiggestExpense, YearlyMonthItem, YearlyWrappedQuery, YearlyWrappedResponse,
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
use chrono::{Datelike, NaiveDate, Utc};
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

// ================================
// User Preferences: Payday Cycle & Travel/Event Mode (#6 & #7)
// ================================

async fn load_preferences_inner(
    state: &AppState,
    user_id: Uuid,
) -> Result<UserPreferencesResponse, AppError> {
    let (cycle_start_day, active_event_tag, active_event_currency): (
        i16,
        Option<String>,
        Option<String>,
    ) = sqlx::query_as(
        "SELECT cycle_start_day, active_event_tag, active_event_currency FROM users WHERE id = $1",
    )
    .bind(user_id)
    .fetch_one(&state.db)
    .await?;

    let active_event_spent: Decimal = if let Some(ref tag) = active_event_tag {
        sqlx::query_scalar(
            r#"
            SELECT COALESCE(SUM(COALESCE(base_amount, amount)), 0)
            FROM transactions
            WHERE user_id = $1 AND status = 'confirmed' AND is_transfer = FALSE
              AND tx_type = 'debit' AND $2 = ANY(tags)
            "#,
        )
        .bind(user_id)
        .bind(tag)
        .fetch_one(&state.db)
        .await?
    } else {
        Decimal::ZERO
    };

    Ok(UserPreferencesResponse {
        cycle_start_day,
        active_event_tag,
        active_event_currency,
        active_event_spent,
        base_currency: state.config.default_currency.clone(),
    })
}

pub async fn get_preferences(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<UserPreferencesResponse>, AppError> {
    Ok(Json(load_preferences_inner(&state, auth.user_id).await?))
}

pub async fn update_preferences(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<UpdatePreferencesRequest>,
) -> Result<Json<UserPreferencesResponse>, AppError> {
    if let Some(day) = payload.cycle_start_day {
        if !(1..=28).contains(&day) {
            return Err(AppError::BadRequest(
                "cycle_start_day must be between 1 and 28".to_string(),
            ));
        }
        sqlx::query("UPDATE users SET cycle_start_day = $2 WHERE id = $1")
            .bind(auth.user_id)
            .bind(day)
            .execute(&state.db)
            .await?;
    }

    if let Some(ref raw_tag) = payload.active_event_tag {
        let cleaned = clean_tags(&[raw_tag.clone()]).into_iter().next();
        sqlx::query("UPDATE users SET active_event_tag = $2 WHERE id = $1")
            .bind(auth.user_id)
            .bind(&cleaned)
            .execute(&state.db)
            .await?;
    }

    if let Some(ref raw_cur) = payload.active_event_currency {
        let cur = raw_cur.trim().to_uppercase();
        let val = if cur.is_empty() { None } else { Some(cur) };
        sqlx::query("UPDATE users SET active_event_currency = $2 WHERE id = $1")
            .bind(auth.user_id)
            .bind(&val)
            .execute(&state.db)
            .await?;
    }

    Ok(Json(load_preferences_inner(&state, auth.user_id).await?))
}

// ================================
// End-of-Month Cash-Flow Forecast, Net Worth Trajectory & Anomaly / Price-Hike Alerts (#2, #4, #5)
// ================================

pub async fn spending_forecast(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<MonthlyQuery>,
) -> Result<Json<ForecastAndHealthResponse>, AppError> {
    use chrono::Datelike;

    let (start_date, end_date) =
        crate::routes::transactions::user_cycle_bounds(&state.db, auth.user_id, params.year, params.month)
            .await?;
    let today = Utc::now().date_naive();

    // 1. Current running balance through end_date
    let current_running_balance: Decimal = sqlx::query_scalar(
        r#"
        SELECT COALESCE(
            SUM(CASE WHEN tx_type = 'credit' THEN COALESCE(base_amount, amount) ELSE -COALESCE(base_amount, amount) END),
            0
        )
        FROM transactions
        WHERE user_id = $1 AND status = 'confirmed' AND is_transfer = FALSE
          AND tx_date < $2
        "#,
    )
    .bind(auth.user_id)
    .bind(end_date)
    .fetch_one(&state.db)
    .await?;

    // 2. Burn rate in current cycle
    let spent_in_cycle: Decimal = sqlx::query_scalar(
        r#"
        SELECT COALESCE(SUM(COALESCE(base_amount, amount)), 0)
        FROM transactions
        WHERE user_id = $1 AND status = 'confirmed' AND is_transfer = FALSE
          AND tx_type = 'debit' AND tx_date >= $2 AND tx_date < $3
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .bind(end_date)
    .fetch_one(&state.db)
    .await?;

    let elapsed_days = if today >= start_date && today < end_date {
        ((today - start_date).num_days() + 1).max(1)
    } else {
        (end_date - start_date).num_days().max(1)
    };
    let remaining_days = if today >= start_date && today < end_date {
        ((end_date - today).num_days() - 1).max(0)
    } else {
        0
    };
    let daily_burn_rate = (spent_in_cycle / Decimal::from(elapsed_days)).round_dp(0);

    // 3. Upcoming unpaid recurring bills expected in this cycle
    let upcoming_recurring_total: Decimal = sqlx::query_scalar(
        r#"
        WITH rec AS (
            SELECT LOWER(TRIM(vendor)) AS vkey,
                   ROUND(AVG(COALESCE(base_amount, amount)), 2) AS avg_amt,
                   MAX(tx_date) AS last_date
            FROM transactions
            WHERE user_id = $1 AND status = 'confirmed' AND is_transfer = FALSE AND tx_type = 'debit'
            GROUP BY LOWER(TRIM(vendor))
            HAVING BOOL_OR(is_recurring) = TRUE OR COUNT(DISTINCT DATE_TRUNC('month', tx_date)) >= 2
        )
        SELECT COALESCE(SUM(avg_amt), 0)
        FROM rec
        WHERE last_date < $2
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .fetch_one(&state.db)
    .await?;

    let projected_month_end_balance = current_running_balance
        - upcoming_recurring_total
        - (daily_burn_rate * Decimal::from(remaining_days));

    // 4. Net Worth Trajectory (last 6 months: wallet initial balances + cumulative net balance)
    let wallets_initial: Decimal = sqlx::query_scalar(
        "SELECT COALESCE(SUM(initial_balance), 0) FROM wallets WHERE user_id = $1",
    )
    .bind(auth.user_id)
    .fetch_one(&state.db)
    .await?;

    let mut buckets: Vec<(i32, u32)> = Vec::with_capacity(6);
    let mut y = params.year;
    let mut m = params.month as i32;
    for _ in 0..6 {
        buckets.push((y, m as u32));
        m -= 1;
        if m == 0 {
            m = 12;
            y -= 1;
        }
    }
    buckets.reverse();

    let mut net_worth_points: Vec<NetWorthPoint> = Vec::with_capacity(6);
    for (by, bm) in buckets {
        let (_, b_end) =
            crate::routes::transactions::user_cycle_bounds(&state.db, auth.user_id, by, bm).await?;
        let cum: Decimal = sqlx::query_scalar(
            r#"
            SELECT COALESCE(
                SUM(CASE WHEN tx_type = 'credit' THEN COALESCE(base_amount, amount) ELSE -COALESCE(base_amount, amount) END),
                0
            )
            FROM transactions
            WHERE user_id = $1 AND status = 'confirmed' AND is_transfer = FALSE
              AND tx_date < $2
            "#,
        )
        .bind(auth.user_id)
        .bind(b_end)
        .fetch_one(&state.db)
        .await?;
        net_worth_points.push(NetWorthPoint {
            year: by,
            month: bm,
            net_worth: wallets_initial + cum,
        });
    }

    // 5. Subscription Price-Hike & Anomaly Alerts (pure SQL, $0, 0 AI calls)
    let mut alerts: Vec<SpendingAlertItem> = Vec::new();
    let cur = state.config.default_currency.clone();

    // 5a. Price hikes: recurring vendors whose latest charge is >= 10% above prior avg
    let hikes: Vec<(String, Decimal, Decimal)> = sqlx::query_as(
        r#"
        WITH ranked AS (
            SELECT
                vendor,
                LOWER(TRIM(vendor)) AS vkey,
                COALESCE(base_amount, amount) AS amt,
                tx_date,
                ROW_NUMBER() OVER (PARTITION BY LOWER(TRIM(vendor)) ORDER BY tx_date DESC, created_at DESC) AS rn
            FROM transactions
            WHERE user_id = $1 AND status = 'confirmed' AND is_transfer = FALSE
              AND tx_type = 'debit'
        ),
        stats AS (
            SELECT
                vkey,
                MAX(CASE WHEN rn = 1 THEN vendor END) AS vendor,
                MAX(CASE WHEN rn = 1 THEN amt END) AS latest_amt,
                MAX(CASE WHEN rn = 1 THEN tx_date END) AS latest_date,
                AVG(CASE WHEN rn > 1 THEN amt END) AS prior_avg,
                COUNT(*) AS cnt
            FROM ranked
            GROUP BY vkey
        )
        SELECT vendor, latest_amt, ROUND(prior_avg, 0) AS prior_avg
        FROM stats
        WHERE cnt >= 2
          AND prior_avg > 0
          AND latest_date >= $2
          AND latest_amt >= prior_avg * 1.10
        ORDER BY (latest_amt - prior_avg) DESC
        LIMIT 3
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .fetch_all(&state.db)
    .await?;

    for (vendor, latest, prior) in hikes {
        let pct = (((latest - prior) / prior) * Decimal::from(100)).round_dp(0);
        alerts.push(SpendingAlertItem {
            kind: "price_hike".to_string(),
            title: format!("Price hike: {vendor} (+{pct}%)"),
            detail: format!("Latest {latest} vs usual {prior} {cur}"),
            amount: latest,
            currency: cur.clone(),
        });
    }

    // 5b. Category anomalies: single transaction in this cycle >= 2.5x category avg (with >= 3 historical tx)
    let anomalies: Vec<(String, String, Decimal, Decimal)> = sqlx::query_as(
        r#"
        WITH cat_avg AS (
            SELECT category_id, AVG(COALESCE(base_amount, amount)) AS avg_amt, COUNT(*) AS cnt
            FROM transactions
            WHERE user_id = $1 AND status = 'confirmed' AND is_transfer = FALSE AND tx_type = 'debit'
            GROUP BY category_id
            HAVING COUNT(*) >= 3
        )
        SELECT t.vendor, COALESCE(c.name, 'Uncategorized'), COALESCE(t.base_amount, t.amount), ROUND(ca.avg_amt, 0)
        FROM transactions t
        JOIN cat_avg ca ON ca.category_id = t.category_id
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.user_id = $1 AND t.status = 'confirmed' AND t.is_transfer = FALSE
          AND t.tx_type = 'debit' AND t.tx_date >= $2 AND t.tx_date < $3
          AND COALESCE(t.base_amount, t.amount) >= ca.avg_amt * 2.5
        ORDER BY COALESCE(t.base_amount, t.amount) DESC
        LIMIT 3
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .bind(end_date)
    .fetch_all(&state.db)
    .await?;

    for (vendor, cat_name, amt, avg_amt) in anomalies {
        alerts.push(SpendingAlertItem {
            kind: "anomaly".to_string(),
            title: format!("Unusually large {cat_name}: {vendor}"),
            detail: format!("{amt} vs your {cat_name} avg of {avg_amt} {cur}"),
            amount: amt,
            currency: cur.clone(),
        });
    }

    let _ = today.year();
    Ok(Json(ForecastAndHealthResponse {
        year: params.year,
        month: params.month,
        base_currency: cur,
        current_running_balance,
        upcoming_recurring_total,
        daily_burn_rate,
        remaining_days,
        projected_month_end_balance,
        net_worth_points,
        alerts,
    }))
}

// ================================
// Global All-Time Search (#1)
// ================================

pub async fn search_all_transactions(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<GlobalSearchQuery>,
) -> Result<Json<GlobalSearchResponse>, AppError> {
    let q = params.q.unwrap_or_default().trim().to_string();
    let pattern = format!("%{}%", q.to_lowercase());
    let tag_exact = q.trim_start_matches('#').to_lowercase();

    let transactions: Vec<TransactionRecord> = sqlx::query_as(
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
        WHERE t.user_id = $1
          AND t.status = 'confirmed'
          AND (
              $2 = '%%'
              OR LOWER(t.vendor) LIKE $2
              OR LOWER(COALESCE(t.description, '')) LIKE $2
              OR LOWER(COALESCE(c.name, '')) LIKE $2
              OR LOWER(COALESCE(w.name, '')) LIKE $2
              OR $3 = ANY(t.tags)
          )
          AND ($4::NUMERIC IS NULL OR COALESCE(t.base_amount, t.amount) >= $4)
          AND ($5::NUMERIC IS NULL OR COALESCE(t.base_amount, t.amount) <= $5)
        ORDER BY t.tx_date DESC, t.created_at DESC
        LIMIT 100
        "#,
    )
    .bind(auth.user_id)
    .bind(&pattern)
    .bind(&tag_exact)
    .bind(params.min_amount)
    .bind(params.max_amount)
    .fetch_all(&state.db)
    .await?;

    let mut total_spent = Decimal::ZERO;
    let mut total_credit = Decimal::ZERO;
    for t in &transactions {
        if t.is_transfer {
            continue;
        }
        let val = t.base_amount.unwrap_or(t.amount);
        if t.tx_type.eq_ignore_ascii_case("credit") {
            total_credit += val;
        } else {
            total_spent += val;
        }
    }

    Ok(Json(GlobalSearchResponse {
        query: q,
        base_currency: state.config.default_currency.clone(),
        total_spent,
        total_credit,
        count: transactions.len(),
        transactions,
    }))
}

// ================================
// Day-of-Week Spending Breakdown (#2)
// ================================

pub async fn weekday_breakdown(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<WeekdayQuery>,
) -> Result<Json<WeekdayBreakdownResponse>, AppError> {
    let months = params.months.unwrap_or(3).clamp(1, 12);
    let cutoff = Utc::now().date_naive() - chrono::Duration::days((months as i64) * 30);

    let rows: Vec<(i32, Decimal, i64)> = sqlx::query_as(
        r#"
        SELECT
            EXTRACT(ISODOW FROM tx_date)::INT AS dow,
            COALESCE(SUM(COALESCE(base_amount, amount)), 0) AS total_spent,
            COUNT(*) AS tx_count
        FROM transactions
        WHERE user_id = $1
          AND status = 'confirmed'
          AND is_transfer = FALSE
          AND tx_type = 'debit'
          AND tx_date >= $2
        GROUP BY 1
        "#,
    )
    .bind(auth.user_id)
    .bind(cutoff)
    .fetch_all(&state.db)
    .await?;

    let labels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
    let mut days: Vec<WeekdaySpendingItem> = Vec::with_capacity(7);
    let mut busiest_day: Option<String> = None;
    let mut busiest_amt = Decimal::ZERO;

    for (idx, lbl) in labels.iter().enumerate() {
        let dow = (idx + 1) as i32;
        let found = rows.iter().find(|r| r.0 == dow);
        let total = found.map(|r| r.1).unwrap_or(Decimal::ZERO);
        let cnt = found.map(|r| r.2).unwrap_or(0);
        if total > busiest_amt {
            busiest_amt = total;
            busiest_day = Some((*lbl).to_string());
        }
        days.push(WeekdaySpendingItem {
            dow,
            label: (*lbl).to_string(),
            total_spent: total,
            tx_count: cnt,
        });
    }

    Ok(Json(WeekdayBreakdownResponse {
        months,
        base_currency: state.config.default_currency.clone(),
        busiest_day,
        days,
    }))
}

// ================================
// Quick Built-In Currency Converter Rates (#3)
// ================================

pub async fn get_fx_rates(
    State(state): State<AppState>,
    _auth: AuthUser,
) -> Result<Json<FxRatesResponse>, AppError> {
    let all = state.fx.get_rates().await;
    let keep = ["USD", "VND", "EUR", "GBP", "JPY", "SGD", "THB", "KRW", "AUD", "CAD"];
    let mut rates_vs_usd = std::collections::HashMap::new();
    for code in keep {
        if let Some(v) = all.get(code) {
            rates_vs_usd.insert(code.to_string(), *v);
        }
    }
    Ok(Json(FxRatesResponse {
        base_currency: state.config.default_currency.clone(),
        rates_vs_usd,
    }))
}

// ================================
// v1.8.0: Annual Year-in-Review ("Wrapped")
// ================================

pub async fn yearly_wrapped(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<YearlyWrappedQuery>,
) -> Result<Json<YearlyWrappedResponse>, AppError> {
    let year = params.year.unwrap_or_else(|| Utc::now().year());
    let start_date = NaiveDate::from_ymd_opt(year, 1, 1)
        .ok_or_else(|| AppError::BadRequest("Invalid year".to_string()))?;
    let end_date = NaiveDate::from_ymd_opt(year + 1, 1, 1)
        .ok_or_else(|| AppError::BadRequest("Invalid year".to_string()))?;

    let monthly_rows: Vec<(i32, Decimal, Decimal, i64)> = sqlx::query_as(
        r#"
        SELECT
            EXTRACT(MONTH FROM tx_date)::INT AS m,
            COALESCE(SUM(CASE WHEN tx_type = 'debit' THEN COALESCE(base_amount, amount) ELSE 0 END), 0) AS spent,
            COALESCE(SUM(CASE WHEN tx_type = 'credit' THEN COALESCE(base_amount, amount) ELSE 0 END), 0) AS income,
            COUNT(*) AS cnt
        FROM transactions
        WHERE user_id = $1
          AND status = 'confirmed'
          AND is_transfer = FALSE
          AND tx_date >= $2
          AND tx_date < $3
        GROUP BY 1
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .bind(end_date)
    .fetch_all(&state.db)
    .await?;

    let month_labels = [
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    ];
    let mut months = Vec::with_capacity(12);
    let mut total_spent = Decimal::ZERO;
    let mut total_income = Decimal::ZERO;
    let mut transaction_count: i64 = 0;
    let mut best_month: Option<String> = None;
    let mut best_spent: Option<Decimal> = None;
    let mut busiest_month: Option<String> = None;
    let mut busiest_spent = Decimal::ZERO;

    for (idx, lbl) in month_labels.iter().enumerate() {
        let m = (idx + 1) as i32;
        let found = monthly_rows.iter().find(|r| r.0 == m);
        let spent = found.map(|r| r.1).unwrap_or(Decimal::ZERO);
        let income = found.map(|r| r.2).unwrap_or(Decimal::ZERO);
        let cnt = found.map(|r| r.3).unwrap_or(0);
        total_spent += spent;
        total_income += income;
        transaction_count += cnt;
        if spent > Decimal::ZERO {
            if best_spent.map(|b| spent < b).unwrap_or(true) {
                best_spent = Some(spent);
                best_month = Some((*lbl).to_string());
            }
            if spent > busiest_spent {
                busiest_spent = spent;
                busiest_month = Some((*lbl).to_string());
            }
        }
        months.push(YearlyMonthItem {
            month: m as u32,
            label: (*lbl).to_string(),
            total_spent: spent,
            total_income: income,
            net: income - spent,
            tx_count: cnt,
        });
    }

    let top_categories: Vec<CategorySpending> = sqlx::query_as(
        r#"
        SELECT
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
        GROUP BY 1
        ORDER BY total_spent DESC
        LIMIT 5
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .bind(end_date)
    .fetch_all(&state.db)
    .await?;

    let top_merchants: Vec<TopMerchantItem> = sqlx::query_as(
        r#"
        SELECT
            t.vendor,
            COALESCE(SUM(COALESCE(t.base_amount, t.amount)), 0) AS total_spent,
            COUNT(*) AS transaction_count
        FROM transactions t
        WHERE t.user_id = $1
          AND t.status = 'confirmed'
          AND t.is_transfer = FALSE
          AND t.tx_type = 'debit'
          AND t.tx_date >= $2
          AND t.tx_date < $3
        GROUP BY t.vendor
        ORDER BY total_spent DESC
        LIMIT 5
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .bind(end_date)
    .fetch_all(&state.db)
    .await?;

    let biggest_row: Option<(String, Decimal, NaiveDate, String)> = sqlx::query_as(
        r#"
        SELECT
            t.vendor,
            COALESCE(t.base_amount, t.amount) AS amt,
            t.tx_date,
            COALESCE(c.name, 'Uncategorized') AS category_name
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.user_id = $1
          AND t.status = 'confirmed'
          AND t.is_transfer = FALSE
          AND t.tx_type = 'debit'
          AND t.tx_date >= $2
          AND t.tx_date < $3
        ORDER BY amt DESC
        LIMIT 1
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .bind(end_date)
    .fetch_optional(&state.db)
    .await?;

    let biggest_expense = biggest_row.map(|(vendor, amount, tx_date, category_name)| {
        YearlyBiggestExpense {
            vendor,
            amount,
            tx_date,
            category_name,
        }
    });

    let net_savings = total_income - total_spent;
    let savings_rate_pct = if total_income > Decimal::ZERO {
        let rate = (net_savings / total_income) * Decimal::from(100);
        Some((rate.to_string().parse::<f64>().unwrap_or(0.0) * 10.0).round() / 10.0)
    } else {
        None
    };

    Ok(Json(YearlyWrappedResponse {
        year,
        base_currency: state.config.default_currency.clone(),
        total_spent,
        total_income,
        net_savings,
        savings_rate_pct,
        transaction_count,
        best_month,
        busiest_month,
        biggest_expense,
        months,
        top_categories,
        top_merchants,
    }))
}

// ================================
// v1.8.0: 50/30/20 Rule Breakdown
// ================================

fn is_need_category(name: &str) -> bool {
    matches!(
        name.trim().to_lowercase().as_str(),
        "groceries"
            | "utilities"
            | "housing"
            | "rent"
            | "health"
            | "medical"
            | "transport"
            | "education"
            | "insurance"
            | "debt repayment"
            | "family & kids"
            | "fees & interest"
            | "bills"
    )
}

pub async fn rule_50_30_20(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<MonthlyQuery>,
) -> Result<Json<Rule503020Response>, AppError> {
    let start_date = NaiveDate::from_ymd_opt(params.year, params.month, 1)
        .ok_or_else(|| AppError::BadRequest("Invalid year or month".to_string()))?;
    let end_date = if params.month == 12 {
        NaiveDate::from_ymd_opt(params.year + 1, 1, 1)
    } else {
        NaiveDate::from_ymd_opt(params.year, params.month + 1, 1)
    }
    .ok_or_else(|| AppError::BadRequest("Invalid year or month".to_string()))?;

    let total_income: Decimal = sqlx::query_scalar(
        r#"
        SELECT COALESCE(SUM(COALESCE(base_amount, amount)), 0)
        FROM transactions
        WHERE user_id = $1
          AND status = 'confirmed'
          AND is_transfer = FALSE
          AND tx_type = 'credit'
          AND tx_date >= $2
          AND tx_date < $3
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .bind(end_date)
    .fetch_one(&state.db)
    .await?;

    let rows: Vec<(String, Decimal)> = sqlx::query_as(
        r#"
        SELECT
            COALESCE(c.name, 'Uncategorized') AS category_name,
            COALESCE(SUM(COALESCE(t.base_amount, t.amount)), 0) AS total
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.user_id = $1
          AND t.status = 'confirmed'
          AND t.is_transfer = FALSE
          AND t.tx_type = 'debit'
          AND t.tx_date >= $2
          AND t.tx_date < $3
        GROUP BY 1
        ORDER BY total DESC
        "#,
    )
    .bind(auth.user_id)
    .bind(start_date)
    .bind(end_date)
    .fetch_all(&state.db)
    .await?;

    let mut needs_amount = Decimal::ZERO;
    let mut wants_amount = Decimal::ZERO;
    let mut needs_categories = Vec::new();
    let mut wants_categories = Vec::new();

    for (cat, amt) in rows {
        if is_need_category(&cat) {
            needs_amount += amt;
            needs_categories.push(cat);
        } else {
            wants_amount += amt;
            wants_categories.push(cat);
        }
    }

    let total_spent = needs_amount + wants_amount;
    let savings_amount = if total_income > total_spent {
        total_income - total_spent
    } else {
        Decimal::ZERO
    };
    let denom = if total_income > Decimal::ZERO {
        total_income
    } else {
        total_spent
    };

    let to_pct = |v: Decimal| -> f64 {
        if denom <= Decimal::ZERO {
            return 0.0;
        }
        let p = ((v / denom) * Decimal::from(100))
            .to_string()
            .parse::<f64>()
            .unwrap_or(0.0);
        (p * 10.0).round() / 10.0
    };

    Ok(Json(Rule503020Response {
        year: params.year,
        month: params.month,
        base_currency: state.config.default_currency.clone(),
        total_income,
        total_spent,
        needs_amount,
        wants_amount,
        savings_amount,
        needs_pct: to_pct(needs_amount),
        wants_pct: to_pct(wants_amount),
        savings_pct: to_pct(savings_amount),
        needs_categories,
        wants_categories,
    }))
}

// ================================
// v1.8.0: Merchant Lifetime Profile & History
// ================================

pub async fn merchant_profile(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<MerchantProfileQuery>,
) -> Result<Json<MerchantProfileResponse>, AppError> {
    let vendor = params.vendor.trim().to_string();
    if vendor.is_empty() {
        return Err(AppError::BadRequest("vendor is required".to_string()));
    }
    let pattern = format!("%{}%", vendor.to_lowercase());

    let stats: (Decimal, i64, Decimal, Decimal, Option<NaiveDate>, Option<NaiveDate>) =
        sqlx::query_as(
            r#"
            SELECT
                COALESCE(SUM(COALESCE(base_amount, amount)), 0),
                COUNT(*),
                COALESCE(MIN(COALESCE(base_amount, amount)), 0),
                COALESCE(MAX(COALESCE(base_amount, amount)), 0),
                MIN(tx_date),
                MAX(tx_date)
            FROM transactions
            WHERE user_id = $1
              AND status = 'confirmed'
              AND is_transfer = FALSE
              AND tx_type = 'debit'
              AND LOWER(vendor) LIKE $2
            "#,
        )
        .bind(auth.user_id)
        .bind(&pattern)
        .fetch_one(&state.db)
        .await?;

    let (total_spent, visit_count, min_amount, max_amount, first_seen, last_seen) = stats;
    let avg_ticket = if visit_count > 0 {
        (total_spent / Decimal::from(visit_count)).round_dp(2)
    } else {
        Decimal::ZERO
    };

    let monthly_rows: Vec<(i32, i32, Decimal, i64)> = sqlx::query_as(
        r#"
        SELECT
            EXTRACT(YEAR FROM tx_date)::INT AS y,
            EXTRACT(MONTH FROM tx_date)::INT AS m,
            COALESCE(SUM(COALESCE(base_amount, amount)), 0) AS total,
            COUNT(*) AS cnt
        FROM transactions
        WHERE user_id = $1
          AND status = 'confirmed'
          AND is_transfer = FALSE
          AND tx_type = 'debit'
          AND LOWER(vendor) LIKE $2
        GROUP BY 1, 2
        ORDER BY 1 DESC, 2 DESC
        LIMIT 6
        "#,
    )
    .bind(auth.user_id)
    .bind(&pattern)
    .fetch_all(&state.db)
    .await?;

    let mut monthly_history: Vec<MerchantMonthPoint> = monthly_rows
        .into_iter()
        .map(|(y, m, total_spent, tx_count)| MerchantMonthPoint {
            year: y,
            month: m as u32,
            total_spent,
            tx_count,
        })
        .collect();
    monthly_history.reverse();

    let recent_transactions: Vec<TransactionRecord> = sqlx::query_as(
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
        WHERE t.user_id = $1
          AND t.status = 'confirmed'
          AND t.is_transfer = FALSE
          AND t.tx_type = 'debit'
          AND LOWER(t.vendor) LIKE $2
        ORDER BY t.tx_date DESC, t.created_at DESC
        LIMIT 15
        "#,
    )
    .bind(auth.user_id)
    .bind(&pattern)
    .fetch_all(&state.db)
    .await?;

    Ok(Json(MerchantProfileResponse {
        vendor,
        base_currency: state.config.default_currency.clone(),
        total_spent,
        visit_count,
        avg_ticket,
        min_amount,
        max_amount,
        first_seen,
        last_seen,
        monthly_history,
        recent_transactions,
    }))
}

// ================================
// v1.8.0: Smart Budget Auto-Suggest (3-Month Average)
// ================================

fn round_suggested_budget(avg: Decimal, currency: &str) -> Decimal {
    let val = avg.to_string().parse::<f64>().unwrap_or(0.0);
    if val <= 0.0 {
        return Decimal::ZERO;
    }
    let step = if currency.eq_ignore_ascii_case("VND") {
        if val >= 500_000.0 {
            100_000.0
        } else if val >= 50_000.0 {
            50_000.0
        } else {
            10_000.0
        }
    } else if val >= 100.0 {
        10.0
    } else {
        5.0
    };
    let rounded = (val / step).ceil() * step;
    Decimal::from(rounded as i64)
}

pub async fn suggest_budgets(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<BudgetSuggestionsResponse>, AppError> {
    let months_analyzed: u32 = 3;
    let cutoff = Utc::now().date_naive() - chrono::Duration::days(90);
    let cur = state.config.default_currency.clone();

    let rows: Vec<(String, Decimal, Option<Decimal>)> = sqlx::query_as(
        r#"
        SELECT
            COALESCE(c.name, 'Uncategorized') AS category_name,
            COALESCE(SUM(COALESCE(t.base_amount, t.amount)), 0) AS total_90d,
            MAX(b.monthly_limit) AS current_limit
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN budgets b ON b.user_id = $1 AND b.category_id = t.category_id
        WHERE t.user_id = $1
          AND t.status = 'confirmed'
          AND t.is_transfer = FALSE
          AND t.tx_type = 'debit'
          AND t.tx_date >= $2
        GROUP BY 1
        HAVING COALESCE(SUM(COALESCE(t.base_amount, t.amount)), 0) > 0
        ORDER BY total_90d DESC
        LIMIT 12
        "#,
    )
    .bind(auth.user_id)
    .bind(cutoff)
    .fetch_all(&state.db)
    .await?;

    let suggestions = rows
        .into_iter()
        .map(|(category_name, total_90d, current_limit)| {
            let avg = (total_90d / Decimal::from(months_analyzed)).round_dp(0);
            let suggested_limit = round_suggested_budget(avg, &cur);
            BudgetSuggestionItem {
                category_name,
                avg_monthly_spent: avg,
                suggested_limit,
                current_limit,
            }
        })
        .collect();

    Ok(Json(BudgetSuggestionsResponse {
        months_analyzed,
        base_currency: cur,
        suggestions,
    }))
}

// ================================
// v1.8.0: Subscription & Recurring Cost Audit
// ================================

pub async fn subscription_audit(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<SubscriptionAuditResponse>, AppError> {
    let cutoff = Utc::now().date_naive() - chrono::Duration::days(180);
    let cur = state.config.default_currency.clone();

    let rows: Vec<(String, String, Decimal, i64, NaiveDate)> = sqlx::query_as(
        r#"
        SELECT
            MIN(t.vendor) AS vendor,
            COALESCE(MAX(c.name), 'Subscriptions') AS category_name,
            ROUND(AVG(COALESCE(t.base_amount, t.amount)), 0) AS monthly_cost,
            COUNT(*) AS occurrences,
            MAX(t.tx_date) AS last_date
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.user_id = $1
          AND t.status = 'confirmed'
          AND t.is_transfer = FALSE
          AND t.tx_type = 'debit'
          AND t.tx_date >= $2
        GROUP BY LOWER(TRIM(t.vendor))
        HAVING BOOL_OR(t.is_recurring)
            OR COUNT(DISTINCT DATE_TRUNC('month', t.tx_date)) >= 2
        ORDER BY monthly_cost DESC
        LIMIT 20
        "#,
    )
    .bind(auth.user_id)
    .bind(cutoff)
    .fetch_all(&state.db)
    .await?;

    let mut monthly_total = Decimal::ZERO;
    let mut items = Vec::with_capacity(rows.len());
    for (vendor, category_name, monthly_cost, occurrences, last_date) in rows {
        let annual_cost = monthly_cost * Decimal::from(12);
        monthly_total += monthly_cost;
        items.push(SubscriptionAuditItem {
            vendor,
            category_name,
            monthly_cost,
            annual_cost,
            occurrences,
            last_date,
        });
    }
    let annual_total = monthly_total * Decimal::from(12);

    let income_90d: Decimal = sqlx::query_scalar(
        r#"
        SELECT COALESCE(SUM(COALESCE(base_amount, amount)), 0)
        FROM transactions
        WHERE user_id = $1
          AND status = 'confirmed'
          AND is_transfer = FALSE
          AND tx_type = 'credit'
          AND tx_date >= $2
        "#,
    )
    .bind(auth.user_id)
    .bind(Utc::now().date_naive() - chrono::Duration::days(90))
    .fetch_one(&state.db)
    .await?;

    let avg_monthly_income = income_90d / Decimal::from(3);
    let pct_of_avg_income = if avg_monthly_income > Decimal::ZERO {
        let p = ((monthly_total / avg_monthly_income) * Decimal::from(100))
            .to_string()
            .parse::<f64>()
            .unwrap_or(0.0);
        Some((p * 10.0).round() / 10.0)
    } else {
        None
    };

    Ok(Json(SubscriptionAuditResponse {
        base_currency: cur,
        monthly_total,
        annual_total,
        pct_of_avg_income,
        items,
    }))
}

// ================================
// v1.9.0: Single-Request Dashboard Bootstrap (runs core queries concurrently)
// ================================

pub async fn dashboard_bootstrap(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<BootstrapQuery>,
) -> Result<Json<DashboardBootstrapResponse>, AppError> {
    let year = params.year;
    let month = params.month;
    let start_date = NaiveDate::from_ymd_opt(year, month, 1)
        .ok_or_else(|| AppError::BadRequest("Invalid year or month".to_string()))?;
    let end_date = if month == 12 {
        NaiveDate::from_ymd_opt(year + 1, 1, 1)
    } else {
        NaiveDate::from_ymd_opt(year, month + 1, 1)
    }
    .ok_or_else(|| AppError::BadRequest("Invalid year or month".to_string()))?;

    let (prev_year, prev_month) = if month == 1 {
        (year - 1, 12)
    } else {
        (year, month - 1)
    };
    let prev_start = NaiveDate::from_ymd_opt(prev_year, prev_month, 1)
        .ok_or_else(|| AppError::BadRequest("Invalid prev month".to_string()))?;
    let today = Utc::now().date_naive();
    let days_in_month = (end_date - start_date).num_days() as u32;
    let compared_day = if year == today.year() && month == today.month() {
        today.day().min(days_in_month)
    } else {
        days_in_month
    };
    let cur_pace_end = start_date + chrono::Duration::days(compared_day as i64);
    let prev_month_end = start_date;
    let prev_days = (prev_month_end - prev_start).num_days() as u32;
    let prev_pace_end = prev_start + chrono::Duration::days(compared_day.min(prev_days) as i64);

    let tx_query = ListTransactionsQuery {
        year: if params.start_date.is_some() { None } else { Some(year) },
        month: if params.start_date.is_some() { None } else { Some(month) },
        start_date: params.start_date,
        end_date: params.end_date,
        status: params.status.or_else(|| Some("confirmed".to_string())),
        tag: None,
        wallet_id: None,
    };

    let (
        Json(monthly_spending),
        Json(transactions),
        Json(budgets),
        Json(wallets),
        Json(templates),
        Json(preferences),
        income_by_category,
        pace_pair,
    ) = tokio::try_join!(
        crate::routes::transactions::monthly_spending(
            State(state.clone()),
            auth.clone(),
            Query(MonthlyQuery { year, month }),
        ),
        crate::routes::transactions::list_transactions(
            State(state.clone()),
            auth.clone(),
            Query(tx_query),
        ),
        crate::routes::transactions::list_budgets(
            State(state.clone()),
            auth.clone(),
            Query(MonthlyQuery { year, month }),
        ),
        list_wallets(State(state.clone()), auth.clone()),
        list_templates(State(state.clone()), auth.clone()),
        get_preferences(State(state.clone()), auth.clone()),
        async {
            sqlx::query_as::<_, CategorySpending>(
                r#"
                SELECT
                    t.category_id,
                    COALESCE(c.name, 'Income') AS category_name,
                    COALESCE(SUM(COALESCE(t.base_amount, t.amount)), 0) AS total_spent,
                    COUNT(*) AS transaction_count
                FROM transactions t
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.user_id = $1
                  AND t.status = 'confirmed'
                  AND t.is_transfer = FALSE
                  AND t.tx_type = 'credit'
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
            .await
            .map_err(AppError::from)
        },
        async {
            let cur_amt: Decimal = sqlx::query_scalar(
                r#"
                SELECT COALESCE(SUM(COALESCE(base_amount, amount)), 0)
                FROM transactions
                WHERE user_id = $1
                  AND status = 'confirmed'
                  AND is_transfer = FALSE
                  AND tx_type = 'debit'
                  AND tx_date >= $2
                  AND tx_date < $3
                "#,
            )
            .bind(auth.user_id)
            .bind(start_date)
            .bind(cur_pace_end)
            .fetch_one(&state.db)
            .await
            .map_err(AppError::from)?;

            let prev_amt: Decimal = sqlx::query_scalar(
                r#"
                SELECT COALESCE(SUM(COALESCE(base_amount, amount)), 0)
                FROM transactions
                WHERE user_id = $1
                  AND status = 'confirmed'
                  AND is_transfer = FALSE
                  AND tx_type = 'debit'
                  AND tx_date >= $2
                  AND tx_date < $3
                "#,
            )
            .bind(auth.user_id)
            .bind(prev_start)
            .bind(prev_pace_end)
            .fetch_one(&state.db)
            .await
            .map_err(AppError::from)?;

            Ok::<(Decimal, Decimal), AppError>((cur_amt, prev_amt))
        }
    )?;

    let (current_spent, previous_spent) = pace_pair;
    let diff_pct = if previous_spent > Decimal::ZERO {
        let p = (((current_spent - previous_spent) / previous_spent) * Decimal::from(100))
            .to_string()
            .parse::<f64>()
            .unwrap_or(0.0);
        Some((p * 10.0).round() / 10.0)
    } else {
        None
    };

    Ok(Json(DashboardBootstrapResponse {
        monthly_spending,
        transactions,
        budgets,
        wallets,
        templates,
        preferences,
        income_by_category,
        pace_vs_last_month: PaceComparison {
            current_spent,
            previous_spent,
            diff_pct,
            compared_through_day: compared_day,
        },
    }))
}

// ================================
// v1.9.0: Duplicate Transaction Scanner
// ================================

pub async fn scan_duplicates(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<DuplicateScanResponse>, AppError> {
    let cutoff = Utc::now().date_naive() - chrono::Duration::days(180);
    let txs: Vec<TransactionRecord> = sqlx::query_as(
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
        WHERE t.user_id = $1
          AND t.status = 'confirmed'
          AND t.is_transfer = FALSE
          AND t.tx_date >= $2
        ORDER BY t.tx_date DESC, t.created_at DESC
        LIMIT 500
        "#,
    )
    .bind(auth.user_id)
    .bind(cutoff)
    .fetch_all(&state.db)
    .await?;

    let mut pairs: Vec<DuplicatePair> = Vec::new();
    let mut flagged = std::collections::HashSet::new();

    for i in 0..txs.len() {
        if flagged.contains(&txs[i].id) {
            continue;
        }
        let a = &txs[i];
        let norm_a = normalize_text(&a.vendor);
        for j in (i + 1)..txs.len() {
            let b = &txs[j];
            if flagged.contains(&b.id) {
                continue;
            }
            let days_apart = (a.tx_date - b.tx_date).num_days().abs();
            if days_apart > 2 {
                break; // sorted DESC by tx_date
            }
            if a.tx_type == b.tx_type
                && a.amount == b.amount
                && normalize_text(&b.vendor) == norm_a
            {
                flagged.insert(b.id);
                pairs.push(DuplicatePair {
                    keep: a.clone(),
                    duplicate: b.clone(),
                    days_apart,
                });
                if pairs.len() >= 25 {
                    break;
                }
            }
        }
        if pairs.len() >= 25 {
            break;
        }
    }

    Ok(Json(DuplicateScanResponse {
        count: pairs.len(),
        pairs,
    }))
}

// ================================
// v2.0.0: Single-Request Analytics Bundle + Financial Health Score + Emergency Runway
// ================================

pub async fn dashboard_analytics(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<AnalyticsBundleQuery>,
) -> Result<Json<DashboardAnalyticsResponse>, AppError> {
    let year = params.year;
    let month = params.month;
    let wrapped_year = params.wrapped_year.unwrap_or(year);
    let cur = state.config.default_currency.clone();

    let (
        Json(trend),
        Json(recurring),
        Json(categories),
        Json(goals),
        Json(ious),
        Json(rules),
        Json(tags),
        Json(comparison),
        Json(forecast),
        Json(weekday),
        Json(fx_rates),
        Json(rule_50_30_20),
        Json(yearly_wrapped),
        Json(subscription_audit),
        Json(budgets),
        Json(wallets),
    ) = tokio::try_join!(
        crate::routes::transactions::spending_trend(
            State(state.clone()),
            auth.clone(),
            Query(TrendQuery { months: Some(6) }),
        ),
        crate::routes::transactions::list_recurring(State(state.clone()), auth.clone()),
        crate::routes::transactions::list_categories(State(state.clone()), auth.clone()),
        crate::routes::insights::list_goals(State(state.clone()), auth.clone()),
        crate::routes::insights::list_ious(State(state.clone()), auth.clone()),
        list_rules(State(state.clone()), auth.clone()),
        list_tags(State(state.clone()), auth.clone()),
        monthly_comparison(
            State(state.clone()),
            auth.clone(),
            Query(MonthlyQuery { year, month }),
        ),
        spending_forecast(
            State(state.clone()),
            auth.clone(),
            Query(MonthlyQuery { year, month }),
        ),
        weekday_breakdown(
            State(state.clone()),
            auth.clone(),
            Query(WeekdayQuery { months: Some(3) }),
        ),
        get_fx_rates(State(state.clone()), auth.clone()),
        rule_50_30_20(
            State(state.clone()),
            auth.clone(),
            Query(MonthlyQuery { year, month }),
        ),
        yearly_wrapped(
            State(state.clone()),
            auth.clone(),
            Query(YearlyWrappedQuery {
                year: Some(wrapped_year),
            }),
        ),
        subscription_audit(State(state.clone()), auth.clone()),
        crate::routes::transactions::list_budgets(
            State(state.clone()),
            auth.clone(),
            Query(MonthlyQuery { year, month }),
        ),
        list_wallets(State(state.clone()), auth.clone()),
    )?;

    // Financial Health Score (0-100): Savings (35) + Budgets (25) + 50/30/20 (20) + Consistency (20)
    let savings_pts = match comparison.savings_rate_pct {
        Some(r) if r >= 25.0 => 35,
        Some(r) if r >= 15.0 => 28,
        Some(r) if r >= 5.0 => 20,
        Some(r) if r > 0.0 => 12,
        Some(_) => 5,
        None => 15,
    };

    let budget_pts = if budgets.is_empty() {
        15
    } else {
        let ok_count = budgets
            .iter()
            .filter(|b| b.spent <= b.effective_limit)
            .count();
        ((ok_count as f64 / budgets.len() as f64) * 25.0).round() as i32
    };

    let rule_pts = {
        let mut pts = 20;
        if rule_50_30_20.needs_pct > 55.0 {
            pts -= 7;
        }
        if rule_50_30_20.wants_pct > 35.0 {
            pts -= 7;
        }
        if rule_50_30_20.savings_pct < 15.0 && rule_50_30_20.total_income > Decimal::ZERO {
            pts -= 6;
        }
        pts.max(0)
    };

    let consistency_pts = {
        let mut pts = 0;
        if comparison.no_spend_days >= 10 {
            pts += 12;
        } else if comparison.no_spend_days >= 5 {
            pts += 8;
        } else {
            pts += 4;
        }
        if forecast.current_running_balance >= Decimal::ZERO {
            pts += 8;
        }
        pts.min(20)
    };

    let score = (savings_pts + budget_pts + rule_pts + consistency_pts).clamp(0, 100);
    let grade = match score {
        90..=100 => "A+",
        80..=89 => "A",
        70..=79 => "B+",
        60..=69 => "B",
        50..=59 => "C",
        _ => "D",
    }
    .to_string();

    let tip = if savings_pts < 20 {
        "Boost your savings rate toward 20% to lift your score fastest.".to_string()
    } else if budget_pts < 20 {
        "Keep more categories under their monthly budget limit.".to_string()
    } else if rule_pts < 14 {
        "Trim discretionary Wants closer to 30% of income.".to_string()
    } else {
        "Strong financial discipline! Keep building your emergency runway.".to_string()
    };

    let health_score = FinancialHealthScore {
        score,
        grade,
        tip,
        savings_pts,
        budget_pts,
        rule_pts,
        consistency_pts,
    };

    // Emergency Fund Runway: Liquid Wallets (cash, bank, ewallet) / trailing 3-month avg spend
    let liquid_balance: Decimal = wallets
        .iter()
        .filter(|w| {
            matches!(
                w.wallet_type.to_lowercase().as_str(),
                "cash" | "bank" | "ewallet" | "other"
            )
        })
        .map(|w| w.current_balance)
        .fold(Decimal::ZERO, |acc, v| acc + v)
        .max(Decimal::ZERO);

    let spend_months: Vec<Decimal> = trend
        .iter()
        .rev()
        .take(3)
        .map(|p| p.total_spent)
        .filter(|v| *v > Decimal::ZERO)
        .collect();
    let avg_monthly_spend = if spend_months.is_empty() {
        Decimal::ZERO
    } else {
        let sum: Decimal = spend_months.iter().copied().sum();
        (sum / Decimal::from(spend_months.len() as i64)).round_dp(0)
    };
    let runway_months = if avg_monthly_spend > Decimal::ZERO {
        let m = (liquid_balance / avg_monthly_spend)
            .to_string()
            .parse::<f64>()
            .unwrap_or(0.0);
        (m * 10.0).round() / 10.0
    } else {
        0.0
    };

    let emergency_runway = EmergencyRunway {
        base_currency: cur,
        liquid_balance,
        avg_monthly_spend,
        runway_months,
        target_months: 6.0,
    };

    // v2.1.0: Monthly Spending Challenges & Streaks (0 extra tables)
    let no_spend_target = 10i64;
    let no_spend_pct = ((comparison.no_spend_days as f64 / no_spend_target as f64) * 100.0)
        .round()
        .clamp(0.0, 100.0) as i32;
    let ok_budgets = budgets.iter().filter(|b| b.spent <= b.effective_limit).count();
    let budget_chal_pct = if budgets.is_empty() {
        0
    } else {
        ((ok_budgets as f64 / budgets.len() as f64) * 100.0).round() as i32
    };
    let sav_rate = comparison.savings_rate_pct.unwrap_or(0.0);
    let sav_chal_pct = ((sav_rate / 20.0) * 100.0).round().clamp(0.0, 100.0) as i32;
    let wants_ok = rule_50_30_20.total_spent > Decimal::ZERO && rule_50_30_20.wants_pct <= 30.0;
    let wants_pct = if rule_50_30_20.total_spent <= Decimal::ZERO {
        0
    } else if wants_ok {
        100
    } else {
        ((30.0 / rule_50_30_20.wants_pct.max(1.0)) * 100.0).round().clamp(0.0, 100.0) as i32
    };

    let challenges = vec![
        SpendingChallenge {
            id: "no_spend_10".to_string(),
            title: "10 No-Spend Days".to_string(),
            detail: format!(
                "{}/{} days · current streak: {}d",
                comparison.no_spend_days, no_spend_target, comparison.current_no_spend_streak
            ),
            progress_pct: no_spend_pct,
            completed: comparison.no_spend_days >= no_spend_target,
        },
        SpendingChallenge {
            id: "under_budget".to_string(),
            title: "All Categories Under Budget".to_string(),
            detail: if budgets.is_empty() {
                "Set at least 1 budget to track".to_string()
            } else {
                format!("{}/{} categories on track", ok_budgets, budgets.len())
            },
            progress_pct: budget_chal_pct,
            completed: !budgets.is_empty() && ok_budgets == budgets.len(),
        },
        SpendingChallenge {
            id: "savings_20".to_string(),
            title: "20% Savings Rate".to_string(),
            detail: format!("Current: {:.1}% (target 20%)", sav_rate),
            progress_pct: sav_chal_pct,
            completed: sav_rate >= 20.0,
        },
        SpendingChallenge {
            id: "wants_30".to_string(),
            title: "Keep Wants ≤ 30%".to_string(),
            detail: format!("Current Wants: {:.1}%", rule_50_30_20.wants_pct),
            progress_pct: wants_pct,
            completed: wants_ok,
        },
    ];

    Ok(Json(DashboardAnalyticsResponse {
        trend,
        recurring,
        categories,
        goals,
        ious,
        rules,
        tags,
        comparison,
        forecast,
        weekday,
        fx_rates,
        rule_50_30_20,
        yearly_wrapped,
        subscription_audit,
        health_score,
        emergency_runway,
        challenges,
    }))
}

// ================================
// v2.0.0: 6-Month Category Trend Drilldown
// ================================

pub async fn category_history(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<CategoryHistoryQuery>,
) -> Result<Json<CategoryHistoryResponse>, AppError> {
    let cat_name = params.category.trim().to_string();
    if cat_name.is_empty() {
        return Err(AppError::BadRequest("category is required".to_string()));
    }
    let months = params.months.unwrap_or(6).clamp(3, 12);
    let today = Utc::now().date_naive();
    let mut cur_y = today.year();
    let mut cur_m = today.month();
    let mut slots: Vec<(i32, u32)> = Vec::with_capacity(months as usize);
    for _ in 0..months {
        slots.push((cur_y, cur_m));
        if cur_m == 1 {
            cur_y -= 1;
            cur_m = 12;
        } else {
            cur_m -= 1;
        }
    }
    slots.reverse();

    let (first_y, first_m) = slots[0];
    let start_date = NaiveDate::from_ymd_opt(first_y, first_m, 1)
        .ok_or_else(|| AppError::BadRequest("Invalid date".to_string()))?;

    let rows: Vec<(i32, i32, Decimal, i64)> = sqlx::query_as(
        r#"
        SELECT
            EXTRACT(YEAR FROM t.tx_date)::INT AS y,
            EXTRACT(MONTH FROM t.tx_date)::INT AS m,
            COALESCE(SUM(COALESCE(t.base_amount, t.amount)), 0) AS total,
            COUNT(*) AS cnt
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.user_id = $1
          AND t.status = 'confirmed'
          AND t.is_transfer = FALSE
          AND t.tx_type = 'debit'
          AND LOWER(COALESCE(c.name, 'Uncategorized')) = LOWER($2)
          AND t.tx_date >= $3
        GROUP BY 1, 2
        "#,
    )
    .bind(auth.user_id)
    .bind(&cat_name)
    .bind(start_date)
    .fetch_all(&state.db)
    .await?;

    let month_labels = [
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    ];
    let mut total_spent = Decimal::ZERO;
    let mut points = Vec::with_capacity(slots.len());
    for (y, m) in slots {
        let found = rows.iter().find(|r| r.0 == y && r.1 == m as i32);
        let amt = found.map(|r| r.2).unwrap_or(Decimal::ZERO);
        let cnt = found.map(|r| r.3).unwrap_or(0);
        total_spent += amt;
        points.push(CategoryMonthPoint {
            year: y,
            month: m,
            label: month_labels[(m - 1) as usize].to_string(),
            total_spent: amt,
            tx_count: cnt,
        });
    }
    let avg_monthly = (total_spent / Decimal::from(months)).round_dp(0);

    Ok(Json(CategoryHistoryResponse {
        category_name: cat_name,
        base_currency: state.config.default_currency.clone(),
        months,
        total_spent,
        avg_monthly,
        points,
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
