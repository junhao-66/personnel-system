CREATE TABLE IF NOT EXISTS operation_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    enterprise_id BIGINT NOT NULL DEFAULT 1,
    actor_user_id BIGINT,
    actor_role VARCHAR(32) NOT NULL,
    operation VARCHAR(40) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    changed_fields VARCHAR(500) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    KEY idx_audit_time (enterprise_id, occurred_at, id),
    KEY idx_audit_actor (enterprise_id, actor_user_id, occurred_at, id),
    KEY idx_audit_target (enterprise_id, target_type, target_id, occurred_at, id),
    KEY idx_audit_operation (enterprise_id, operation, occurred_at, id)
) ENGINE=InnoDB;
