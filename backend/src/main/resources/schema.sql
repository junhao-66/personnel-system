CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键编号',
    user_name VARCHAR(32) NOT NULL UNIQUE COMMENT '登录用户名，唯一',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt密码散列；不保存明文密码',
    real_name VARCHAR(50) NOT NULL COMMENT '用户姓名',
    phone VARCHAR(20) COMMENT '联系方式，可为空',
    role_code VARCHAR(32) NOT NULL DEFAULT 'EMPLOYEE' COMMENT '角色：ADMIN管理员、DEPARTMENT_MANAGER部门负责人、EMPLOYEE普通员工',
    audit_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING待审核、APPROVED已通过、REJECTED已拒绝',
    audit_reason VARCHAR(500) COMMENT '注册审核意见；拒绝时填写原因',
    reviewed_at DATETIME(6) COMMENT '注册审核处理时间',
    auth_version BIGINT NOT NULL DEFAULT 0 COMMENT '凭证版本；改密、角色或状态变更时递增，使旧JWT失效',
    user_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '账号状态：ENABLED启用、DISABLED停用',
    department_id BIGINT COMMENT '关联部门编号，对应sys_department.id',
    position_id BIGINT COMMENT '关联岗位编号，对应sys_position.id',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，业务时间按Asia/Shanghai处理',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间，业务时间按Asia/Shanghai处理'
) DEFAULT CHARSET=utf8mb4 COMMENT='用户账号、身份权限与注册审核';

CREATE TABLE IF NOT EXISTS sys_role (
    role_code VARCHAR(32) PRIMARY KEY COMMENT '角色编码，主键：ADMIN、DEPARTMENT_MANAGER、EMPLOYEE',
    role_name VARCHAR(50) NOT NULL COMMENT '角色中文名称',
    description VARCHAR(255) COMMENT '角色职责说明',
    is_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用标志：1启用、0停用；历史引用保留',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，业务时间按Asia/Shanghai处理'
) DEFAULT CHARSET=utf8mb4 COMMENT='系统角色字典；首版使用三种角色';

CREATE TABLE IF NOT EXISTS sys_department (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键编号',
    department_name VARCHAR(100) NOT NULL COMMENT '部门名称',
    department_code VARCHAR(50) NOT NULL UNIQUE COMMENT '部门编码，唯一',
    manager_user_id BIGINT COMMENT '部门请假审批负责人，对应sys_user.id',
    self_approver_user_id BIGINT COMMENT '本部门负责人本人请假的审批人，对应另一负责人用户编号；禁止自批',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '显示排序值，较小值优先',
    is_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用标志：1启用、0停用；历史引用保留',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，业务时间按Asia/Shanghai处理',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间，业务时间按Asia/Shanghai处理'
) DEFAULT CHARSET=utf8mb4 COMMENT='部门及请假审批负责人配置';

CREATE TABLE IF NOT EXISTS sys_position (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键编号',
    position_name VARCHAR(100) NOT NULL COMMENT '岗位名称',
    position_code VARCHAR(50) NOT NULL UNIQUE COMMENT '岗位编码，唯一',
    department_id BIGINT COMMENT '岗位所属部门编号；为空时为通用岗位',
    description VARCHAR(255) COMMENT '岗位职责说明',
    is_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用标志：1启用、0停用；历史引用保留',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，业务时间按Asia/Shanghai处理',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间，业务时间按Asia/Shanghai处理',
    CONSTRAINT fk_position_department FOREIGN KEY (department_id) REFERENCES sys_department(id)
) DEFAULT CHARSET=utf8mb4 COMMENT='岗位基础资料；保留历史引用';

CREATE TABLE IF NOT EXISTS employee_profile (
    user_id BIGINT PRIMARY KEY COMMENT '关联用户编号，对应sys_user.id',
    hire_date DATE COMMENT '入职日期，可为空',
    employment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '在职状态：ACTIVE在职、RESIGNED离职',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，业务时间按Asia/Shanghai处理',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间，业务时间按Asia/Shanghai处理',
    CONSTRAINT fk_employee_profile_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) DEFAULT CHARSET=utf8mb4 COMMENT='员工档案补充信息；用户编号与账号一一关联';

