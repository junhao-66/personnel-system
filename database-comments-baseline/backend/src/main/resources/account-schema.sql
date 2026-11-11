-- MySQL 8 migration, safe on existing databases and repeat startup. No stored procedure privileges required.
SET @account_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'audit_reason'), 'SELECT 1', 'ALTER TABLE sys_user ADD COLUMN audit_reason VARCHAR(500) NULL');
PREPARE account_stmt FROM @account_ddl;
EXECUTE account_stmt;
DEALLOCATE PREPARE account_stmt;
SET @account_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'reviewed_at'), 'SELECT 1', 'ALTER TABLE sys_user ADD COLUMN reviewed_at DATETIME(6) NULL');
PREPARE account_stmt FROM @account_ddl;
EXECUTE account_stmt;
DEALLOCATE PREPARE account_stmt;
SET @account_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'auth_version'), 'SELECT 1', 'ALTER TABLE sys_user ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0');
PREPARE account_stmt FROM @account_ddl;
EXECUTE account_stmt;
DEALLOCATE PREPARE account_stmt;
