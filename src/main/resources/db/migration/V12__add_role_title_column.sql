ALTER TABLE roles ADD COLUMN title VARCHAR(100) NOT NULL DEFAULT 'Untitled';

UPDATE roles SET title = 'Administrator' WHERE name = 'ADMIN';
UPDATE roles SET title = 'User' WHERE name = 'USER';
UPDATE roles SET title = 'User Manager' WHERE name = 'USER_MANAGER';
UPDATE roles SET title = 'Manager' WHERE name = 'MANAGER';
UPDATE roles SET title = 'Employee' WHERE name = 'EMPLOYEE';
UPDATE roles SET title = 'Auditor' WHERE name = 'AUDITOR';
UPDATE roles SET title = 'Finance' WHERE name = 'FINANCE';
