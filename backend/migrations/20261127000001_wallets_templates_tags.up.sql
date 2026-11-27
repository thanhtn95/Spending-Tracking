-- Multiple Wallets / Accounts, Quick-Add Templates, Internal Transfers, and Transaction Tags

CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    wallet_type TEXT NOT NULL DEFAULT 'cash' CHECK (wallet_type IN ('cash', 'bank', 'credit_card', 'ewallet', 'other')),
    initial_balance NUMERIC(14, 2) NOT NULL DEFAULT 0,
    currency TEXT NOT NULL DEFAULT 'VND',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, name)
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);

CREATE TABLE quick_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label TEXT NOT NULL,
    vendor TEXT NOT NULL,
    amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    tx_type TEXT NOT NULL DEFAULT 'debit' CHECK (tx_type IN ('debit', 'credit')),
    currency TEXT NOT NULL DEFAULT 'VND',
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    wallet_id UUID REFERENCES wallets(id) ON DELETE SET NULL,
    tags TEXT[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_quick_templates_user_id ON quick_templates (user_id);

ALTER TABLE transactions
    ADD COLUMN wallet_id UUID REFERENCES wallets(id) ON DELETE SET NULL,
    ADD COLUMN is_transfer BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN tags TEXT[] NOT NULL DEFAULT '{}';

CREATE INDEX idx_transactions_wallet_id ON transactions (wallet_id);
CREATE INDEX idx_transactions_tags ON transactions USING GIN (tags);
