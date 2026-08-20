-- MFA management is now handled through user management endpoints (USER_WRITE permission).
-- MFA_MANAGE is no longer needed since PLATFORM_ADMIN and TENANT_ADMIN already have USER_WRITE.
DELETE FROM role_permissions WHERE permission = 'MFA_MANAGE';
