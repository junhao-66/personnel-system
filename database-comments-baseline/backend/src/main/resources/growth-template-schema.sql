CREATE TABLE IF NOT EXISTS growth_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    suggested_cycle VARCHAR(500) NOT NULL,
    observation_target VARCHAR(1000) NOT NULL,
    review_prompt VARCHAR(1000) NOT NULL,
    fallback_context VARCHAR(200) NOT NULL,
    fallback_action VARCHAR(1000) NOT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    KEY idx_growth_template_status (status, id)
) ENGINE=InnoDB;
