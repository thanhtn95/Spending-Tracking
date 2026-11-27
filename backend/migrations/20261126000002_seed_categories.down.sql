-- Remove seeded default categories (only if unreferenced)
DELETE FROM categories
WHERE name IN (
    'Groceries',
    'Dining',
    'Utilities',
    'Transport',
    'Shopping',
    'Entertainment',
    'Health',
    'Housing',
    'Transfer',
    'Uncategorized'
);
