-- Expand seeded categories to cover daily expenses, subscriptions, debt, and credits/income

INSERT INTO categories (name)
VALUES
    ('Subscriptions'),
    ('Education'),
    ('Travel'),
    ('Personal Care'),
    ('Family & Kids'),
    ('Gifts & Donations'),
    ('Insurance'),
    ('Fees & Interest'),
    ('Investments'),
    ('Salary'),
    ('Income'),
    ('Refund'),
    ('Debt Repayment')
ON CONFLICT (name) DO NOTHING;
