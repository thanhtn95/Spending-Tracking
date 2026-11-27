-- Budget Rollover, Payday Cycle Start Day, and Travel / Event Mode Preferences

ALTER TABLE budgets
    ADD COLUMN rollover BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN cycle_start_day SMALLINT NOT NULL DEFAULT 1 CHECK (cycle_start_day BETWEEN 1 AND 28),
    ADD COLUMN active_event_tag TEXT,
    ADD COLUMN active_event_currency TEXT;
