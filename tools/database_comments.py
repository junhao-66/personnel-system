"""UTF-8 Chinese database comments; preserve exact live column definitions for metadata-only ALTERs."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import uuid

from db_maintenance import DatabaseTools, MaintenanceError, identifier, require

ROOT = Path(__file__).resolve().parent.parent
COMMON = {
    'id': '主键编号', 'enterprise_id': '企业编号；首版默认1，预留多企业扩展',
    'user_id': '关联用户编号，对应sys_user.id', 'version': '内容版本号；发布后变更须创建新版本',
    'status': '版本状态：DRAFT待核验、PUBLISHED已发布、ARCHIVED已停用',
    'content_hash': '冻结内容的SHA256摘要，用于版本与历史一致性校验',
    'create_by': '创建操作人编号，对应sys_user.id', 'created_by': '创建操作人编号，对应sys_user.id',
    'create_time': '创建时间，业务时间按Asia/Shanghai处理',
    'created_at': '创建时间，业务时间按Asia/Shanghai处理',
    'update_time': '最后更新时间，业务时间按Asia/Shanghai处理',
    'department_id': '关联部门编号，对应sys_department.id',
    'position_id': '关联岗位编号，对应sys_position.id',
    'is_enabled': '启用标志：1启用、0停用；历史引用保留',
}
# Every column must resolve to a reviewed description; unknown columns stop the migration.
TABLES = {
    'sys_user': ('用户账号、身份权限与注册审核', {
        'user_name': '登录用户名，唯一', 'password_hash': 'BCrypt密码散列；不保存明文密码',
        'real_name': '用户姓名', 'phone': '联系方式，可为空',
        'role_code': '角色：ADMIN管理员、DEPARTMENT_MANAGER部门负责人、EMPLOYEE普通员工',
        'audit_status': '审核状态：PENDING待审核、APPROVED已通过、REJECTED已拒绝',
        'audit_reason': '注册审核意见；拒绝时填写原因', 'reviewed_at': '注册审核处理时间',
        'auth_version': '凭证版本；改密、角色或状态变更时递增，使旧JWT失效',
        'user_status': '账号状态：ENABLED启用、DISABLED停用',
    }),
    'sys_role': ('系统角色字典；首版使用三种角色', {
        'role_code': '角色编码，主键：ADMIN、DEPARTMENT_MANAGER、EMPLOYEE',
        'role_name': '角色中文名称', 'description': '角色职责说明',
    }),
    'sys_department': ('部门及请假审批负责人配置', {
        'department_name': '部门名称', 'department_code': '部门编码，唯一',
        'manager_user_id': '部门请假审批负责人，对应sys_user.id',
        'self_approver_user_id': '本部门负责人本人请假的审批人，对应另一负责人用户编号；禁止自批',
        'sort_order': '显示排序值，较小值优先',
    }),
    'sys_position': ('岗位基础资料；保留历史引用', {
        'position_name': '岗位名称', 'position_code': '岗位编码，唯一',
        'department_id': '岗位所属部门编号；为空时为通用岗位', 'description': '岗位职责说明',
    }),
    'employee_profile': ('员工档案补充信息；用户编号与账号一一关联', {
        'hire_date': '入职日期，可为空', 'employment_status': '在职状态：ACTIVE在职、RESIGNED离职',
    }),
    'assessment_scale': ('量表题库版本；来源、许可、题目与评分规则统一冻结', {
        'name': '量表名称；与版本号共同唯一',
        'definition_json': '量表完整JSON：来源许可、中文版本、评分规则、题目及数据使用说明',
        'published_by': '发布核验人编号，对应sys_user.id', 'published_at': '核验发布完成时间',
    }),
    'assessment_session': ('测评草稿及提交后的冻结答卷、结果和报告', {
        'scale_id': '量表版本编号，对应assessment_scale.id',
        'scale_snapshot': '开始测评时冻结的量表JSON，不随题库后续修改变化',
        'status': '答卷状态：DRAFT作答中、COMPLETED已提交',
        'revision': '草稿乐观锁版本，防止多窗口覆盖答案',
        'answers_json': '答案JSON，题目标识映射到1至5分；属于个人测评数据',
        'result_json': '提交后冻结的四维得分、类型、报告、图谱依据和模板快照JSON',
        'consent_at': '用户同意本次测评及数据使用说明的时间',
        'submitted_at': '本次测评最终提交时间；草稿为空',
        'active_user_id': '生成列：仅草稿取用户编号，配合唯一索引保证每人最多一份草稿',
    }),
    'knowledge_graph_version': ('人格知识关系图版本；关系解释不代表确定因果', {
        'title': '图谱中文标题',
        'definition_json': '图谱JSON：节点、关系、来源与多维条件规则；报告保存命中路径快照',
    }),
    'leave_request': ('请假申请与审批记录；不含销假或多级审批', {
        'department_id': '申请时所属部门编号，对应sys_department.id',
        'leave_type': '请假类型，取服务端配置的类型字典',
        'start_time': '请假开始时间，Asia/Shanghai，精确到分钟',
        'end_time': '请假结束时间，必须晚于开始时间', 'reason': '请假原因，最多500字；属于个人业务信息',
        'status': '审批状态：PENDING待审批、APPROVED已批准、REJECTED已拒绝',
        'reviewer_id': '实际审批人编号，对应sys_user.id',
        'review_comment': '审批意见；拒绝时必须填写', 'reviewed_at': '审批处理时间',
    }),
    'attendance_rule': ('固定上下班考勤规则；首版单例编号1', {
        'work_start_time': '规则上班时间，Asia/Shanghai', 'work_end_time': '规则下班时间，Asia/Shanghai',
    }),
    'attendance_record': ('每日原始打卡及当时作息快照；不允许修改原始时间', {
        'attendance_date': '考勤日期，按服务器Asia/Shanghai日期；同用户同日唯一',
        'clock_in_time': '上班原始打卡时间，由服务器记录',
        'clock_out_time': '下班原始打卡时间，由服务器记录；未打卡为空',
        'work_start_time': '打卡时冻结的规则上班时间', 'work_end_time': '打卡时冻结的规则下班时间',
        'is_rest_day': '休息日快照：1休息日、0工作日',
        'is_late': '上班打卡时保存的迟到标志：1是、0否；每日参考状态另按已批请假计算',
        'is_early': '下班打卡时保存的早退标志：1是、0否；每日参考状态另按已批请假计算',
    }),
    'operation_audit': ('成功业务变更的持久化审计；与业务同事务提交', {
        'actor_user_id': '操作人用户编号', 'actor_role': '操作时角色编码快照',
        'operation': '操作编码，对应AuditOperation常量',
        'target_type': '业务对象类型，用于审计筛选', 'target_id': '业务对象编号',
        'changed_fields': '变更字段名列表；不保存密码、答案及原因等内容原文',
        'occurred_at': '业务操作发生时间，Asia/Shanghai',
    }),
    'growth_template': ('成长建议模板版本；新报告冻结模板内容，旧报告不补算', {
        'title': '建议模板标题', 'suggested_cycle': '建议尝试周期文案',
        'observation_target': '建议观察目标文案', 'review_prompt': '周期结束后的复盘提示',
        'fallback_context': '未命中图谱规则时的反思情境',
        'fallback_action': '未命中图谱规则时的通用行动建议',
    }),
    'job_preference_profile': ('探索性岗位偏好规则版本；不作为能力或录用判断', {
        'definition_json': '岗位探索配置JSON：来源、多维条件及权重；仅表示演示规则覆盖',
    }),
    'sys_system_settings': ('系统配置单例；编号1；初始化不覆盖管理员已保存设置', {
        'assessment_enabled': '新测评开关：1开放、0暂停；已有草稿和历史不受影响',
        'assessment_notice': '暂停新测评时显示的说明',
        'announcement_enabled': '公告开关：1发布、0关闭；关闭草稿不返回员工',
        'announcement_title': '当前公告标题，纯文本', 'announcement_content': '当前公告正文，纯文本',
        'revision': '配置乐观锁版本，防止管理员多窗口互相覆盖',
        'create_by': '配置创建人编号；0表示系统初始化', 'update_by': '最近修改人编号；0表示系统初始化',
    }),
}
COMMENT = re.compile(r"\s+COMMENT\s*(?:=\s*)?'(?:[^'\\]|\\.|'')*'", re.I)


def quote(value: str) -> str:
    require("'" not in value and '\\' not in value, 'Comment contains unsupported SQL quoting.')
    return "'" + value + "'"


def description(table: str, column: str) -> str:
    value = TABLES[table][1].get(column, COMMON.get(column))
    require(value is not None, f'Unmapped column: {table}.{column}')
    return value


def update_schemas() -> None:
    """Mechanical rewrite of CREATE definitions only, leaving DML and constraints intact."""
    found = set()
    for path in (ROOT / 'backend/src/main/resources').glob('*schema.sql'):
        text = path.read_text(encoding='utf-8')
        def annotate(match: re.Match) -> str:
            table, body, suffix = match.group(1, 2, 3)
            require(table in TABLES, f'Unmapped table {table}')
            found.add(table)
            lines = []
            for line in body.splitlines():
                column = re.match(r'^(\s+)([a-z_]+)\s+(?:BIGINT|INT|TINYINT|VARCHAR|CHAR|LONGTEXT|DATETIME|DATE|TIME)\b', line)
                if column:
                    comma = ',' if line.rstrip().endswith(',') else ''
                    line = COMMENT.sub('', line.rstrip().removesuffix(',')) + ' COMMENT ' + quote(description(table, column.group(2))) + comma
                lines.append(line)
            # Explicit UTF-8 for newly created tables; no conversion of existing business data.
            suffix = COMMENT.sub('', suffix)
            suffix = re.sub(r'\s*(?:DEFAULT\s+)?CHARSET\s*=\s*\w+', '', suffix, flags=re.I)
            return f'CREATE TABLE IF NOT EXISTS {table} (' + '\n'.join(lines) + '\n)'+suffix+' DEFAULT CHARSET=utf8mb4 COMMENT='+quote(TABLES[table][0])+';'
        text = re.sub(r'CREATE TABLE IF NOT EXISTS ([a-z_]+) \((.*?)\n\)([^;\n]*);', annotate, text, flags=re.S)
        path.write_text(text, encoding='utf-8', newline='\n')
    require(found == set(TABLES), 'Not all schema tables annotated.')
    dictionary = ['# 数据库中文数据字典', '', '编码：UTF-8；MySQL连接使用utf8mb4。业务时间：Asia/Shanghai。', '']
    for table, (title, _) in TABLES.items():
        dictionary += [f'## {table}：{title}', '', '| 字段 | 中文含义 |', '| --- | --- |']
        for path in (ROOT / 'backend/src/main/resources').glob('*schema.sql'):
            match = re.search(r'CREATE TABLE IF NOT EXISTS '+table+r' \((.*?)\n\)', path.read_text(encoding='utf-8'), re.S)
            if match:
                for column in re.findall(r'^\s+([a-z_]+)\s+(?:BIGINT|INT|TINYINT|VARCHAR|CHAR|LONGTEXT|DATETIME|DATE|TIME)\b', match[1], re.M):
                    dictionary.append(f'| {column} | {description(table, column)} |')
        dictionary.append('')
    (ROOT / '数据库中文数据字典.md').write_text('\n'.join(dictionary), encoding='utf-8')


def live(args: argparse.Namespace) -> None:
    tools = DatabaseTools(args)
    database = identifier(args.database)
    tables = tools.table_metadata(database)
    require({row[0] for row in tables} == set(TABLES), 'Live tables differ from reviewed application tables; refusing unknown schema.')
    charset = tools.query(f"SELECT TABLE_NAME,TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA='{database}' ORDER BY TABLE_NAME")
    require(all(row[1].startswith(('utf8mb4_', 'utf8mb3_', 'utf8_')) for row in charset), 'Existing table charset is not UTF-8; requires separate data conversion review.')
    output = Path(args.output)
    require(not output.exists(), 'Output directory exists; choose a new one.')
    statements, before = [], {}
    for table in sorted(TABLES):
        # Raw output plus splitlines handles Windows CRLF without moving COMMENT to another clause.
        raw = tools.run('mysql', ['--batch', '--raw', '--skip-column-names', f'--execute=SHOW CREATE TABLE `{database}`.`{table}`'])
        ddl = raw.decode('utf-8').split('\t', 1)[1].rstrip('\r\n')
        columns = tools.query(f"SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{database}' AND TABLE_NAME='{table}' ORDER BY ORDINAL_POSITION")
        definitions = {}
        for line in ddl.splitlines():
            match = re.fullmatch(r'  `([a-z_]+)` (.+)', line)
            if match:
                definitions[match[1]] = match[2].rstrip().removesuffix(',')
        require(len(definitions) == len(columns), 'Unable to preserve every exact column definition.')
        clauses = []
        for (column,) in columns:
            clauses.append(f'MODIFY COLUMN `{column}` '+COMMENT.sub('', definitions[column])+' COMMENT '+quote(description(table, column)))
        clauses.append('COMMENT='+quote(TABLES[table][0]))
        statements.append(f'ALTER TABLE `{database}`.`{table}`\n  '+',\n  '.join(clauses)+';')
        before[table] = ddl
    output.mkdir(parents=True)
    (output / 'before-schema.json').write_text(json.dumps(before, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    sql = 'SET NAMES utf8mb4;\nSET SESSION lock_wait_timeout=10;\n'+ '\n\n'.join(statements)+'\n'
    migration = output / 'apply-comments.sql'
    migration.write_text(sql, encoding='utf-8', newline='\n')
    if not args.apply:
        print(f'Prepared comments for {len(before)} UTF-8 tables; no database changes. SQL: {migration}')
        return
    def row_digest() -> str:
        return hashlib.sha256(tools.run('mysqldump', ['--single-transaction', '--skip-lock-tables', '--no-create-info', '--skip-comments', '--skip-add-locks', '--skip-disable-keys', '--skip-triggers', '--hex-blob', '--order-by-primary', '--set-gtid-purged=OFF', '--no-tablespaces', database])).hexdigest()
    digest = row_digest()
    with migration.open('rb') as stream:
        tools.run('mysql', [], stdin=stream)
    require(row_digest() == digest, 'Row digest changed during operation; check concurrent business writes.')
    count = 0
    for table, ddl in before.items():
        actual = tools.run('mysql', ['--batch', '--raw', '--skip-column-names', f'--execute=SHOW CREATE TABLE `{database}`.`{table}`']).decode('utf-8').split('\t', 1)[1].rstrip('\r\n')
        require(COMMENT.sub('', actual) == COMMENT.sub('', ddl), f'Non-comment schema changed: {table}')
        actual_comments = tools.query(f"SELECT COLUMN_NAME,COLUMN_COMMENT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{database}' AND TABLE_NAME='{table}'")
        for column, comment in actual_comments:
            require(comment == description(table, column), f'Comment mismatch: {table}.{column}; actual={comment!r}; expected={description(table, column)!r}')
        count += len(actual_comments)
        require(tools.query(f"SELECT TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA='{database}' AND TABLE_NAME='{table}'") == [[TABLES[table][0]]], 'Table comment mismatch.')
    result = {'status': 'PASSED', 'database': database, 'tables': len(before), 'columns': count, 'connectionCharset': 'utf8mb4', 'tableCollationsPreserved': dict(charset), 'schemaExceptCommentsUnchanged': True, 'rowDigestUnchanged': True}
    (output / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))


def verify_schemas(args: argparse.Namespace) -> None:
    tools = DatabaseTools(args)
    name = 'personnel_comments_' + uuid.uuid4().hex[:16]
    resources = ROOT / 'backend/src/main/resources'
    paths = ['schema.sql', 'account-schema.sql', 'attendance-leave-schema.sql', 'audit-schema.sql', 'growth-template-schema.sql', 'job-preference-schema.sql', 'system-settings-schema.sql']
    tools.query(f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4')
    try:
        # First run the original definitions, testing actual comment-only ALTERs on an existing schema.
        for file in paths:
            original = ROOT / 'database-comments-baseline/backend/src/main/resources' / file
            with original.open('rb') as stream:
                tools.run('mysql', [name], stdin=stream)
        cloned = argparse.Namespace(**vars(args))
        cloned.database, cloned.apply = name, True
        live(cloned)
        for _ in range(2):
            for file in paths:
                with (resources / file).open('rb') as stream:
                    tools.run('mysql', [name], stdin=stream)
        for table in TABLES:
            columns = tools.query(f"SELECT COLUMN_NAME,COLUMN_COMMENT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{name}' AND TABLE_NAME='{table}'")
            require(all(comment == description(table, column) for column, comment in columns), 'Repeated initialization changed comments.')
        # Independently verify the annotated CREATE statements on a fresh schema.
        for table in reversed(list(TABLES)):
            tools.query(f'SET FOREIGN_KEY_CHECKS=0; DROP TABLE `{name}`.`{table}`;')
        for file in paths:
            with (resources / file).open('rb') as stream:
                tools.run('mysql', [name], stdin=stream)
        tools.query(f'ALTER TABLE `{name}`.sys_user DROP COLUMN audit_reason, DROP COLUMN reviewed_at, DROP COLUMN auth_version')
        tools.query(f'ALTER TABLE `{name}`.sys_department DROP COLUMN self_approver_user_id')
        for file in ('account-schema.sql', 'attendance-leave-schema.sql'):
            with (resources / file).open('rb') as stream:
                tools.run('mysql', [name], stdin=stream)
        for table in TABLES:
            columns = tools.query(f"SELECT COLUMN_NAME,COLUMN_COMMENT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{name}' AND TABLE_NAME='{table}'")
            require(bool(columns) and all(comment == description(table, column) for column, comment in columns), 'Fresh schema comments incomplete.')
        print('Verified comment-only migration, fresh CREATE, legacy ADD COLUMN and repeated initialization.')
    finally:
        tools.query(f'DROP DATABASE `{name}`')
        print('Owned isolated schema test database removed.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--update-schemas', action='store_true')
    parser.add_argument('--verify-schemas', action='store_true')
    parser.add_argument('--database', default='personnel_system')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--user', default='root')
    parser.add_argument('--mysql-bin', default='D:/mysql/mysql-8.0.41-winx64/bin')
    parser.add_argument('--output', default='output/database-comments')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    try:
        if args.update_schemas: update_schemas()
        elif args.verify_schemas: verify_schemas(args)
        else: live(args)
    except MaintenanceError as error:
        parser.exit(1, str(error)+'\n')
