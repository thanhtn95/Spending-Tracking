use std::env;

#[derive(Debug, Clone)]
pub struct Config {
    pub database_url: String,
    pub gemini_api_key: String,
    pub jwt_secret: String,
    pub jwt_expiration_hours: i64,
    pub allow_registration: bool,
    pub gemini_model_image: String,
    pub gemini_model_pdf: String,
    pub gemini_api_base: String,
    pub default_currency: String,
    pub port: u16,
    pub run_migrations: bool,
}

impl Config {
    pub fn from_env() -> Result<Self, String> {
        let database_url = env::var("DATABASE_URL")
            .map_err(|_| "DATABASE_URL environment variable is required".to_string())?;
        let gemini_api_key = env::var("GEMINI_API_KEY")
            .map_err(|_| "GEMINI_API_KEY environment variable is required".to_string())?;
        let jwt_secret = env::var("JWT_SECRET")
            .map_err(|_| "JWT_SECRET environment variable is required".to_string())?;

        if jwt_secret.len() < 32 {
            return Err("JWT_SECRET must be at least 32 characters long".to_string());
        }

        let jwt_expiration_hours = env::var("JWT_EXPIRATION_HOURS")
            .ok()
            .and_then(|v| v.parse::<i64>().ok())
            .filter(|&h| h > 0)
            .unwrap_or(168); // 7 days default for mobile client
        let allow_registration = env::var("ALLOW_REGISTRATION")
            .map(|v| v != "false" && v != "0")
            .unwrap_or(true);

        let gemini_model_image = env::var("GEMINI_MODEL_IMAGE")
            .unwrap_or_else(|_| "gemini-3.5-flash-lite".to_string());
        let gemini_model_pdf = env::var("GEMINI_MODEL_PDF")
            .unwrap_or_else(|_| "gemini-3.8-flash".to_string());
        let gemini_api_base = env::var("GEMINI_API_BASE")
            .unwrap_or_else(|_| "https://generativelanguage.googleapis.com/v1beta".to_string());
        let default_currency = env::var("DEFAULT_CURRENCY")
            .unwrap_or_else(|_| "VND".to_string());
        let port = env::var("PORT")
            .ok()
            .and_then(|p| p.parse::<u16>().ok())
            .unwrap_or(8080);
        let run_migrations = env::var("RUN_MIGRATIONS")
            .map(|v| v != "false" && v != "0")
            .unwrap_or(true);

        Ok(Self {
            database_url,
            gemini_api_key,
            jwt_secret,
            jwt_expiration_hours,
            allow_registration,
            gemini_model_image,
            gemini_model_pdf,
            gemini_api_base,
            default_currency,
            port,
            run_migrations,
        })
    }
}
