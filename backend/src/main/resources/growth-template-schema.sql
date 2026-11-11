CREATE TABLE IF NOT EXISTS growth_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键编号',
    version VARCHAR(64) NOT NULL UNIQUE COMMENT '内容版本号；发布后变更须创建新版本',
    title VARCHAR(100) NOT NULL COMMENT '建议模板标题',
    status VARCHAR(20) NOT NULL COMMENT '版本状态：DRAFT待核验、PUBLISHED已发布、ARCHIVED已停用',
    content_hash CHAR(64) NOT NULL COMMENT '冻结内容的SHA256摘要，用于版本与历史一致性校验',
    suggested_cycle VARCHAR(500) NOT NULL COMMENT '建议尝试周期文案',
    observation_target VARCHAR(1000) NOT NULL COMMENT '建议观察目标文案',
    review_prompt VARCHAR(1000) NOT NULL COMMENT '周期结束后的复盘提示',
    fallback_context VARCHAR(200) NOT NULL COMMENT '未命中图谱规则时的反思情境',
    fallback_action VARCHAR(1000) NOT NULL COMMENT '未命中图谱规则时的通用行动建议',
    create_by BIGINT NOT NULL COMMENT '创建操作人编号，对应sys_user.id',
    create_time DATETIME(6) NOT NULL COMMENT '创建时间，业务时间按Asia/Shanghai处理',
    KEY idx_growth_template_status (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长建议模板版本；新报告冻结模板内容，旧报告不补算';
