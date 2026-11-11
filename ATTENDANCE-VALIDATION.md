# 员工工作台与考勤模块自验收

日期：2026-10-28。项目：`D:\毕业\毕设\Personnel System`。

## 已交付功能

- 普通员工及部门负责人登录后进入 `/workbench`，可查看本人今日考勤和个人历史记录。管理员可由后台导航进入个人工作台。
- 管理员在 `/admin/attendance` 配置固定上下班时间，按日期、姓名/用户名关键词和员工当前部门查询考勤记录。
- 默认作息09:00—18:00，管理员可修改；周末双休。未配置过规则才初始化默认值，后续启动不覆盖已有规则。
- 实际打卡日期与时间均由服务器 `Asia/Shanghai` 时钟生成，忽略客户端提交的用户ID或时间。
- 员工每天只能上班、下班各打卡一次；必须先上班才能下班。上班通过数据库唯一约束防重复，下班通过条件更新保护首次记录。
- 每条记录保存打卡时的作息快照。调整规则不重算历史记录，也不改变已完成上班打卡当天的下班判定标准。
- 支持正常、迟到、早退、迟到且早退、未完成下班打卡、休息日打卡的展示。休息日只记录时间，不判迟到早退。
- 保持蓝青色弥散渐变和毛玻璃风格，390px宽度下可操作打卡、查询记录，表格内容在区域内横向滚动。

## 权限与登录调整

- 新增 `/api/user/me`，身份由有效令牌及数据库实际账号共同确定。
- 所有非公开API均校验登录，只有登录、注册、健康检查保持公开。
- 管理接口仍只允许管理员。部门负责人可使用自己的考勤功能，不能查看他人的考勤列表或管理员数据。
- 每次请求核对数据库中的审核状态、启用状态和角色。账号停用、撤回审核或角色变化后，已有令牌立即被拒绝。
- 移除Java代码中的固定JWT签名密钥，改为读取 `PERSONNEL_JWT_SECRET`。未设置时启动生成随机临时密钥，重启后需重新登录。
- 正式部署应配置私密、足够随机的长密钥（建议至少64字节），通过环境注入，不能提交到代码仓库。
- 本次没有实现退出登录后的令牌撤销列表、限流和HTTPS配置；这些仍属于公网部署前的工作。

## 数据库与接口

新增 `attendance_rule`、`attendance_record` 两张表，已通过真实MySQL初始化。现有员工及连接配置未重建或覆盖。新增表包含 `enterprise_id` 作为将来扩展字段，当前仍为单企业系统，不能宣称已经实现租户隔离。

| 接口 | 用途 | 权限 |
| --- | --- | --- |
| GET `/api/user/me` | 当前实际登录用户 | 已审核且启用的账号 |
| GET `/api/attendance/rule` | 固定作息 | 已登录 |
| GET `/api/attendance/today` | 本人当天状态和服务器时间 | 已登录，仅本人 |
| POST `/api/attendance/clock-in` | 本人上班打卡 | 已登录，仅本人 |
| POST `/api/attendance/clock-out` | 本人下班打卡 | 已登录，仅本人 |
| GET `/api/attendance/records` | 本人历史记录 | 已登录，仅本人 |
| GET `/api/admin/attendance/records` | 员工历史记录 | 管理员 |
| PUT `/api/admin/attendance/rule` | 修改固定作息 | 管理员 |

查询参数为 `pageNum/pageSize/startDate/endDate`，管理员另外支持 `keyword/departmentId`。默认本月，单次查询起止日期差不得超过366天，单页最多100条。分页响应保持 `Result<PageResult<T>>`。部门筛选按员工当前归属，历史作息按记录快照。

## 实际验证结果

