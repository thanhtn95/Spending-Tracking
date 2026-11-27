-- Add monthly category budgets table and allow 'manual' transaction source

ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS transactions_source_check;

ALTER TABLE transactions
    ADD CONSTRAINT transactions_source_check
    CHECK (source IN ('bill', 'screenshot', 'bank_statement', 'manual'));

CREATE TABLE budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    monthly_limit NUMERIC(14, 2) NOT NULL CHECK (monthly_limit > 0),
    currency TEXT NOT NULL DEFAULT 'VND',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, category_id)
);

CREATE INDEX idx_budgets_user_id ON budgets (user_id);
