-- Add new business roles required by the expense and approval system
INSERT INTO roles (name, description) VALUES ('MANAGER', 'Department manager with expense approval authority');
INSERT INTO roles (name, description) VALUES ('EMPLOYEE', 'Employee who can create and manage own expenses');
INSERT INTO roles (name, description) VALUES ('AUDITOR', 'Read-only auditor across users, departments and expenses');
INSERT INTO roles (name, description) VALUES ('FINANCE', 'Finance user who can process approved expenses');

-- Grant ADMIN all new permissions
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (
    SELECT 'USER_CREATE' AS permission UNION ALL
    SELECT 'USER_UPDATE' UNION ALL
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
    SELECT 'AUDIT_LOG_READ' UNION ALL
    SELECT 'TENANT_READ' UNION ALL
    SELECT 'TENANT_CREATE' UNION ALL
    SELECT 'TENANT_UPDATE' UNION ALL
    SELECT 'TENANT_DELETE'
) p
WHERE r.name = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission
  );

-- MANAGER
INSERT INTO role_permissions (role_id, permission) SELECT id, 'USER_READ' FROM roles WHERE name = 'MANAGER';
INSERT INTO role_permissions (role_id, permission) SELECT id, 'DEPARTMENT_READ' FROM roles WHERE name = 'MANAGER';
INSERT INTO role_permissions (role_id, permission) SELECT id, 'EXPENSE_READ' FROM roles WHERE name = 'MANAGER';
INSERT INTO role_permissions (role_id, permission) SELECT id, 'EXPENSE_APPROVE' FROM roles WHERE name = 'MANAGER';
INSERT INTO role_permissions (role_id, permission) SELECT id, 'EXPENSE_REJECT' FROM roles WHERE name = 'MANAGER';
INSERT INTO role_permissions (role_id, permission) SELECT id, 'REPORT_READ' FROM roles WHERE name = 'MANAGER';

-- EMPLOYEE
INSERT INTO role_permissions (role_id, permission) SELECT id, 'EXPENSE_READ' FROM roles WHERE name = 'EMPLOYEE';
INSERT INTO role_permissions (role_id, permission) SELECT id, 'EXPENSE_CREATE' FROM roles WHERE name = 'EMPLOYEE';
INSERT INTO role_permissions (role_id, permission) SELECT id, 'EXPENSE_UPDATE' FROM roles WHERE name = 'EMPLOYEE';
INSERT INTO role_permissions (role_id, permission) SELECT id, 'EXPENSE_DELETE' FROM roles WHERE name = 'EMPLOYEE';

-- AUDITOR
INSERT INTO role_permissions (role_id, permission) SELECT id, 'USER_READ' FROM roles WHERE name = 'AUDITOR';
INSERT INTO role_permissions (role_id, permission) SELECT id, 'DEPARTMENT_READ' FROM roles WHERE name = 'AUDITOR';
INSERT INTO role_permissions (role_id, permission) SELECT id, 'EXPENSE_READ' FROM roles WHERE name = 'AUDITOR';
INSERT INTO role_permissions (role_id, permission) SELECT id, 'REPORT_READ' FROM roles WHERE name = 'AUDITOR';
INSERT INTO role_permissions (role_id, permission) SELECT id, 'AUDIT_LOG_READ' FROM roles WHERE name = 'AUDITOR';

-- FINANCE
INSERT INTO role_permissions (role_id, permission) SELECT id, 'EXPENSE_READ' FROM roles WHERE name = 'FINANCE';
INSERT INTO role_permissions (role_id, permission) SELECT id, 'EXPENSE_PROCESS' FROM roles WHERE name = 'FINANCE';
INSERT INTO role_permissions (role_id, permission) SELECT id, 'REPORT_READ' FROM roles WHERE name = 'FINANCE';

-- Legacy USER role intentionally keeps only its original permissions. New registrations
-- will eventually be tied to EMPLOYEE for expense access without breaking the existing
-- user management tests that rely on USER being a low-privilege target role.
