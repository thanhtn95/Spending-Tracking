-- Feature #2: Smart Merchant Memory (auto-categorization rules per user)
CREATE TABLE merchant_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    merchant_pattern TEXT NOT NULL,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, merchant_pattern)
);

CREATE INDEX idx_merchant_rules_user_id ON merchant_rules (user_id);

-- Feature #3 (Recurring bills) & Feature #5 (Multi-currency base_amount normalization)
ALTER TABLE transactions
    ADD COLUMN is_recurring BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN base_amount NUMERIC(14, 2);

-- Backfill existing rows with their original amount
UPDATE transactions
SET base_amount = amount
WHERE base_amount IS NULL;
