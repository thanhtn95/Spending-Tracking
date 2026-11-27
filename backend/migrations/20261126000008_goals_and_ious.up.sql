-- Debt Payoff / Savings Goals & Split / IOU ("Who Owes Me") Tracker

CREATE TABLE goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    goal_type TEXT NOT NULL DEFAULT 'savings' CHECK (goal_type IN ('savings', 'debt_payoff')),
    target_amount NUMERIC(14, 2) NOT NULL CHECK (target_amount > 0),
    current_amount NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (current_amount >= 0),
    currency TEXT NOT NULL DEFAULT 'VND',
    target_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_goals_user_id ON goals (user_id);

CREATE TABLE ious (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    person_name TEXT NOT NULL,
    direction TEXT NOT NULL DEFAULT 'owed_to_me' CHECK (direction IN ('owed_to_me', 'i_owe')),
    amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    currency TEXT NOT NULL DEFAULT 'VND',
    note TEXT,
    settled BOOLEAN NOT NULL DEFAULT FALSE,
    settled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ious_user_settled ON ious (user_id, settled);