- 后端 `mvn test`：45项单元测试通过，包括员工8项、组织归属9项、考勤21项、鉴权7项。
- `mvn -Dtest=AttendanceDatabaseIT,EmployeeDatabaseIT test`：8项真实MySQL集成测试通过（考勤5项、员工3项）。覆盖数据库读写、重复限制、首次下班时间保护、身份/时间参数伪造、作息快照、个人数据隔离、账号状态变化、异常参数。
- 集成测试使用实际Controller、Interceptor、Service和Mapper。新增测试账号、打卡数据和规则修改在测试事务中回滚；新增表和初始规则按交付保留。没有声称进行过并发压力测试。
- `mvn package` 通过，生成可执行jar。
- 前端 `npm run build`、`tsc --noEmit -p tsconfig.node.json` 通过。
- `frontend-smoke.cjs` 原有23项浏览器回归通过。
- `attendance-smoke.cjs` 新增14项浏览器检查通过，包括登录跳转、失败刷新、上班/下班、取消确认、规则保存、权限导航、390px布局和会话失效。此类交互测试使用模拟API，不写数据库。
- `employee-live-smoke.cjs` 扩展后的9项真实链路检查通过：临时启动实际jar和浏览器，完成真实管理员登录、员工档案查询、考勤规则、工作台及个人考勤展示。该脚本不提交真实打卡或规则保存请求。
- 对照需求检查本次文件，已对原有源码和测试脚本执行改前副本差异比较，并检查新增代码；未发现与工作台、考勤及其鉴权依赖无关的修改。项目尚无Git仓库，本次使用 `git diff --no-index`，没有创建提交。
- 已检查工作台桌面和窄屏截图。测试脚本会关闭自己创建的服务与浏览器。

开发过程中发现并修复了前端行键函数缺少类型、测试中Mapper重载匹配不明确的问题，随后重新运行相关检查并通过。

## 复验命令

后端（需本机MySQL和现有本地连接配置）：

```powershell
Set-Location 'D:\毕业\毕设\Personnel System\backend'
$env:JAVA_HOME='D:\jdk\jdk17'
$env:Path='D:\jdk\jdk17\bin;'+$env:Path
& 'D:\maven\apache-maven-3.8.2\bin\mvn.cmd' test
& 'D:\maven\apache-maven-3.8.2\bin\mvn.cmd' '-Dtest=AttendanceDatabaseIT,EmployeeDatabaseIT' test
& 'D:\maven\apache-maven-3.8.2\bin\mvn.cmd' package
```

前端（浏览器测试依赖本机Chrome和Playwright）：

```powershell
Set-Location 'D:\毕业\毕设\Personnel System\frontend'
& 'D:\node\npm.cmd' run build
& 'D:\node\node.exe' node_modules/typescript/bin/tsc --noEmit -p tsconfig.node.json
$env:PLAYWRIGHT_MODULE='C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules\playwright'
& 'D:\node\node.exe' frontend-smoke.cjs
& 'D:\node\node.exe' attendance-smoke.cjs
& 'D:\node\node.exe' employee-live-smoke.cjs
```

模拟考勤测试使用5202端口，原有回归使用5199端口，真实联调使用18080和5201端口；这些测试脚本运行后会关闭临时服务。

交付时另已启动仅本机可访问的预览环境：前端 `http://127.0.0.1:5173`、后端 `http://127.0.0.1:8080`，前端响应200且后端健康检查业务码200。验收时请重新登录；该预览环境未注册为系统服务，退出或重启电脑后需要重新启动。

## 剩余范围与风险

- 未实现请假审批、MBTI测评、知识图谱及人才看板，不能把本次交付理解为整个系统完成。
- 首版不支持跨日班次、轮班、节假日日历、补卡、人工修改原始记录或定位；未打卡日期不自动认定为缺勤。
- 请假尚未接入，因此当前不生成请假抵扣、缺勤或薪资结算结果。
- 人事档案中的离职状态仍与账号启停分开；只有数据库账号状态停用才立即阻止访问。
- 仍有既有Sass旧接口和主包体积警告；项目尚无lint任务，本次未声称通过lint。
- 尚未开展公网部署、多企业隔离、并发压力测试和外部安全验收。
