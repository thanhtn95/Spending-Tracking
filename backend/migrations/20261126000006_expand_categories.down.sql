-- Revert added categories (only if unreferenced)

DELETE FROM categories
WHERE name IN (
    'Subscriptions',
    'Education',
    'Travel',
    'Personal Care',
    'Family & Kids',
    'Gifts & Donations',
    'Insurance',
    'Fees & Interest',
    'Investments',
    'Salary',
    'Income',
    'Refund',
    'Debt Repayment'
);
