-- Revert users auth schema changes

DROP INDEX IF EXISTS idx_transactions_user_amount_date;
DROP INDEX IF EXISTS idx_transactions_user_status_date;
DROP INDEX IF EXISTS idx_documents_user_id;

ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS transactions_user_dedup_hash_key;

ALTER TABLE transactions
    DROP COLUMN IF EXISTS user_id;

ALTER TABLE transactions
    ADD CONSTRAINT transactions_dedup_hash_key UNIQUE (dedup_hash);

ALTER TABLE documents
    DROP CONSTRAINT IF EXISTS documents_user_file_hash_key;

ALTER TABLE documents
    DROP COLUMN IF EXISTS user_id;

ALTER TABLE documents
    ADD CONSTRAINT documents_file_hash_key UNIQUE (file_hash);

DROP TABLE IF EXISTS users;
