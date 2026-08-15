-- Self-registration is disabled; user creation is restricted to ADMIN and USER_MANAGER.
-- ADMIN already holds USER_CREATE (V7). Grant it to USER_MANAGER so managers can
-- provision users within their own tenant.
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'USER_CREATE'
FROM roles r
WHERE r.name = 'USER_MANAGER'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'USER_CREATE'
  );
