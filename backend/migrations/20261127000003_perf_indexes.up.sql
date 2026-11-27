-- Composite indexes to accelerate monthly, yearly, trend, forecast, and merchant queries
CREATE INDEX IF NOT EXISTS idx_transactions_user_status_date
    ON transactions (user_id, status, tx_date DESC);

CREATE INDEX IF NOT EXISTS idx_transactions_user_type_date
    ON transactions (user_id, status, is_transfer, tx_type, tx_date DESC);

CREATE INDEX IF NOT EXISTS idx_transactions_user_vendor_lower
    ON transactions (user_id, LOWER(vendor));
