-- MySQL 8 migration, safe on existing databases and repeat startup. No stored procedure privileges required.
SET @account_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'audit_reason'), 'SELECT 1', 'ALTER TABLE sys_user ADD COLUMN audit_reason VARCHAR(500) NULL COMMENT ''注册审核意见；拒绝时填写原因''');
PREPARE account_stmt FROM @account_ddl;
EXECUTE account_stmt;
DEALLOCATE PREPARE account_stmt;
SET @account_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'reviewed_at'), 'SELECT 1', 'ALTER TABLE sys_user ADD COLUMN reviewed_at DATETIME(6) NULL COMMENT ''注册审核处理时间''');
PREPARE account_stmt FROM @account_ddl;
EXECUTE account_stmt;
DEALLOCATE PREPARE account_stmt;
SET @account_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'auth_version'), 'SELECT 1', 'ALTER TABLE sys_user ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0 COMMENT ''凭证版本；改密、角色或状态变更时递增，使旧JWT失效''');
PREPARE account_stmt FROM @account_ddl;
EXECUTE account_stmt;
DEALLOCATE PREPARE account_stmt;
