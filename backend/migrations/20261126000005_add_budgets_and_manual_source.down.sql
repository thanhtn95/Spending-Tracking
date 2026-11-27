-- Revert budgets table and 'manual' source constraint

DROP TABLE IF EXISTS budgets;

ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS transactions_source_check;

ALTER TABLE transactions
    ADD CONSTRAINT transactions_source_check
    CHECK (source IN ('bill', 'screenshot', 'bank_statement'));
