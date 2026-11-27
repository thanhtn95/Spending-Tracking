pub mod auth;
pub mod extras;
pub mod health;
pub mod ingest;
pub mod insights;
pub mod transactions;
pub mod web;

use crate::{
    config::Config,
    services::{fx::FxService, gemini::GeminiClient},
};
use axum::{
    extract::DefaultBodyLimit,
    routing::{get, post},
    Router,
};
use sqlx::PgPool;
use tower_http::{cors::CorsLayer, trace::TraceLayer};

#[derive(Clone)]
pub struct AppState {
    pub db: PgPool,
    pub gemini: GeminiClient,
    pub fx: FxService,
    pub config: Config,
}

pub fn build_router(state: AppState) -> Router {
    Router::new()
        // Embedded Web UI + PWA
        .route("/", get(web::index_html))
        .route("/assets/styles.css", get(web::styles_css))
        .route("/assets/app.js", get(web::app_js))
        .route("/assets/icon.svg", get(web::icon_svg))
        .route("/manifest.webmanifest", get(web::manifest))
        .route("/sw.js", get(web::service_worker))
        // Public routes
        .route("/health", get(health::health_check))
        .route("/api/v1/auth/register", post(auth::register))
        .route("/api/v1/auth/login", post(auth::login))
        // Authenticated routes (require Authorization: Bearer <jwt>)
        .route("/api/v1/auth/me", get(auth::me))
        .route("/api/v1/auth/change-password", post(auth::change_password))
        .route("/api/v1/ingest/media", post(ingest::ingest_media))
        .route(
            "/api/v1/transactions/confirm",
            post(transactions::confirm_transactions),
        )
        .route(
            "/api/v1/spending/monthly",
            get(transactions::monthly_spending),
        )
        .route(
            "/api/v1/spending/zero-balance",
            post(transactions::zero_balance),
        )
        .route(
            "/api/v1/spending/trend",
            get(transactions::spending_trend),
        )
        .route(
            "/api/v1/transactions",
            get(transactions::list_transactions).post(transactions::create_transaction),
        )
        .route(
            "/api/v1/transactions/pending",
            get(transactions::list_pending_transactions),
        )
        .route(
            "/api/v1/transactions/draft",
            post(extras::create_notification_draft),
        )
        .route(
            "/api/v1/transactions/{id}/split",
            post(extras::split_transaction),
        )
        .route(
            "/api/v1/transactions/{id}",
            axum::routing::patch(transactions::update_transaction)
                .delete(transactions::delete_transaction),
        )
        .route(
            "/api/v1/recurring",
            get(transactions::list_recurring),
        )
        .route(
            "/api/v1/budgets",
            get(transactions::list_budgets).put(transactions::upsert_budget),
        )
        .route(
            "/api/v1/categories",
            get(transactions::list_categories).post(transactions::create_category),
        )
        .route(
            "/api/v1/insights/monthly",
            get(insights::monthly_insights),
        )
        .route(
            "/api/v1/goals",
            get(insights::list_goals).post(insights::create_goal),
        )
        .route(
            "/api/v1/goals/{id}",
            axum::routing::patch(insights::update_goal).delete(insights::delete_goal),
        )
        .route(
            "/api/v1/ious",
            get(insights::list_ious).post(insights::create_iou),
        )
        .route(
            "/api/v1/ious/{id}/settle",
            axum::routing::patch(insights::settle_iou),
        )
        .route(
            "/api/v1/ious/{id}",
            axum::routing::delete(insights::delete_iou),
        )
        // Wallets & Internal Transfers
        .route(
            "/api/v1/wallets",
            get(extras::list_wallets).post(extras::create_wallet),
        )
        .route(
            "/api/v1/wallets/{id}",
            axum::routing::delete(extras::delete_wallet),
        )
        .route(
            "/api/v1/wallets/transfer",
            post(extras::transfer_wallet),
        )
        // Quick-Add Templates
        .route(
            "/api/v1/templates",
            get(extras::list_templates).post(extras::create_template),
        )
        .route(
            "/api/v1/templates/{id}",
            axum::routing::delete(extras::delete_template),
        )
        .route(
            "/api/v1/templates/{id}/use",
            post(extras::use_template),
        )
        // Merchant Auto-Categorization Rules
        .route(
            "/api/v1/rules",
            get(extras::list_rules).post(extras::upsert_rule),
        )
        .route(
            "/api/v1/rules/{id}",
            axum::routing::delete(extras::delete_rule),
        )
        // Tags & Custom Date-Range / YTD Analytics
        .route(
            "/api/v1/spending/range",
            get(extras::range_spending),
        )
        .route(
            "/api/v1/spending/comparison",
            get(extras::monthly_comparison),
        )
        .route(
            "/api/v1/spending/forecast",
            get(extras::spending_forecast),
        )
        .route(
            "/api/v1/preferences",
            get(extras::get_preferences).put(extras::update_preferences),
        )
        .route(
            "/api/v1/transactions/bulk",
            post(extras::bulk_transactions),
        )
        .route(
            "/api/v1/tags",
            get(extras::list_tags),
        )
        // Full Backup & Restore (JSON)
        .route(
            "/api/v1/backup/export",
            get(extras::export_backup),
        )
        .route(
            "/api/v1/backup/import",
            post(extras::import_backup),
        )
        // Allow up to 25 MB multipart uploads for multi-page PDFs / batched receipts
        .layer(DefaultBodyLimit::max(25 * 1024 * 1024))
        .layer(CorsLayer::permissive())
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}
