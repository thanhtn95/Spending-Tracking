-- Phase 1: Initial schema for Personal Spending Tracker (Neon PostgreSQL)
-- Uses pgcrypto's gen_random_uuid() (built-in since PostgreSQL 13, supported on Neon).

CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type TEXT NOT NULL CHECK (
        source_type IN (
            'bill_image',
            'screenshot',
            'bank_statement_pdf',
            'bank_statement_csv'
        )
    ),
    file_hash TEXT NOT NULL UNIQUE,
    opening_balance NUMERIC(14, 2),
    closing_balance NUMERIC(14, 2),
    reconciled BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID REFERENCES documents(id) ON DELETE SET NULL,
    tx_date DATE NOT NULL,
    vendor TEXT NOT NULL,
    description TEXT,
    category_id UUID REFERENCES categories(id) ON DELETE RESTRICT,
    amount NUMERIC(14, 2) NOT NULL CHECK (amount >= 0),
    currency TEXT NOT NULL DEFAULT 'VND',
    source TEXT NOT NULL CHECK (
        source IN ('bill', 'screenshot', 'bank_statement')
    ),
    status TEXT NOT NULL DEFAULT 'draft' CHECK (
        status IN ('draft', 'confirmed', 'duplicate_flagged')
    ),
    dedup_hash TEXT UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for monthly analytics, listing, and fuzzy deduplication (±2 days, exact amount)
CREATE INDEX idx_transactions_status_date ON transactions (status, tx_date);
CREATE INDEX idx_transactions_amount_date ON transactions (amount, tx_date);
CREATE INDEX idx_transactions_document_id ON transactions (document_id);
CREATE INDEX idx_transactions_category_id ON transactions (category_id);
