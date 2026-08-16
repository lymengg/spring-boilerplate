-- Refactor role model to match updated business terminology.
-- Renames ADMIN -> PLATFORM_ADMIN, MANAGER -> DEPARTMENT_MANAGER,
-- creates TENANT_ADMIN, and adds a title column for display names.

-- Add title column for human-readable role display names
ALTER TABLE roles ADD COLUMN title VARCHAR(100);

-- Rename ADMIN -> PLATFORM_ADMIN
UPDATE roles SET name = 'PLATFORM_ADMIN', title = 'Platform Administrator',
    description = 'Platform-wide administrator with unrestricted cross-tenant access'
    WHERE name = 'ADMIN';

-- Create TENANT_ADMIN role
INSERT INTO roles (name, title, description)
SELECT 'TENANT_ADMIN', 'Tenant Administrator',
    'Tenant-scoped administrator responsible for managing their organization'
    WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'TENANT_ADMIN');

-- Grant TENANT_ADMIN the same permissions as PLATFORM_ADMIN except TENANT_CREATE/DELETE
-- (tenant admins should not create or delete tenants)
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (
    SELECT 'TENANT_READ' AS permission UNION ALL
    SELECT 'TENANT_UPDATE' UNION ALL
    SELECT 'USER_READ' UNION ALL
    SELECT 'USER_WRITE' UNION ALL
    SELECT 'USER_CREATE' UNION ALL
    SELECT 'USER_UPDATE' UNION ALL
    SELECT 'USER_DELETE' UNION ALL
    SELECT 'USER_ENABLE' UNION ALL
    SELECT 'USER_ASSIGN_ROLE' UNION ALL
    SELECT 'ROLE_READ' UNION ALL
    SELECT 'ROLE_WRITE' UNION ALL
    SELECT 'ROLE_DELETE' UNION ALL
    SELECT 'ROLE_ASSIGN_PERMISSION' UNION ALL
    SELECT 'DEPARTMENT_READ' UNION ALL
    SELECT 'DEPARTMENT_CREATE' UNION ALL
    SELECT 'DEPARTMENT_UPDATE' UNION ALL
    SELECT 'DEPARTMENT_DELETE' UNION ALL
    SELECT 'EXPENSE_READ' UNION ALL
    SELECT 'EXPENSE_CREATE' UNION ALL
    SELECT 'EXPENSE_UPDATE' UNION ALL
    SELECT 'EXPENSE_DELETE' UNION ALL
    SELECT 'EXPENSE_APPROVE' UNION ALL
    SELECT 'EXPENSE_REJECT' UNION ALL
    SELECT 'EXPENSE_PROCESS' UNION ALL
    SELECT 'REPORT_READ' UNION ALL
    SELECT 'AUDIT_LOG_READ'
) p
WHERE r.name = 'TENANT_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission
  );

-- Rename MANAGER -> DEPARTMENT_MANAGER
UPDATE roles SET name = 'DEPARTMENT_MANAGER', title = 'Department Manager',
    description = 'Department-scoped manager responsible for approving expenses'
    WHERE name = 'MANAGER';

-- Update existing role titles
UPDATE roles SET title = 'Employee' WHERE name = 'EMPLOYEE';
UPDATE roles SET title = 'Finance Officer' WHERE name = 'FINANCE';
UPDATE roles SET title = 'Auditor' WHERE name = 'AUDITOR';
UPDATE roles SET title = 'User Manager' WHERE name = 'USER_MANAGER';
UPDATE roles SET title = 'Default User' WHERE name = 'USER';

-- Update descriptions for clarity
UPDATE roles SET description = 'Employee who can create and manage own expenses'
    WHERE name = 'EMPLOYEE';
UPDATE roles SET description = 'Finance officer who processes approved expenses for payment'
    WHERE name = 'FINANCE';
UPDATE roles SET description = 'Read-only auditor with compliance access to tenant data'
    WHERE name = 'AUDITOR';
UPDATE roles SET description = 'User manager with limited user administration permissions within a tenant'
    WHERE name = 'USER_MANAGER';
UPDATE roles SET description = 'Legacy default user role'
    WHERE name = 'USER';
