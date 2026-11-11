CREATE TABLE IF NOT EXISTS job_preference_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    position_id BIGINT NOT NULL,
    version VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    definition_json LONGTEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_job_profile_status_position (status, position_id, id)
) ENGINE=InnoDB;
