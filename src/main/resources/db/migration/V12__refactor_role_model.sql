-- Refactor role model: renames ADMIN -> PLATFORM_ADMIN, MANAGER -> DEPARTMENT_MANAGER,
-- creates TENANT_ADMIN, removes USER role, and converts department managers to many-to-many.

-- Add title column for human-readable role display names
ALTER TABLE roles ADD COLUMN title VARCHAR(100);

-- Rename ADMIN -> PLATFORM_ADMIN
UPDATE roles SET name = 'PLATFORM_ADMIN', title = 'Platform Administrator',
    description = 'Platform-wide administrator with unrestricted cross-tenant access'
    WHERE name = 'ADMIN';

-- Create TENANT_ADMIN role with all permissions except platform-level (TENANT_CREATE/DELETE)
INSERT INTO roles (name, title, description)
SELECT 'TENANT_ADMIN', 'Tenant Administrator',
    'Tenant-scoped administrator with full permissions within their organization'
    WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'TENANT_ADMIN');

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

-- Update descriptions for clarity
UPDATE roles SET description = 'Employee who can create and manage own expenses'
    WHERE name = 'EMPLOYEE';
UPDATE roles SET description = 'Finance officer who processes approved expenses for payment'
    WHERE name = 'FINANCE';
UPDATE roles SET description = 'Read-only auditor with compliance access to tenant data'
    WHERE name = 'AUDITOR';
UPDATE roles SET description = 'User manager with user administration permissions within a tenant'
    WHERE name = 'USER_MANAGER';

-- Remove USER role (delete permissions first due to FK constraint)
DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE name = 'USER');
DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE name = 'USER');
DELETE FROM roles WHERE name = 'USER';

-- Convert department managers from single FK to many-to-many junction table
CREATE TABLE department_managers (
    department_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (department_id, user_id),
    CONSTRAINT fk_dept_mgr_department FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE CASCADE,
    CONSTRAINT fk_dept_mgr_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Migrate existing single manager assignments to the junction table
INSERT INTO department_managers (department_id, user_id)
SELECT id, manager_id FROM departments WHERE manager_id IS NOT NULL;

-- Drop the old single-manager FK column
ALTER TABLE departments DROP CONSTRAINT fk_department_manager;
ALTER TABLE departments DROP COLUMN manager_id;
