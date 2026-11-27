ALTER TABLE users
    DROP COLUMN IF EXISTS active_event_currency,
    DROP COLUMN IF EXISTS active_event_tag,
    DROP COLUMN IF EXISTS cycle_start_day;

ALTER TABLE budgets
    DROP COLUMN IF EXISTS rollover;
