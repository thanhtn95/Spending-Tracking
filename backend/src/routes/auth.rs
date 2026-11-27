use crate::{
    error::AppError,
    models::{AuthResponse, ChangePasswordRequest, LoginRequest, RegisterRequest, UserProfile},
    routes::AppState,
    services::auth::{create_access_token, hash_password, verify_password, AuthUser},
};
use axum::{extract::State, http::StatusCode, Json};
use chrono::{DateTime, Utc};
use uuid::Uuid;

#[derive(sqlx::FromRow)]
struct UserAuthRow {
    id: Uuid,
    email: String,
    password_hash: String,
    display_name: Option<String>,
    created_at: DateTime<Utc>,
}

pub async fn register(
    State(state): State<AppState>,
    Json(payload): Json<RegisterRequest>,
) -> Result<(StatusCode, Json<AuthResponse>), AppError> {
    if !state.config.allow_registration {
        return Err(AppError::Forbidden(
            "Public user registration is disabled".to_string(),
        ));
    }

    let email = normalize_and_validate_email(&payload.email)?;
    validate_password(&payload.password)?;

    let display_name = payload
        .display_name
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(ToString::to_string);

    let password_plain = payload.password;
    let password_hash = tokio::task::spawn_blocking(move || hash_password(&password_plain))
        .await
        .map_err(|e| AppError::Internal(format!("Password hashing task failed: {e}")))??;

    let inserted = sqlx::query_as::<_, UserProfile>(
        r#"
        INSERT INTO users (email, password_hash, display_name)
        VALUES ($1, $2, $3)
        RETURNING id, email, display_name, created_at
        "#,
    )
    .bind(&email)
    .bind(&password_hash)
    .bind(&display_name)
    .fetch_one(&state.db)
    .await;

    let user = match inserted {
        Ok(u) => u,
        Err(sqlx::Error::Database(db_err)) if db_err.code().as_deref() == Some("23505") => {
            return Err(AppError::Conflict(
                "An account with this email already exists".to_string(),
            ));
        }
        Err(e) => return Err(AppError::Database(e)),
    };

    let (access_token, expires_in) = create_access_token(
        user.id,
        &user.email,
        &state.config.jwt_secret,
        state.config.jwt_expiration_hours,
    )?;

    Ok((
        StatusCode::CREATED,
        Json(AuthResponse {
            access_token,
            token_type: "Bearer",
            expires_in,
            user,
        }),
    ))
}

pub async fn login(
    State(state): State<AppState>,
    Json(payload): Json<LoginRequest>,
) -> Result<Json<AuthResponse>, AppError> {
    let email = payload.email.trim().to_lowercase();
    if email.is_empty() || payload.password.is_empty() {
        return Err(AppError::BadRequest(
            "Email and password are required".to_string(),
        ));
    }

    let row: Option<UserAuthRow> = sqlx::query_as(
        r#"
        SELECT id, email, password_hash, display_name, created_at
        FROM users
        WHERE email = $1
        "#,
    )
    .bind(&email)
    .fetch_optional(&state.db)
    .await?;

    let Some(user_row) = row else {
        return Err(AppError::Unauthorized(
            "Invalid email or password".to_string(),
        ));
    };

    let password_plain = payload.password;
    let stored_hash = user_row.password_hash.clone();
    let valid = tokio::task::spawn_blocking(move || verify_password(&password_plain, &stored_hash))
        .await
        .map_err(|e| AppError::Internal(format!("Password verification task failed: {e}")))??;

    if !valid {
        return Err(AppError::Unauthorized(
            "Invalid email or password".to_string(),
        ));
    }

    let user = UserProfile {
        id: user_row.id,
        email: user_row.email,
        display_name: user_row.display_name,
        created_at: user_row.created_at,
    };

    let (access_token, expires_in) = create_access_token(
        user.id,
        &user.email,
        &state.config.jwt_secret,
        state.config.jwt_expiration_hours,
    )?;

    Ok(Json(AuthResponse {
        access_token,
        token_type: "Bearer",
        expires_in,
        user,
    }))
}

pub async fn me(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<UserProfile>, AppError> {
    let user: Option<UserProfile> = sqlx::query_as(
        r#"
        SELECT id, email, display_name, created_at
        FROM users
        WHERE id = $1
        "#,
    )
    .bind(auth.user_id)
    .fetch_optional(&state.db)
    .await?;

    match user {
        Some(u) => Ok(Json(u)),
        None => Err(AppError::Unauthorized("User account no longer exists".to_string())),
    }
}

pub async fn change_password(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(payload): Json<ChangePasswordRequest>,
) -> Result<StatusCode, AppError> {
    validate_password(&payload.new_password)?;

    let stored_hash: Option<String> =
        sqlx::query_scalar("SELECT password_hash FROM users WHERE id = $1")
            .bind(auth.user_id)
            .fetch_optional(&state.db)
            .await?;

    let Some(stored_hash) = stored_hash else {
        return Err(AppError::Unauthorized("User not found".to_string()));
    };

    let current_plain = payload.current_password;
    let valid = tokio::task::spawn_blocking(move || verify_password(&current_plain, &stored_hash))
        .await
        .map_err(|e| AppError::Internal(format!("Password verification task failed: {e}")))??;

    if !valid {
        return Err(AppError::BadRequest(
            "Current password is incorrect".to_string(),
        ));
    }

    let new_plain = payload.new_password;
    let new_hash = tokio::task::spawn_blocking(move || hash_password(&new_plain))
        .await
        .map_err(|e| AppError::Internal(format!("Password hashing task failed: {e}")))??;

    sqlx::query("UPDATE users SET password_hash = $2 WHERE id = $1")
        .bind(auth.user_id)
        .bind(&new_hash)
        .execute(&state.db)
        .await?;

    Ok(StatusCode::NO_CONTENT)
}

fn normalize_and_validate_email(raw: &str) -> Result<String, AppError> {
    let email = raw.trim().to_lowercase();
    let parts: Vec<&str> = email.split('@').collect();
    if parts.len() != 2 || parts[0].is_empty() || !parts[1].contains('.') {
        return Err(AppError::BadRequest(
            "Please provide a valid email address".to_string(),
        ));
    }
    Ok(email)
}

fn validate_password(password: &str) -> Result<(), AppError> {
    if password.len() < 8 {
        return Err(AppError::BadRequest(
            "Password must be at least 8 characters long".to_string(),
        ));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_and_normalizes_email() {
        assert_eq!(
            normalize_and_validate_email("  Alice@Example.COM ").unwrap(),
            "alice@example.com"
        );
        assert!(normalize_and_validate_email("not-an-email").is_err());
        assert!(normalize_and_validate_email("a@localhost").is_err());
    }

    #[test]
    fn enforces_minimum_password_length() {
        assert!(validate_password("short7!").is_err());
        assert!(validate_password("longenough8").is_ok());
    }
}
