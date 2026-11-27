-- Revert merchant_rules, is_recurring, and base_amount

ALTER TABLE transactions
    DROP COLUMN IF EXISTS base_amount,
    DROP COLUMN IF EXISTS is_recurring;

DROP TABLE IF EXISTS merchant_rules;
