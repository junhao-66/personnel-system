CREATE TABLE IF NOT EXISTS job_preference_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键编号',
    position_id BIGINT NOT NULL COMMENT '关联岗位编号，对应sys_position.id',
    version VARCHAR(64) NOT NULL UNIQUE COMMENT '内容版本号；发布后变更须创建新版本',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '版本状态：DRAFT待核验、PUBLISHED已发布、ARCHIVED已停用',
    definition_json LONGTEXT NOT NULL COMMENT '岗位探索配置JSON：来源、多维条件及权重；仅表示演示规则覆盖',
    content_hash VARCHAR(64) NOT NULL COMMENT '冻结内容的SHA256摘要，用于版本与历史一致性校验',
    created_by BIGINT NOT NULL COMMENT '创建操作人编号，对应sys_user.id',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，业务时间按Asia/Shanghai处理',
    INDEX idx_job_profile_status_position (status, position_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='探索性岗位偏好规则版本；不作为能力或录用判断';
