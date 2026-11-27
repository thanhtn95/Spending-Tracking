-- Seed default spending categories
INSERT INTO categories (name)
VALUES
    ('Groceries'),
    ('Dining'),
    ('Utilities'),
    ('Transport'),
    ('Shopping'),
    ('Entertainment'),
    ('Health'),
    ('Housing'),
    ('Transfer'),
    ('Uncategorized')
ON CONFLICT (name) DO NOTHING;
