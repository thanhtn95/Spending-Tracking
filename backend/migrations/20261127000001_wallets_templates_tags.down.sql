DROP INDEX IF EXISTS idx_transactions_tags;
DROP INDEX IF EXISTS idx_transactions_wallet_id;

ALTER TABLE transactions
    DROP COLUMN IF EXISTS tags,
    DROP COLUMN IF EXISTS is_transfer,
    DROP COLUMN IF EXISTS wallet_id;

DROP TABLE IF EXISTS quick_templates;
DROP TABLE IF EXISTS wallets;
