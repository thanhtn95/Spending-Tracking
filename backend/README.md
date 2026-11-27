# Spending Tracker — Backend

Rust (Axum + `sqlx`) REST API and PostgreSQL schema migrations for Neon PostgreSQL.

## Phase 1: Database Migrations

Migrations live in `migrations/` using `sqlx` reversible migration format (`.up.sql` / `.down.sql`):
- `20261126000001_init_schema` — Creates `categories`, `documents`, and `transactions` tables with constraints and indexes.
- `20261126000002_seed_categories` — Seeds default spending categories (`Groceries`, `Dining`, `Utilities`, `Transport`, `Shopping`, `Entertainment`, `Health`, `Housing`, `Transfer`, `Uncategorized`).

### Running Migrations Against Neon PostgreSQL

1. Copy `.env.example` to `.env` and fill in your Neon `DATABASE_URL` (ensure `?sslmode=require` is included).
2. Install `sqlx-cli` (once Rust/Cargo is installed):
   ```powershell
   cargo install sqlx-cli --no-default-features --features native-tls,postgres
   ```
3. Run migrations:
   ```powershell
   cd backend
   sqlx migrate run
   ```
4. To revert the latest migration:
   ```powershell
   sqlx migrate revert
   ```

> **Note:** In Phase 2, the Rust backend will also embed migrations via `sqlx::migrate!("./migrations")` so they can run automatically on startup if desired.
