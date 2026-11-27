use axum::{
    http::{header, StatusCode},
    response::IntoResponse,
};

const INDEX_HTML: &str = include_str!("../../web/index.html");
const STYLES_CSS: &str = include_str!("../../web/styles.css");
const APP_JS: &str = include_str!("../../web/app.js");
const MANIFEST_JSON: &str = include_str!("../../web/manifest.webmanifest");
const SW_JS: &str = include_str!("../../web/sw.js");
const ICON_SVG: &str = r##"<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><rect width="512" height="512" rx="112" fill="#0D5C4B"/><path d="M128 336l96-104 64 60 96-116" fill="none" stroke="#fff" stroke-width="36" stroke-linecap="round" stroke-linejoin="round"/><path d="M312 176h72v72" fill="none" stroke="#fff" stroke-width="36" stroke-linecap="round" stroke-linejoin="round"/></svg>"##;

pub async fn index_html() -> impl IntoResponse {
    (
        StatusCode::OK,
        [
            (header::CONTENT_TYPE, "text/html; charset=utf-8"),
            (header::CACHE_CONTROL, "no-cache"),
        ],
        INDEX_HTML,
    )
}

pub async fn styles_css() -> impl IntoResponse {
    (
        StatusCode::OK,
        [
            (header::CONTENT_TYPE, "text/css; charset=utf-8"),
            (header::CACHE_CONTROL, "public, max-age=3600"),
        ],
        STYLES_CSS,
    )
}

pub async fn app_js() -> impl IntoResponse {
    (
        StatusCode::OK,
        [
            (header::CONTENT_TYPE, "application/javascript; charset=utf-8"),
            (header::CACHE_CONTROL, "public, max-age=3600"),
        ],
        APP_JS,
    )
}

pub async fn manifest() -> impl IntoResponse {
    (
        StatusCode::OK,
        [
            (header::CONTENT_TYPE, "application/manifest+json"),
            (header::CACHE_CONTROL, "public, max-age=3600"),
        ],
        MANIFEST_JSON,
    )
}

pub async fn service_worker() -> impl IntoResponse {
    (
        StatusCode::OK,
        [
            (header::CONTENT_TYPE, "application/javascript; charset=utf-8"),
            (header::CACHE_CONTROL, "no-cache"),
        ],
        SW_JS,
    )
}

pub async fn icon_svg() -> impl IntoResponse {
    (
        StatusCode::OK,
        [
            (header::CONTENT_TYPE, "image/svg+xml"),
            (header::CACHE_CONTROL, "public, max-age=86400"),
        ],
        ICON_SVG,
    )
}
