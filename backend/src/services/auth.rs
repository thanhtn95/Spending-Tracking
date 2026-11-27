use crate::{error::AppError, routes::AppState};
use argon2::{
    password_hash::{rand_core::OsRng, PasswordHash, PasswordHasher, PasswordVerifier, SaltString},
    Argon2,
};
use axum::{
    extract::FromRequestParts,
    http::{header::AUTHORIZATION, request::Parts},
};
use chrono::{Duration, Utc};
use jsonwebtoken::{decode, encode, Algorithm, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Claims {
    pub sub: Uuid,
    pub email: String,
    pub iat: i64,
    pub exp: i64,
}

/// Authenticated user extracted from `Authorization: Bearer <jwt>` header.
#[derive(Debug, Clone)]
pub struct AuthUser {
    pub user_id: Uuid,
    #[allow(dead_code)]
    pub email: String,
}

impl FromRequestParts<AppState> for AuthUser {
    type Rejection = AppError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        let header_value = parts
            .headers
            .get(AUTHORIZATION)
            .and_then(|v| v.to_str().ok())
            .ok_or_else(|| {
                AppError::Unauthorized("Missing Authorization header".to_string())
            })?;

        let token = header_value
            .strip_prefix("Bearer ")
            .or_else(|| header_value.strip_prefix("bearer "))
            .map(str::trim)
            .filter(|t| !t.is_empty())
            .ok_or_else(|| {
                AppError::Unauthorized("Authorization header must use Bearer scheme".to_string())
            })?;

        let claims = verify_access_token(token, &state.config.jwt_secret)?;
        Ok(AuthUser {
            user_id: claims.sub,
            email: claims.email,
        })
    }
}

/// Hashes a plaintext password using Argon2id with a random salt.
pub fn hash_password(password: &str) -> Result<String, AppError> {
    let salt = SaltString::generate(&mut OsRng);
    Argon2::default()
        .hash_password(password.as_bytes(), &salt)
        .map(|h| h.to_string())
        .map_err(|e| AppError::Internal(format!("Password hashing failed: {e}")))
}

/// Verifies a plaintext password against a stored Argon2 PHC hash string.
pub fn verify_password(password: &str, password_hash: &str) -> Result<bool, AppError> {
    let parsed_hash = PasswordHash::new(password_hash)
        .map_err(|e| AppError::Internal(format!("Stored password hash is invalid: {e}")))?;
    Ok(Argon2::default()
        .verify_password(password.as_bytes(), &parsed_hash)
        .is_ok())
}

/// Issues a signed HS256 JWT access token and returns `(token, expires_in_seconds)`.
pub fn create_access_token(
    user_id: Uuid,
    email: &str,
    secret: &str,
    expiration_hours: i64,
) -> Result<(String, i64), AppError> {
    let now = Utc::now();
    let expires_in = expiration_hours * 3600;
    let exp = (now + Duration::seconds(expires_in)).timestamp();

    let claims = Claims {
        sub: user_id,
        email: email.to_string(),
        iat: now.timestamp(),
        exp,
    };

    let token = encode(
        &Header::new(Algorithm::HS256),
        &claims,
        &EncodingKey::from_secret(secret.as_bytes()),
    )
    .map_err(|e| AppError::Internal(format!("JWT signing failed: {e}")))?;

    Ok((token, expires_in))
}

/// Validates a JWT access token signature and expiration.
pub fn verify_access_token(token: &str, secret: &str) -> Result<Claims, AppError> {
    let mut validation = Validation::new(Algorithm::HS256);
    validation.validate_exp = true;

    decode::<Claims>(
        token,
        &DecodingKey::from_secret(secret.as_bytes()),
        &validation,
    )
    .map(|data| data.claims)
    .map_err(|_| AppError::Unauthorized("Invalid or expired access token".to_string()))
}

#[cfg(test)]
mod tests {
    use super::*;

    const TEST_SECRET: &str = "super_secret_test_key_at_least_32_bytes_long";

    #[test]
    fn argon2_hash_and_verify_roundtrip() {
        let hash = hash_password("MyStr0ng!Pass").expect("hash should succeed");
        assert!(verify_password("MyStr0ng!Pass", &hash).unwrap());
        assert!(!verify_password("WrongPassword", &hash).unwrap());
    }

    #[test]
    fn jwt_create_and_verify_roundtrip() {
        let uid = Uuid::new_v4();
        let (token, expires_in) =
            create_access_token(uid, "user@example.com", TEST_SECRET, 24).expect("sign jwt");
        assert_eq!(expires_in, 24 * 3600);

        let claims = verify_access_token(&token, TEST_SECRET).expect("verify jwt");
        assert_eq!(claims.sub, uid);
        assert_eq!(claims.email, "user@example.com");
    }

    #[test]
    fn jwt_rejects_wrong_secret() {
        let uid = Uuid::new_v4();
        let (token, _) =
            create_access_token(uid, "user@example.com", TEST_SECRET, 24).expect("sign jwt");

        let res = verify_access_token(&token, "different_secret_key_at_least_32_bytes_long");
        assert!(matches!(res, Err(AppError::Unauthorized(_))));
    }
}
