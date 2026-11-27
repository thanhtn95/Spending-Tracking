# Personal Spending Tracker

An end-to-end personal spending tracker running on **$0/month free tiers** (~100 uploads/month), featuring a **Rust Axum API**, **Embedded Web UI**, **Android Jetpack Compose App**, **Gemini AI Structured Outputs**, and **Neon PostgreSQL**.

## Repository Structure

- `backend/` — Rust Axum REST API, `sqlx` PostgreSQL migrations, Argon2+JWT auth, `lopdf` PDF decryption, Gemini extraction + model routing, statement balance reconciliation, exact/fuzzy deduplication, and embedded Web UI (`backend/web/`).
- `android/` — Kotlin + Jetpack Compose Android client with ML Kit Document Scanner, Photo/Document Picker, Android Share Sheet (`ACTION_SEND`), encrypted JWT storage, and Draft Confirmation dialog.
- `deploy/` — Google Cloud Run deployment script (`cloudrun-deploy.ps1`), Artifact Registry cleanup policy (`artifact-cleanup-policy.json`), and setup guide (`deploy/README.md`).
- `docker-compose.yml` — One-command local PostgreSQL + API + Web UI stack.

## Quick Start (Local Docker)

```powershell
# Optional: set your Gemini API key for live receipt/PDF extraction
$env:GEMINI_API_KEY = "your_gemini_api_key"

docker compose up --build
```

Then open **http://localhost:8080** in your browser to register, sign in, upload receipts/statements, and view monthly spending.

## Deploying to Production ($0/month)

Follow [deploy/README.md](deploy/README.md) to configure Neon PostgreSQL, Google Cloud Secret Manager, Artifact Registry cleanup, and Cloud Run (`us-central1`).
