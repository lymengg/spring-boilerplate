-- Grant EXPENSE_READ_ALL to FINANCE so finance users see all expenses in their
-- tenant (filterable by department and status) like TENANT_ADMIN, while remaining
-- restricted to processing (no approve/reject).

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'EXPENSE_READ_ALL'
FROM roles r
WHERE r.name = 'FINANCE'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'EXPENSE_READ_ALL'
  );
