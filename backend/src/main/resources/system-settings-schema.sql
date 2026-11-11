-- Additive singleton configuration. Reinitialization never overwrites administrator edits.
CREATE TABLE IF NOT EXISTS sys_system_settings (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '主键编号',
    assessment_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '新测评开关：1开放、0暂停；已有草稿和历史不受影响',
    assessment_notice VARCHAR(500) NOT NULL DEFAULT '' COMMENT '暂停新测评时显示的说明',
    announcement_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '公告开关：1发布、0关闭；关闭草稿不返回员工',
    announcement_title VARCHAR(100) NOT NULL DEFAULT '' COMMENT '当前公告标题，纯文本',
    announcement_content VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '当前公告正文，纯文本',
    revision INT NOT NULL DEFAULT 0 COMMENT '配置乐观锁版本，防止管理员多窗口互相覆盖',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '配置创建人编号；0表示系统初始化',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '最近修改人编号；0表示系统初始化',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，业务时间按Asia/Shanghai处理',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间，业务时间按Asia/Shanghai处理'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置单例；编号1；初始化不覆盖管理员已保存设置';
INSERT INTO sys_system_settings (id) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM sys_system_settings WHERE id = 1);
