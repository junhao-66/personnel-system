-- Additive singleton configuration. Reinitialization never overwrites administrator edits.
CREATE TABLE IF NOT EXISTS sys_system_settings (
    id BIGINT NOT NULL PRIMARY KEY,
    assessment_enabled TINYINT NOT NULL DEFAULT 1,
    assessment_notice VARCHAR(500) NOT NULL DEFAULT '',
    announcement_enabled TINYINT NOT NULL DEFAULT 0,
    announcement_title VARCHAR(100) NOT NULL DEFAULT '',
    announcement_content VARCHAR(2000) NOT NULL DEFAULT '',
    revision INT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL DEFAULT 0,
    update_by BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO sys_system_settings (id) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM sys_system_settings WHERE id = 1);
