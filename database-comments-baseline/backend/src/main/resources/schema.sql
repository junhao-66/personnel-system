CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_name VARCHAR(32) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    phone VARCHAR(20),
    role_code VARCHAR(32) NOT NULL DEFAULT 'EMPLOYEE',
    audit_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    audit_reason VARCHAR(500),
    reviewed_at DATETIME(6),
    auth_version BIGINT NOT NULL DEFAULT 0,
    user_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    department_id BIGINT,
    position_id BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_role (
    role_code VARCHAR(32) PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    is_enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_department (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    department_name VARCHAR(100) NOT NULL,
    department_code VARCHAR(50) NOT NULL UNIQUE,
    manager_user_id BIGINT,
    self_approver_user_id BIGINT,
    sort_order INT NOT NULL DEFAULT 0,
    is_enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_position (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    position_name VARCHAR(100) NOT NULL,
    position_code VARCHAR(50) NOT NULL UNIQUE,
    department_id BIGINT,
    description VARCHAR(255),
    is_enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_position_department FOREIGN KEY (department_id) REFERENCES sys_department(id)
);

CREATE TABLE IF NOT EXISTS employee_profile (
    user_id BIGINT PRIMARY KEY,
    hire_date DATE,
    employment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_employee_profile_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS assessment_scale (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    enterprise_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(100) NOT NULL,
    version VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    definition_json LONGTEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    create_by BIGINT NOT NULL,
    published_by BIGINT,
    published_at DATETIME(6),
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    UNIQUE KEY uk_scale_name_version (name, version),
    KEY idx_scale_status (status, id)
);

CREATE TABLE IF NOT EXISTS assessment_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    enterprise_id BIGINT NOT NULL DEFAULT 1,
    user_id BIGINT NOT NULL,
    scale_id BIGINT NOT NULL,
    scale_snapshot LONGTEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    revision INT NOT NULL DEFAULT 0,
    answers_json LONGTEXT NOT NULL,
    result_json LONGTEXT,
    consent_at DATETIME(6) NOT NULL,
    submitted_at DATETIME(6),
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    active_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'DRAFT' THEN user_id ELSE NULL END) STORED,
    UNIQUE KEY uk_assessment_active (active_user_id),
    KEY idx_assessment_owner (user_id, status, id),
    CONSTRAINT fk_assessment_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_assessment_scale FOREIGN KEY (scale_id) REFERENCES assessment_scale(id)
);

CREATE TABLE IF NOT EXISTS knowledge_graph_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    definition_json LONGTEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    KEY idx_graph_published (status, id)
);

CREATE TABLE IF NOT EXISTS leave_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    enterprise_id BIGINT NOT NULL DEFAULT 1,
    user_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    leave_type VARCHAR(20) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_id BIGINT,
    review_comment VARCHAR(500),
    reviewed_at DATETIME(6),
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    KEY idx_leave_overlap (user_id, status, start_time, end_time),
    CONSTRAINT fk_leave_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_leave_department FOREIGN KEY (department_id) REFERENCES sys_department(id),
    CONSTRAINT fk_leave_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS attendance_rule (
    id BIGINT PRIMARY KEY,
    enterprise_id BIGINT NOT NULL DEFAULT 1,
    work_start_time TIME NOT NULL,
    work_end_time TIME NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS attendance_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    enterprise_id BIGINT NOT NULL DEFAULT 1,
    user_id BIGINT NOT NULL,
    attendance_date DATE NOT NULL,
    clock_in_time DATETIME(6) NOT NULL,
    clock_out_time DATETIME(6),
    work_start_time TIME NOT NULL,
    work_end_time TIME NOT NULL,
    is_rest_day TINYINT NOT NULL,
    is_late TINYINT NOT NULL DEFAULT 0,
    is_early TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_attendance_user_date (user_id, attendance_date),
    KEY idx_attendance_date (attendance_date),
    CONSTRAINT fk_attendance_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

INSERT INTO attendance_rule (id, work_start_time, work_end_time)
SELECT 1, '09:00:00', '18:00:00' WHERE NOT EXISTS (SELECT 1 FROM attendance_rule WHERE id = 1);

INSERT INTO sys_role (role_code, role_name, description) VALUES
('ADMIN', '管理员', '系统配置、员工管理、审核及整体数据查看'),
('DEPARTMENT_MANAGER', '部门负责人', '审批本人负责员工的请假申请'),
('EMPLOYEE', '普通员工', '使用个人测评、考勤及请假功能')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), description = VALUES(description);
