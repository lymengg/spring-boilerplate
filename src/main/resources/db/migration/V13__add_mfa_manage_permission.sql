-- Add MFA_MANAGE permission and grant it to PLATFORM_ADMIN and TENANT_ADMIN roles.

-- Grant MFA_MANAGE to PLATFORM_ADMIN
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'MFA_MANAGE'
FROM roles r
WHERE r.name = 'PLATFORM_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'MFA_MANAGE'
  );

-- Grant MFA_MANAGE to TENANT_ADMIN
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'MFA_MANAGE'
FROM roles r
WHERE r.name = 'TENANT_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'MFA_MANAGE'
  );
