# 请假申请与部门负责人审批交付

日期：2026-10-28。对应正式需求基线V1.1第一阶段的请假闭环。

## 使用入口

- 员工与负责人：个人工作台 → 请假与审批（`/leave`）。可提交申请、分页查询本人记录、按状态筛选、查看处理时间和拒绝原因。
- 负责人：同页“员工请假审批”页签，可批准或填写原因拒绝当前负责员工的待审批申请。
- 管理员：部门与岗位 → 请假审批负责人。仅允许配置已审核且启用的部门负责人，保存需确认。管理员没有默认代批权限。
- 负责人本人如部门审批人就是自己，将明确阻止提交；请先配置另一名有效负责人，不引入未确认的管理员兜底审批。

## 规则与权限

- 身份只取服务端登录用户；客户端传入他人userId不会改变申请人或本人查询范围。
- 待审核、停用账号不能访问。员工、管理员不能调用负责人审批接口。
- 审批范围按申请人的当前部门及该部门当前负责人确定，排除负责人本人。调整部门或负责人后，待审批申请随当前归属流转，旧负责人立即失去处理权限。申请记录保留提交时departmentId，实际处理人独立记录。
- 部门停用、未分配部门、缺失负责人、负责人账号不可用时，不允许提交无人处理的申请。
- 开始时间不得早于服务器时间，结束晚于开始，精确到分钟；统一Asia/Shanghai。
- 开发默认：事假/病假/年假，时长为30分钟整数倍，允许跨日。这些是待业务确认的可配置默认值，不表示已确认企业制度。通过`personnel.leave.types`、`personnel.leave.minimum-minutes`、`personnel.leave.cross-day-allowed`覆盖；页面从服务端读取规则。
- 时长指起止时间的自然分钟差，不计算工作小时、假期余额或薪资。
- 待审批、已批准时间不能重叠；区间采用[start,end)，首尾相接允许。被拒绝后允许再次申请。
- 同一员工提交时在事务内锁定用户行，再检查重叠及插入；审批使用带当前归属及PENDING条件的原子更新，不能重复覆盖结果。
- 原因与审批意见最多500字，拒绝必须有非空原因。日志只记录申请ID、员工ID、处理人和状态，不记录原因正文。

## 接口与数据

全部响应使用`Result<T>`，分页为`PageResult<T>`（records/total/pageNum/pageSize）。

| 方法与路径 | 功能 | 权限 |
| --- | --- | --- |
| GET /api/leave/options | 类型、时间规则及当前审批负责人/不可用原因 | 本人 |
| POST /api/leave | 提交leaveType/startTime/endTime/reason | 本人 |
| GET /api/leave | 本人记录，pageNum/pageSize/status | 本人 |
| GET /api/leave/reviews | 当前负责员工记录，相同分页筛选 | 部门负责人 |
| PUT /api/leave/{id}/review | status=APPROVED或REJECTED、reviewComment | 当前对应负责人 |
| PUT /api/admin/departments/{id}/manager | managerUserId | 管理员 |

新增`leave_request`表，包含企业预留标识、提交部门、申请区间、原因、状态、处理人、意见及时间。schema.sql为新增建表，不重建已有表。通过现有启动初始化机制创建；已使用现有本地MySQL验证。没有覆盖本地数据库连接配置。

后端遵守Controller→Service→Mapper、DO/DTO/VO/Convert分层；跨业务通过UserService和OrganizationService协作。前端新增独立api、types和页面，页面局部状态不进入Pinia。

## 验证

- 后端56项单元测试通过，其中新增11项请假Service测试。
- 真实MySQL集成测试13项通过：请假5项、原有考勤5项、人事3项。覆盖申请审批闭环、越权、重复审批、重叠、拒绝后重提、当前归属变更、账号状态与非法参数。测试业务数据事务回滚，新表保留。
- 后端`mvn -o package`通过。
- 前端`npm run build`通过（含Vue/TypeScript检查）。
- `leave-smoke.cjs`新增13项模拟API浏览器检查通过：空表单、失败保留、成功刷新、查询恢复、390px布局、缺审批人、取消审批、拒绝必填、结果回显、状态筛选、管理员配置、页面异常检查。
- 原有`frontend-smoke.cjs`23项和`attendance-smoke.cjs`14项回归通过。
- 查看390px截图，表格在容器内横向滚动，不导致整页溢出。
- 集成测试经过实际Controller/Interceptor/Service/Mapper及MySQL；浏览器测试使用模拟API，两者不能等同于真实浏览器全链路验收。尚未执行并发压力测试。

复验命令（JDK17、本地Maven、Node）：

```powershell
$env:JAVA_HOME='D:\jdk\jdk17'
$env:Path='D:\jdk\jdk17\bin;'+$env:Path
Set-Location 'D:\毕业\毕设\Personnel System\backend'
& 'D:\maven\apache-maven-3.8.2\bin\mvn.cmd' -o test
& 'D:\maven\apache-maven-3.8.2\bin\mvn.cmd' -o '-Dtest=LeaveDatabaseIT,AttendanceDatabaseIT,EmployeeDatabaseIT' test
Set-Location '..\frontend'
& 'D:\node\npm.cmd' run build
$env:PLAYWRIGHT_MODULE='C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules\playwright'
& 'D:\node\node.exe' leave-smoke.cjs
```

## 剩余阶段

当前尚未完成第一阶段全部内容。后续优先：明确合法量表与评分规则→测评草稿及计分→报告快照、成长建议、历史对比→图谱维护交互→管理员人才看板。

请假与考勤的请假覆盖优先规则尚未确定，本次不修改原始打卡时间或已有迟到早退判定，不声称已完成考勤请假联动。上线前应确定半天/跨日覆盖及状态展示规则并补齐该联动。

第二阶段分析增强、PDF和缓存降级，以及第三阶段LLM均未实现。销假、撤回、多级审批、余额管理不在本次交付中。未执行公网部署、压力测试、Sonar或Alibaba静态扫描，不宣称已通过这些检查。
