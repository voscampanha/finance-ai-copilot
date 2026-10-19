SELECT 
    t.id AS transaction_id,
    t.property_id,
    u.id AS user_id,
    u.name AS user_name,
    t.description,
    t.value,
    t.date AS transaction_date,
    t.tags,
    c.name AS category_name,
    c.type AS category_type,
    g.name AS group_name,
    fa.name AS account_name,
    t.updated_at
FROM transactions t
LEFT JOIN categories c ON t.category_id = c.id
LEFT JOIN `groups` g ON c.group_id = g.id
LEFT JOIN finance_accounts fa ON t.finance_account_id = fa.id
LEFT JOIN users u ON fa.user_id = u.id
WHERE (t.id > :lastProcessedId)
   OR (t.updated_at > :lastProcessedAt AND t.id <= :lastProcessedId)
ORDER BY t.updated_at ASC, t.id ASC