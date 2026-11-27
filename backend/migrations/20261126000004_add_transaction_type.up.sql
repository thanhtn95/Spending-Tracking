-- Record both debit (outflow/spending) and credit (inflow/repayment) transactions

ALTER TABLE transactions
    ADD COLUMN tx_type TEXT NOT NULL DEFAULT 'debit'
    CHECK (tx_type IN ('debit', 'credit'));

CREATE INDEX idx_transactions_user_type_status_date
    ON transactions (user_id, tx_type, status, tx_date);
