-- Add EXPENSE_READ_ALL permission for tenant-wide expense visibility
-- Replaces the AUDIT_LOG_READ hack used in ExpenseServiceImpl for expense scoping

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'EXPENSE_READ_ALL'
FROM roles r
WHERE r.name IN ('PLATFORM_ADMIN', 'TENANT_ADMIN', 'AUDITOR')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'EXPENSE_READ_ALL'
  );
