use crate::{
    error::AppError,
    models::{
        AskLedgerRequest, AskLedgerResponse, CreateGoalRequest, CreateIouRequest, GoalRecord,
        IouRecord, MonthlyInsightsResponse, MonthlyQuery, UpdateGoalRequest,
    },
    routes::AppState,
    services::auth::AuthUser,
};
use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    Json,
};
use chrono::NaiveDate;
use rust_decimal::Decimal;
use uuid::Uuid;

// --- AI Monthly Spending Coach (1 Flash-Lite call) ---

pub async fn monthly_insights(
    State(state): State<AppState>,
    auth: AuthUser,
    Query(params): Query<MonthlyQuery>,
) -> Result<Json<MonthlyInsightsResponse>, AppError> {
    if !(1..=12).contains(&params.month) {
        return Err(AppError::BadRequest("month must be 1..=12".to_string()));
    }
    let start = NaiveDate::from_ymd_opt(params.year, params.month, 1)
        .ok_or_else(|| AppError::BadRequest("Invalid year/month".to_string()))?;
    let (ny, nm) = if params.month == 12 {
        (params.year + 1, 1)
    } else {
        (params.year, params.month + 1)
    };
    let end = NaiveDate::from_ymd_opt(ny, nm, 1).unwrap();

    let (py, pm) = if params.month == 1 {
        (params.year - 1, 12)
    } else {
        (params.year, params.month - 1)
    };
    let prev_start = NaiveDate::from_ymd_opt(py, pm, 1).unwrap();

    let (cur_spent, cur_credit): (Decimal, Decimal) = sqlx::query_as(
        r#"
        SELECT
            COALESCE(SUM(CASE WHEN tx_type = 'debit' THEN COALESCE(base_amount, amount) ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN tx_type = 'credit' THEN COALESCE(base_amount, amount) ELSE 0 END), 0)
        FROM transactions
        WHERE user_id = $1 AND status = 'confirmed' AND tx_date >= $2 AND tx_date < $3
        "#,
    )
    .bind(auth.user_id)
    .bind(start)
    .bind(end)
    .fetch_one(&state.db)
    .await?;

    let prev_spent: Decimal = sqlx::query_scalar(
        r#"
        SELECT COALESCE(SUM(COALESCE(base_amount, amount)), 0)
        FROM transactions
        WHERE user_id = $1 AND status = 'confirmed' AND tx_type = 'debit'
          AND tx_date >= $2 AND tx_date < $3
        "#,
    )
    .bind(auth.user_id)
    .bind(prev_start)
    .bind(start)
    .fetch_one(&state.db)
    .await?;

    let running_balance: Decimal = sqlx::query_scalar(
        r#"
        SELECT COALESCE(SUM(CASE WHEN tx_type = 'credit' THEN COALESCE(base_amount, amount) ELSE -COALESCE(base_amount, amount) END), 0)
        FROM transactions
        WHERE user_id = $1 AND status = 'confirmed' AND tx_date < $2
        "#,
    )
    .bind(auth.user_id)
    .bind(end)
    .fetch_one(&state.db)
    .await?;

    let top_cats: Vec<(String, Decimal)> = sqlx::query_as(
        r#"
        SELECT COALESCE(c.name, 'Uncategorized'), COALESCE(SUM(COALESCE(t.base_amount, t.amount)), 0)
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.user_id = $1 AND t.status = 'confirmed' AND t.tx_type = 'debit'
          AND t.tx_date >= $2 AND t.tx_date < $3
        GROUP BY c.name
        ORDER BY 2 DESC
        LIMIT 5
        "#,
    )
    .bind(auth.user_id)
    .bind(start)
    .bind(end)
    .fetch_all(&state.db)
    .await?;

    let cats_summary = if top_cats.is_empty() {
        "None yet".to_string()
    } else {
        top_cats
            .iter()
            .map(|(n, v)| format!("{n}: {v}"))
            .collect::<Vec<_>>()
            .join(", ")
    };

    let year = params.year;
    let month = params.month;
    let currency = &state.config.default_currency;
    let prompt = format!(
        "You are a concise, practical personal finance coach.\n\
         Month: {year}-{month:02} (Currency: {currency}).\n\
         This month spent (debit): {cur_spent}.\n\
         This month inflow (credit): {cur_credit}.\n\
         Previous month spent: {prev_spent}.\n\
         Cumulative running balance (negative = in debt): {running_balance}.\n\
         Top spending categories: {cats_summary}.\n\
         Give exactly 3 short, specific, actionable tips (1-2 sentences each) covering: \
         (1) biggest spending trend vs last month, (2) category optimization, and (3) debt payoff or savings pace."
    );

    let (tips, model_used) = state.gemini.generate_text_insights(&prompt).await?;

    Ok(Json(MonthlyInsightsResponse {
        year: params.year,
        month: params.month,
        model_used,
        tips,
    }))
}

