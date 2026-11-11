# 第6批：关键业务操作审计

日期：2026-10-29。

新增9类成功操作记录，沿用 operation_audit 表及管理员只读查询页面：

| 操作 | 审计对象 | 记录字段名 |
| --- | --- | --- |
| LEAVE_CREATE | 请假申请 | leaveType,startTime,endTime,reason,status |
| LEAVE_REVIEW | 请假申请 | status,reviewComment,reviewerId,reviewedAt |
| SCALE_IMPORT | 测评量表 | definitionJson,status |
| SCALE_PUBLISH | 测评量表 | status,publishedBy,publishedAt |
| SCALE_ARCHIVE | 测评量表 | status |
| GRAPH_IMPORT | 知识图谱 | definitionJson,status |
| GRAPH_PUBLISH | 知识图谱 | status |
| GRAPH_ARCHIVE | 知识图谱 | status |
| ATTENDANCE_RULE_UPDATE | 考勤规则 | 实际变化的 workStartTime 和/或 workEndTime |

管理员在“操作审计”中选择对象或操作类型，即可结合操作者编号、对象编号、日期及分页查询。部门负责人和普通员工不能查询全局审计。成功请求的操作者来自认证上下文，不接受请求参数伪造；内部无认证上下文的调用仍标记为SYSTEM。

只存字段名，不存请假原因、审批意见、题库/图谱正文、密码或其他字段原值。记录对象编号、操作及操作者，不可据此还原字段历史值。各类操作从对应功能启用后开始记录，不追补历史，不包含失败请求、打卡、测评答案、报告阅读等完整访问日志。

审计调用位于业务ServiceImpl事务内，通过OperationAuditService的MANDATORY事务参与业务提交；审计写入失败则业务一起回滚。发布、停用、审批沿用状态条件更新，失败或重复操作不会追加成功记录。考勤规则在事务中SELECT FOR UPDATE，比较锁定后的值，仅在有变化且更新成功时记录，避免并发读取旧值造成错误字段记录。

验证结果：

- 146项单元/MockMvc测试通过，含规则无变化不写入、业务更新失败不写审计。
- 36项本地MySQL集成测试通过：ExtendedAuditDatabaseIT 12项，以及既有审计、考勤、请假、测评、图谱回归24项。新增测试覆盖9类审计故障回滚、内容生命周期及重复发布、请假真实认证操作者与隐私文本不入日志、规则真实变更字段与无操作去重。合成业务数据由事务回滚，不停止现有应用进程。
- 前端类型检查与生产构建通过。
- audit-smoke.cjs的24项浏览器检查通过：新增9类操作筛选和中文字段、管理员权限、分页/日期、失败重试、空数据、迟到响应保护及390px布局。浏览器接口为模拟数据；持久化验证由MySQL集成测试完成。

无需新增数据库字段，继续使用已有audit-schema.sql。同步源代码后重新编译并重启后端，前端使用开发热更新或重新构建部署。下一批为第一阶段内容维护，完整部署验收仍未全部完成。
