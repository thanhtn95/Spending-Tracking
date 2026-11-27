-- Add users table and scope documents & transactions to user_id

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Scope documents per user
ALTER TABLE documents
    ADD COLUMN user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE documents
    DROP CONSTRAINT IF EXISTS documents_file_hash_key;

ALTER TABLE documents
    ADD CONSTRAINT documents_user_file_hash_key UNIQUE (user_id, file_hash);

-- Scope transactions per user
ALTER TABLE transactions
    ADD COLUMN user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS transactions_dedup_hash_key;

ALTER TABLE transactions
    ADD CONSTRAINT transactions_user_dedup_hash_key UNIQUE (user_id, dedup_hash);

-- User-scoped indexes for monthly analytics and fuzzy deduplication
CREATE INDEX idx_documents_user_id ON documents (user_id);
CREATE INDEX idx_transactions_user_status_date ON transactions (user_id, status, tx_date);
CREATE INDEX idx_transactions_user_amount_date ON transactions (user_id, amount, tx_date);
