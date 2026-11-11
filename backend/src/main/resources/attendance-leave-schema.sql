-- Additive and repeatable migration; preserves existing organization and attendance rows.
SET @attendance_leave_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_department' AND column_name = 'self_approver_user_id'), 'SELECT 1', 'ALTER TABLE sys_department ADD COLUMN self_approver_user_id BIGINT NULL COMMENT ''本部门负责人本人请假的审批人，对应另一负责人用户编号；禁止自批''');
PREPARE attendance_leave_stmt FROM @attendance_leave_ddl;
EXECUTE attendance_leave_stmt;
DEALLOCATE PREPARE attendance_leave_stmt;
