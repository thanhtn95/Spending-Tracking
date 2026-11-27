# Personal Spending Tracker — Architecture & Build Specification

## 1. Project Overview
A personal spending tracker consisting of an **Android app** and a **Rust REST API** that ingests:
- Paper bills / receipts (photos via document scanner)
- Banking app transfer screenshots (via Android Share Sheet or Photo Picker)
- Bank statements (PDFs and CSVs, including password-protected PDFs)

The system extracts structured transactions, auto-categorizes expenses, reconciles statement balances, deduplicates overlapping entries, and calculates monthly spending summaries—all running on **$0/month free tiers** for ~100 uploads/month.

---

## 2. Locked-In Tech Stack

| Layer | Technology | Role & Notes |
| :--- | :--- | :--- |
| **Android Client** | Kotlin, Jetpack Compose, Retrofit, OkHttp | UI, ML Kit Document Scanner (auto-crop/glare removal), Photo Picker, and Share Sheet (`ACTION_SEND`) for screenshots/PDFs. Downscales photos to ~1600px JPEG before upload. |
| **Backend API** | Rust (Axum, Tokio, `sqlx`, `reqwest`, `serde`) | Unified media ingestion, optional PDF decryption (`lopdf` or `qpdf`), Gemini API routing, math reconciliation, deduplication, and monthly aggregation. |
| **AI Extraction** | Gemini API (Free Tier, Structured Outputs) | **Gemini 3.5 Flash-Lite** (~500 RPD) for receipts & screenshots; **Gemini 3.8 Flash** (~20 RPD) for multi-page PDF bank statements. |
| **Database** | Neon (Free PostgreSQL) | 1 GB storage, 100 CU-hours/mo, scales to zero & auto-wakes. Stores currency as `BIGINT` minor units or `NUMERIC(14,2)` to avoid floating-point drift. |
| **Hosting & Infra** | Google Cloud Run (Always Free) | Deployed in `us-central1` or `us-west1` (Tier 1 free-tier regions). Uses **Secret Manager** for keys and **Artifact Registry** (with image cleanup policy to stay < 0.5 GB). |
| **File Archive** *(Optional)* | Neon Object Storage or GCP Cloud Storage | 5 GB free tier for archiving original receipts and PDFs. |

---

## 3. End-to-End Data Flow

1. **Capture (Android):** User scans a paper bill (ML Kit Document Scanner), shares a banking screenshot (`ACTION_SEND`), or picks a PDF/CSV bank statement.
2. **Upload (Android → Cloud Run):** App sends multipart file(s) (plus optional `pdf_password`) to `POST /api/v1/ingest/media`.
3. **Pre-processing (Rust):**
   - If encrypted PDF: decrypt in-memory using `pdf_password`.
   - Route to **Gemini 3.5 Flash-Lite** for images/screenshots (batching multiple images into 1 request when possible) or **Gemini 3.8 Flash** for PDFs.
4. **Extraction (Gemini):** Returns strict JSON via Structured Outputs containing `transactions[]` (`date`, `vendor`, `description`, `amount`, `type`, `category`, `confidence`) and, for statements, `opening_balance` and `closing_balance`.
5. **Verification & Deduplication (Rust):**
   - **Math Check (Statements):** Verify `opening_balance - total_debits + total_credits == closing_balance`. Flag discrepancies for manual review.
   - **Deduplication:** Generate deterministic hashes for statement rows and flag fuzzy matches (±2 days, exact same amount) against previously scanned bills.
6. **Draft → Confirm:** Return parsed results to Android for quick user confirmation before committing to Neon PostgreSQL.
7. **Analytics:** Android queries `GET /api/v1/spending/monthly` for total spending and category breakdowns.

---

## 4. Database Schema Blueprint (Neon PostgreSQL)

### `categories`
- `id` (`UUID`, PK)
- `name` (`TEXT`, UNIQUE) — e.g., Groceries, Dining, Utilities, Transport, Uncategorized
- `created_at` (`TIMESTAMPTZ`)

### `documents`
- `id` (`UUID`, PK)
- `source_type` (`TEXT`) — `bill_image` | `screenshot` | `bank_statement_pdf` | `bank_statement_csv`
- `file_hash` (`TEXT`, UNIQUE) — SHA-256 of uploaded file to prevent re-importing identical files
- `opening_balance` (`NUMERIC(14,2)`, NULLABLE)
- `closing_balance` (`NUMERIC(14,2)`, NULLABLE)
- `reconciled` (`BOOLEAN`, DEFAULT `false`)
- `uploaded_at` (`TIMESTAMPTZ`)

### `transactions`
- `id` (`UUID`, PK)
- `document_id` (`UUID`, FK → `documents.id`, NULLABLE)
- `tx_date` (`DATE`, NOT NULL)
- `vendor` (`TEXT`, NOT NULL)
- `description` (`TEXT`)
- `category_id` (`UUID`, FK → `categories.id`)
- `amount` (`NUMERIC(14,2)`, NOT NULL) — Positive value representing outflow/spending
- `currency` (`TEXT`, DEFAULT `'VND'` or `'USD'`)
- `source` (`TEXT`, NOT NULL) — `bill` | `screenshot` | `bank_statement`
- `status` (`TEXT`, NOT NULL) — `draft` | `confirmed` | `duplicate_flagged`
- `dedup_hash` (`TEXT`, UNIQUE) — Hash of `(tx_date, normalized_description, amount, source)`
- `created_at` (`TIMESTAMPTZ`)

---

## 5. REST API Contract (Rust Axum)

- `GET /health` — Health check for Cloud Run.
- `POST /api/v1/ingest/media` — Multipart upload (`files[]`, optional `pdf_password`). Calls Gemini, runs math check & duplicate detection, inserts as `draft`, and returns parsed preview.
- `POST /api/v1/transactions/confirm` — Accepts edited/approved draft transaction IDs & fields, updates status to `confirmed`.
- `GET /api/v1/spending/monthly?year=2026&month=11` — Returns `total_spent`, `transaction_count`, and `by_category` breakdown for `confirmed` transactions.
- `GET /api/v1/transactions?year=2026&month=11&status=confirmed` — Lists transactions for the month.

---

## 6. Operational Guardrails
- **GCP Free Tier:** Deploy Cloud Run in `us-central1` or `us-west1`, set a **$1/month billing alert**, and configure Artifact Registry cleanup to keep only the 2–3 most recent images.
- **Secrets:** Store `GEMINI_API_KEY` and `DATABASE_URL` in Google Cloud Secret Manager; never commit them to git.
- **Privacy:** Free-tier Gemini inputs may be used by Google to improve products. Avoid sending unredacted sensitive IDs if privacy is a concern.

---

## 7. Build Roadmap (Execution Order)
1. **Phase 1 — Database:** Initialize `sqlx` migrations for Neon PostgreSQL (`categories`, `documents`, `transactions`).
2. **Phase 2 — Rust Backend:** Scaffold Axum API, implement Gemini Structured Outputs client with model routing (`3.5 Flash-Lite` vs `3.8 Flash`), PDF decryption, balance reconciliation, and deduplication.
3. **Phase 3 — Cloud Run Deployment:** Add multi-stage slim `Dockerfile` (`distroless`/`alpine`), configure Secret Manager, and deploy to Cloud Run.
4. **Phase 4 — Android App:** Build Jetpack Compose UI, Retrofit client, ML Kit Document Scanner integration, `ACTION_SEND` Share Sheet handler, and Draft Confirmation dialog.