CREATE TABLE IF NOT EXISTS assessment_scale (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键编号',
    enterprise_id BIGINT NOT NULL DEFAULT 1 COMMENT '企业编号；首版默认1，预留多企业扩展',
    name VARCHAR(100) NOT NULL COMMENT '量表名称；与版本号共同唯一',
    version VARCHAR(64) NOT NULL COMMENT '内容版本号；发布后变更须创建新版本',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '版本状态：DRAFT待核验、PUBLISHED已发布、ARCHIVED已停用',
    definition_json LONGTEXT NOT NULL COMMENT '量表完整JSON：来源许可、中文版本、评分规则、题目及数据使用说明',
    content_hash CHAR(64) NOT NULL COMMENT '冻结内容的SHA256摘要，用于版本与历史一致性校验',
    create_by BIGINT NOT NULL COMMENT '创建操作人编号，对应sys_user.id',
    published_by BIGINT COMMENT '发布核验人编号，对应sys_user.id',
    published_at DATETIME(6) COMMENT '核验发布完成时间',
    create_time DATETIME(6) NOT NULL COMMENT '创建时间，业务时间按Asia/Shanghai处理',
    update_time DATETIME(6) NOT NULL COMMENT '最后更新时间，业务时间按Asia/Shanghai处理',
    UNIQUE KEY uk_scale_name_version (name, version),
    KEY idx_scale_status (status, id)
) DEFAULT CHARSET=utf8mb4 COMMENT='量表题库版本；来源、许可、题目与评分规则统一冻结';

CREATE TABLE IF NOT EXISTS assessment_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键编号',
    enterprise_id BIGINT NOT NULL DEFAULT 1 COMMENT '企业编号；首版默认1，预留多企业扩展',
    user_id BIGINT NOT NULL COMMENT '关联用户编号，对应sys_user.id',
    scale_id BIGINT NOT NULL COMMENT '量表版本编号，对应assessment_scale.id',
    scale_snapshot LONGTEXT NOT NULL COMMENT '开始测评时冻结的量表JSON，不随题库后续修改变化',
    content_hash CHAR(64) NOT NULL COMMENT '冻结内容的SHA256摘要，用于版本与历史一致性校验',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '答卷状态：DRAFT作答中、COMPLETED已提交',
    revision INT NOT NULL DEFAULT 0 COMMENT '草稿乐观锁版本，防止多窗口覆盖答案',
    answers_json LONGTEXT NOT NULL COMMENT '答案JSON，题目标识映射到1至5分；属于个人测评数据',
    result_json LONGTEXT COMMENT '提交后冻结的四维得分、类型、报告、图谱依据和模板快照JSON',
    consent_at DATETIME(6) NOT NULL COMMENT '用户同意本次测评及数据使用说明的时间',
    submitted_at DATETIME(6) COMMENT '本次测评最终提交时间；草稿为空',
    create_time DATETIME(6) NOT NULL COMMENT '创建时间，业务时间按Asia/Shanghai处理',
    update_time DATETIME(6) NOT NULL COMMENT '最后更新时间，业务时间按Asia/Shanghai处理',
    active_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'DRAFT' THEN user_id ELSE NULL END) STORED COMMENT '生成列：仅草稿取用户编号，配合唯一索引保证每人最多一份草稿',
    UNIQUE KEY uk_assessment_active (active_user_id),
    KEY idx_assessment_owner (user_id, status, id),
    CONSTRAINT fk_assessment_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_assessment_scale FOREIGN KEY (scale_id) REFERENCES assessment_scale(id)
) DEFAULT CHARSET=utf8mb4 COMMENT='测评草稿及提交后的冻结答卷、结果和报告';

CREATE TABLE IF NOT EXISTS knowledge_graph_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键编号',
    version VARCHAR(64) NOT NULL UNIQUE COMMENT '内容版本号；发布后变更须创建新版本',
    title VARCHAR(100) NOT NULL COMMENT '图谱中文标题',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '版本状态：DRAFT待核验、PUBLISHED已发布、ARCHIVED已停用',
    definition_json LONGTEXT NOT NULL COMMENT '图谱JSON：节点、关系、来源与多维条件规则；报告保存命中路径快照',
    content_hash CHAR(64) NOT NULL COMMENT '冻结内容的SHA256摘要，用于版本与历史一致性校验',
    create_by BIGINT NOT NULL COMMENT '创建操作人编号，对应sys_user.id',
    create_time DATETIME(6) NOT NULL COMMENT '创建时间，业务时间按Asia/Shanghai处理',
    KEY idx_graph_published (status, id)
) DEFAULT CHARSET=utf8mb4 COMMENT='人格知识关系图版本；关系解释不代表确定因果';