// --- v2.1.0: "Ask Your Ledger" AI Q&A (1 free Flash-Lite call with compact pre-aggregated stats) ---

pub async fn ask_ledger(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<AskLedgerRequest>,
) -> Result<Json<AskLedgerResponse>, AppError> {
    let question = payload.question.trim().to_string();
    if question.is_empty() || question.len() > 300 {
        return Err(AppError::BadRequest(
            "question must be between 1 and 300 characters".to_string(),
        ));
    }
    let today = chrono::Utc::now().date_naive();
    let year = payload.year.unwrap_or_else(|| chrono::Datelike::year(&today));
    let month = payload.month.unwrap_or_else(|| chrono::Datelike::month(&today));
    let start = NaiveDate::from_ymd_opt(year, month, 1)
        .ok_or_else(|| AppError::BadRequest("Invalid year/month".to_string()))?;
    let (ny, nm) = if month == 12 { (year + 1, 1) } else { (year, month + 1) };
    let end = NaiveDate::from_ymd_opt(ny, nm, 1).unwrap();

    let (cur_spent, cur_credit): (Decimal, Decimal) = sqlx::query_as(
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
    .bind(start)
    .bind(end)
    .fetch_one(&state.db)
    .await?;

    let running_balance: Decimal = sqlx::query_scalar(
        r#"
        SELECT COALESCE(SUM(CASE WHEN tx_type = 'credit' THEN COALESCE(base_amount, amount) ELSE -COALESCE(base_amount, amount) END), 0)
        FROM transactions
        WHERE user_id = $1 AND status = 'confirmed' AND is_transfer = FALSE AND tx_date < $2
        "#,
    )
    .bind(auth.user_id)
    .bind(end)
    .fetch_one(&state.db)
    .await?;

    let top_cats: Vec<(String, Decimal)> = sqlx::query_as(
        r#"
        SELECT COALESCE(c.name, 'Uncategorized'), COALESCE(SUM(COALESCE(t.base_amount, t.amount)), 0)
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.user_id = $1 AND t.status = 'confirmed' AND t.is_transfer = FALSE
          AND t.tx_type = 'debit' AND t.tx_date >= $2 AND t.tx_date < $3
        GROUP BY c.name
        ORDER BY 2 DESC
        LIMIT 8
        "#,
    )
    .bind(auth.user_id)
    .bind(start)
    .bind(end)
    .fetch_all(&state.db)
    .await?;

    let top_merchants: Vec<(String, Decimal, i64)> = sqlx::query_as(
        r#"
        SELECT vendor, COALESCE(SUM(COALESCE(base_amount, amount)), 0), COUNT(*)
        FROM transactions
        WHERE user_id = $1 AND status = 'confirmed' AND is_transfer = FALSE
          AND tx_type = 'debit' AND tx_date >= $2 AND tx_date < $3
        GROUP BY vendor
        ORDER BY 2 DESC
        LIMIT 8
        "#,
    )
    .bind(auth.user_id)
    .bind(start)
    .bind(end)
    .fetch_all(&state.db)
    .await?;

    let cats_str = top_cats
        .iter()
        .map(|(n, v)| format!("{n}: {v}"))
        .collect::<Vec<_>>()
        .join(", ");
    let merch_str = top_merchants
        .iter()
        .map(|(v, amt, cnt)| format!("{v} ({cnt}x): {amt}"))
        .collect::<Vec<_>>()
        .join(", ");
    let currency = &state.config.default_currency;

    let prompt = format!(
        "You are a helpful, concise personal finance assistant answering a user's question based ONLY on their summary data.\n\
         Month: {year}-{month:02} (Currency: {currency}).\n\
         Month spent: {cur_spent}, Month income: {cur_credit}, Running balance: {running_balance}.\n\
         Top categories: {cats_str}.\n\
         Top merchants: {merch_str}.\n\
         User question: \"{question}\"\n\
         Reply in 1 to 3 concise, direct sentences with concrete numbers from the data."
    );

    let (lines, model_used) = state.gemini.generate_text_insights(&prompt).await?;
    let answer = lines.join(" ");

    Ok(Json(AskLedgerResponse {
        question,
        answer,
        model_used,
    }))
}

// --- Goals (Debt Payoff & Savings Pots) ---

pub async fn list_goals(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<Vec<GoalRecord>>, AppError> {
    let rows: Vec<GoalRecord> = sqlx::query_as(
        r#"
        SELECT id, name, goal_type, target_amount, current_amount, currency, target_date
        FROM goals
        WHERE user_id = $1
        ORDER BY created_at DESC
        "#,
    )
    .bind(auth.user_id)
    .fetch_all(&state.db)
    .await?;
    Ok(Json(rows))
}

pub async fn create_goal(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<CreateGoalRequest>,
) -> Result<(StatusCode, Json<GoalRecord>), AppError> {
    let name = payload.name.trim();
    if name.is_empty() {
        return Err(AppError::BadRequest("Goal name is required".to_string()));
    }
    if payload.target_amount <= Decimal::ZERO {
        return Err(AppError::BadRequest("target_amount must be positive".to_string()));
    }

    let goal_type = match payload.goal_type.as_deref().map(str::trim) {
        Some("debt_payoff") => "debt_payoff",
        _ => "savings",
    };
    let current = payload.current_amount.unwrap_or(Decimal::ZERO).max(Decimal::ZERO);
    let currency = payload
        .currency
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(|s| s.to_uppercase())
        .unwrap_or_else(|| state.config.default_currency.clone());

    let row: GoalRecord = sqlx::query_as(
        r#"
        INSERT INTO goals (user_id, name, goal_type, target_amount, current_amount, currency, target_date)
        VALUES ($1, $2, $3, $4, $5, $6, $7)
        RETURNING id, name, goal_type, target_amount, current_amount, currency, target_date
        "#,
    )
    .bind(auth.user_id)
    .bind(name)
    .bind(goal_type)
    .bind(payload.target_amount)
    .bind(current)
    .bind(currency)
    .bind(payload.target_date)
    .fetch_one(&state.db)
    .await?;

    Ok((StatusCode::CREATED, Json(row)))
}

pub async fn update_goal(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
    Json(payload): Json<UpdateGoalRequest>,
) -> Result<Json<GoalRecord>, AppError> {
    let row: Option<GoalRecord> = sqlx::query_as(
        r#"
        UPDATE goals
        SET name           = COALESCE($3, name),
            target_amount  = COALESCE($4, target_amount),
            current_amount = COALESCE($5, current_amount),
            target_date    = COALESCE($6, target_date),
            updated_at     = NOW()
        WHERE id = $1 AND user_id = $2
        RETURNING id, name, goal_type, target_amount, current_amount, currency, target_date
        "#,
    )
    .bind(id)
    .bind(auth.user_id)
    .bind(payload.name.as_deref().map(str::trim).filter(|s| !s.is_empty()))
    .bind(payload.target_amount)
    .bind(payload.current_amount)
    .bind(payload.target_date)
    .fetch_optional(&state.db)
    .await?;

    match row {
        Some(r) => Ok(Json(r)),
        None => Err(AppError::NotFound(format!("Goal {id} not found"))),
    }
}

pub async fn delete_goal(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, AppError> {
    let res = sqlx::query("DELETE FROM goals WHERE id = $1 AND user_id = $2")
        .bind(id)
        .bind(auth.user_id)
        .execute(&state.db)
        .await?;
    if res.rows_affected() == 0 {
        return Err(AppError::NotFound(format!("Goal {id} not found")));
    }
    Ok(StatusCode::NO_CONTENT)
}

// --- Split / IOUs ("Who Owes Me") ---

pub async fn list_ious(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<Vec<IouRecord>>, AppError> {
    let rows: Vec<IouRecord> = sqlx::query_as(
        r#"
        SELECT id, person_name, direction, amount, currency, note, settled, created_at
        FROM ious
        WHERE user_id = $1
        ORDER BY settled ASC, created_at DESC
        "#,
    )
    .bind(auth.user_id)
    .fetch_all(&state.db)
    .await?;
    Ok(Json(rows))
}

pub async fn create_iou(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<CreateIouRequest>,
) -> Result<(StatusCode, Json<IouRecord>), AppError> {
    let person = payload.person_name.trim();
    if person.is_empty() {
        return Err(AppError::BadRequest("person_name is required".to_string()));
    }
    if payload.amount <= Decimal::ZERO {
        return Err(AppError::BadRequest("amount must be positive".to_string()));
    }

    let direction = match payload.direction.as_deref().map(str::trim) {
        Some("i_owe") => "i_owe",
        _ => "owed_to_me",
    };
    let currency = payload
        .currency
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(|s| s.to_uppercase())
        .unwrap_or_else(|| state.config.default_currency.clone());

    let row: IouRecord = sqlx::query_as(
        r#"
        INSERT INTO ious (user_id, person_name, direction, amount, currency, note)
        VALUES ($1, $2, $3, $4, $5, $6)
        RETURNING id, person_name, direction, amount, currency, note, settled, created_at
        "#,
    )
    .bind(auth.user_id)
    .bind(person)
    .bind(direction)
    .bind(payload.amount)
    .bind(currency)
    .bind(payload.note.as_deref().map(str::trim).filter(|s| !s.is_empty()))
    .fetch_one(&state.db)
    .await?;

    Ok((StatusCode::CREATED, Json(row)))
}

pub async fn settle_iou(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
) -> Result<Json<IouRecord>, AppError> {
    let row: Option<IouRecord> = sqlx::query_as(
        r#"
        UPDATE ious
        SET settled = NOT settled,
            settled_at = CASE WHEN NOT settled THEN NOW() ELSE NULL END
        WHERE id = $1 AND user_id = $2
        RETURNING id, person_name, direction, amount, currency, note, settled, created_at
        "#,
    )
    .bind(id)
    .bind(auth.user_id)
    .fetch_optional(&state.db)
    .await?;

    match row {
        Some(r) => Ok(Json(r)),
        None => Err(AppError::NotFound(format!("IOU {id} not found"))),
    }
}

pub async fn delete_iou(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, AppError> {
    let res = sqlx::query("DELETE FROM ious WHERE id = $1 AND user_id = $2")
        .bind(id)
        .bind(auth.user_id)
        .execute(&state.db)
        .await?;
    if res.rows_affected() == 0 {
        return Err(AppError::NotFound(format!("IOU {id} not found")));
    }
    Ok(StatusCode::NO_CONTENT)
}
