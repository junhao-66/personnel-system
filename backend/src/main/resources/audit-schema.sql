CREATE TABLE IF NOT EXISTS operation_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键编号',
    enterprise_id BIGINT NOT NULL DEFAULT 1 COMMENT '企业编号；首版默认1，预留多企业扩展',
    actor_user_id BIGINT COMMENT '操作人用户编号',
    actor_role VARCHAR(32) NOT NULL COMMENT '操作时角色编码快照',
    operation VARCHAR(40) NOT NULL COMMENT '操作编码，对应AuditOperation常量',
    target_type VARCHAR(20) NOT NULL COMMENT '业务对象类型，用于审计筛选',
    target_id BIGINT NOT NULL COMMENT '业务对象编号',
    changed_fields VARCHAR(500) NOT NULL COMMENT '变更字段名列表；不保存密码、答案及原因等内容原文',
    occurred_at DATETIME(6) NOT NULL COMMENT '业务操作发生时间，Asia/Shanghai',
    KEY idx_audit_time (enterprise_id, occurred_at, id),
    KEY idx_audit_actor (enterprise_id, actor_user_id, occurred_at, id),
    KEY idx_audit_target (enterprise_id, target_type, target_id, occurred_at, id),
    KEY idx_audit_operation (enterprise_id, operation, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成功业务变更的持久化审计；与业务同事务提交';