CREATE TABLE IF NOT EXISTS leave_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键编号',
    enterprise_id BIGINT NOT NULL DEFAULT 1 COMMENT '企业编号；首版默认1，预留多企业扩展',
    user_id BIGINT NOT NULL COMMENT '关联用户编号，对应sys_user.id',
    department_id BIGINT NOT NULL COMMENT '申请时所属部门编号，对应sys_department.id',
    leave_type VARCHAR(20) NOT NULL COMMENT '请假类型，取服务端配置的类型字典',
    start_time DATETIME NOT NULL COMMENT '请假开始时间，Asia/Shanghai，精确到分钟',
    end_time DATETIME NOT NULL COMMENT '请假结束时间，必须晚于开始时间',
    reason VARCHAR(500) NOT NULL COMMENT '请假原因，最多500字；属于个人业务信息',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '审批状态：PENDING待审批、APPROVED已批准、REJECTED已拒绝',
    reviewer_id BIGINT COMMENT '实际审批人编号，对应sys_user.id',
    review_comment VARCHAR(500) COMMENT '审批意见；拒绝时必须填写',
    reviewed_at DATETIME(6) COMMENT '审批处理时间',
    create_time DATETIME(6) NOT NULL COMMENT '创建时间，业务时间按Asia/Shanghai处理',
    update_time DATETIME(6) NOT NULL COMMENT '最后更新时间，业务时间按Asia/Shanghai处理',
    KEY idx_leave_overlap (user_id, status, start_time, end_time),
    CONSTRAINT fk_leave_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_leave_department FOREIGN KEY (department_id) REFERENCES sys_department(id),
    CONSTRAINT fk_leave_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user(id)
) DEFAULT CHARSET=utf8mb4 COMMENT='请假申请与审批记录；不含销假或多级审批';

CREATE TABLE IF NOT EXISTS attendance_rule (
    id BIGINT PRIMARY KEY COMMENT '主键编号',
    enterprise_id BIGINT NOT NULL DEFAULT 1 COMMENT '企业编号；首版默认1，预留多企业扩展',
    work_start_time TIME NOT NULL COMMENT '规则上班时间，Asia/Shanghai',
    work_end_time TIME NOT NULL COMMENT '规则下班时间，Asia/Shanghai',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间，业务时间按Asia/Shanghai处理'
) DEFAULT CHARSET=utf8mb4 COMMENT='固定上下班考勤规则；首版单例编号1';

CREATE TABLE IF NOT EXISTS attendance_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键编号',
    enterprise_id BIGINT NOT NULL DEFAULT 1 COMMENT '企业编号；首版默认1，预留多企业扩展',
    user_id BIGINT NOT NULL COMMENT '关联用户编号，对应sys_user.id',
    attendance_date DATE NOT NULL COMMENT '考勤日期，按服务器Asia/Shanghai日期；同用户同日唯一',
    clock_in_time DATETIME(6) NOT NULL COMMENT '上班原始打卡时间，由服务器记录',
    clock_out_time DATETIME(6) COMMENT '下班原始打卡时间，由服务器记录；未打卡为空',
    work_start_time TIME NOT NULL COMMENT '打卡时冻结的规则上班时间',
    work_end_time TIME NOT NULL COMMENT '打卡时冻结的规则下班时间',
    is_rest_day TINYINT NOT NULL COMMENT '休息日快照：1休息日、0工作日',
    is_late TINYINT NOT NULL DEFAULT 0 COMMENT '上班打卡时保存的迟到标志：1是、0否；每日参考状态另按已批请假计算',
    is_early TINYINT NOT NULL DEFAULT 0 COMMENT '下班打卡时保存的早退标志：1是、0否；每日参考状态另按已批请假计算',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，业务时间按Asia/Shanghai处理',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间，业务时间按Asia/Shanghai处理',
    UNIQUE KEY uk_attendance_user_date (user_id, attendance_date),
    KEY idx_attendance_date (attendance_date),
    CONSTRAINT fk_attendance_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) DEFAULT CHARSET=utf8mb4 COMMENT='每日原始打卡及当时作息快照；不允许修改原始时间';

INSERT INTO attendance_rule (id, work_start_time, work_end_time)
SELECT 1, '09:00:00', '18:00:00' WHERE NOT EXISTS (SELECT 1 FROM attendance_rule WHERE id = 1);

INSERT INTO sys_role (role_code, role_name, description) VALUES
('ADMIN', '管理员', '系统配置、员工管理、审核及整体数据查看'),
('DEPARTMENT_MANAGER', '部门负责人', '审批本人负责员工的请假申请'),
('EMPLOYEE', '普通员工', '使用个人测评、考勤及请假功能')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), description = VALUES(description);
