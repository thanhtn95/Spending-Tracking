-- Revert tx_type column

DROP INDEX IF EXISTS idx_transactions_user_type_status_date;

ALTER TABLE transactions
    DROP COLUMN IF EXISTS tx_type;
