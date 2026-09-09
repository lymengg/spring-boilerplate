-- Drop username column from users (email is now the sole identifier)
ALTER TABLE users DROP COLUMN IF EXISTS username;

-- Rename audit_logs.actor_username to actor_email
ALTER TABLE audit_logs RENAME COLUMN actor_username TO actor_email;
