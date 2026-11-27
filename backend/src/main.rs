mod config;
mod error;
mod models;
mod routes;
mod services;

use crate::{
    config::Config,
    routes::{build_router, AppState},
    services::{fx::FxService, gemini::GeminiClient},
};
use sqlx::postgres::PgPoolOptions;
use std::net::SocketAddr;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    dotenvy::dotenv().ok();

    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "spending_tracker_api=info,tower_http=info".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    let config = Config::from_env().map_err(|e| format!("Configuration error: {e}"))?;

    let db = PgPoolOptions::new()
        .max_connections(16)
        .connect(&config.database_url)
        .await?;

    if config.run_migrations {
        tracing::info!("Running embedded database migrations...");
        sqlx::migrate!("./migrations").run(&db).await?;
    }

    let gemini = GeminiClient::new(&config);
    let fx = FxService::new();
    let port = config.port;
    let state = AppState { db, gemini, fx, config };
    let app = build_router(state);

    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    tracing::info!(%addr, "Starting spending-tracker-api");

    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await?;

    Ok(())
}
