-- Add MFA_MANAGE permission for admin-only MFA management
-- Grant to PLATFORM_ADMIN and TENANT_ADMIN

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'MFA_MANAGE'
FROM roles r
WHERE r.name IN ('PLATFORM_ADMIN', 'TENANT_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'MFA_MANAGE'
  );
