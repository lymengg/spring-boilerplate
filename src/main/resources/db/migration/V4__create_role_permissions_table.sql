CREATE TABLE IF NOT EXISTS role_permissions (
    role_id BIGINT NOT NULL,
    permission VARCHAR(50) NOT NULL,
    PRIMARY KEY (role_id, permission),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- Add USER_MANAGER role
INSERT INTO roles (name, description) VALUES ('USER_MANAGER', 'User manager role with limited user management permissions');

-- USER_MANAGER permissions
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'USER_READ' FROM roles WHERE name = 'USER_MANAGER';

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'USER_WRITE' FROM roles WHERE name = 'USER_MANAGER';

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'USER_ASSIGN_ROLE' FROM roles WHERE name = 'USER_MANAGER';

-- ADMIN gets all permissions
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (
    SELECT 'USER_READ' AS permission UNION ALL
    SELECT 'USER_WRITE' UNION ALL
    SELECT 'USER_DELETE' UNION ALL
    SELECT 'USER_ENABLE' UNION ALL
    SELECT 'USER_ASSIGN_ROLE' UNION ALL
    SELECT 'ROLE_READ' UNION ALL
    SELECT 'ROLE_WRITE' UNION ALL
    SELECT 'ROLE_DELETE' UNION ALL
    SELECT 'ROLE_ASSIGN_PERMISSION'
) p
WHERE r.name = 'ADMIN';
