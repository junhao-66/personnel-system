# 员工档案模块自验收记录

验收日期：2026-10-28。项目目录：`D:\毕业\毕设\Personnel System`。

## 本次交付

- 管理员入口：后台导航 → 员工档案，路由 `/admin/employees`。
- 查询已通过审核的账号，包含管理员、部门负责人、普通员工；支持姓名、用户名、联系方式关键词、部门、在职状态筛选与分页。
- 编辑姓名、联系方式、部门、岗位、入职日期、在职/离职状态，支持清空部门、岗位和日期。
- 新分配的部门与岗位必须存在且启用；部门专属岗位必须与部门匹配，通用岗位允许不分配部门。
- 停用前的历史部门/岗位允许原样保留，调整关联时重新校验；前端切换部门清空旧岗位。
- 只允许管理员查询/编辑档案。未登录返回401，员工和部门负责人返回403。
- 保留毛玻璃和蓝青色弥散渐变，支持窄屏内表格横向滚动。
- 入职日期不得晚于当天；在职状态仅记录档案，不自动改变账号登录状态。

## 接口与数据

- `GET /api/admin/employees`：`pageNum`（默认1）、`pageSize`（默认10，最多100）、`keyword`、`departmentId`、`employmentStatus`。
- 分页响应：`Result<PageResult<EmployeeVO>>`，包含 `records/total/pageNum/pageSize`，不返回密码散列。
- `PUT /api/admin/employees/{id}`：完整提交可编辑字段，关联和日期传 `null` 可以清空。
- 复用 `sys_user`、`employee_profile`、`sys_department`、`sys_position`，本次无需改表或手动执行SQL。
- 用户资料与档案更新在同一事务内完成；档案缺失时补建，列表批量组装避免逐员工查库。
- 创建用户也复用部门/岗位校验，阻止直接调用API写入无效关联。
- 修复参数校验异常被 Spring Problem Details 处理器抢先处理的问题，统一使用项目 `Result` 格式。

## 已执行验证

1. `mvn test`：17项单元测试通过（员工业务8项、组织归属9项）。
2. `mvn -Dtest=EmployeeDatabaseIT test`：3项真实MySQL集成测试通过，覆盖数据库分页、筛选、编辑、清空、非法参数和权限；使用 MockMvc 经过实际控制器/拦截器/服务/Mapper，测试数据事务回滚。
3. `mvn package`：成功生成可执行jar，默认单元测试通过。
4. 前端 `npm run build`：Vue/TypeScript检查、Vite生产构建通过；`tsc --noEmit -p tsconfig.node.json` 通过。
5. `frontend/frontend-smoke.cjs`：23项浏览器回归通过；使用模拟API验证失败重试、分页、编辑和窄屏，不写数据库。
6. `frontend/employee-live-smoke.cjs`：真实jar、真实MySQL和浏览器联调通过，验证真实管理员登录、档案读取、敏感字段排除、编辑回填及取消；脚本请求不保存或修改员工数据。
7. 查看桌面和390px窄屏截图，检查表格固定列不透明背景，避免内容叠字。
8. 项目尚未初始化Git；对9个已有改动文件与改前副本执行 `git diff --no-index`，并检查新增文件，没有发现与员工档案交付无关的源码修改。

## 复验方式

后端使用JDK17与本机Maven。数据库集成测试需要现有的 `application-local.yml` 和MySQL表。集成测试单独指定运行，默认单元测试不依赖数据库。

```powershell
$env:JAVA_HOME='D:\jdk\jdk17'
$env:Path='D:\jdk\jdk17\bin;'+$env:Path
Set-Location 'D:\毕业\毕设\Personnel System\backend'
& 'D:\maven\apache-maven-3.8.2\bin\mvn.cmd' test
& 'D:\maven\apache-maven-3.8.2\bin\mvn.cmd' '-Dtest=EmployeeDatabaseIT' test
& 'D:\maven\apache-maven-3.8.2\bin\mvn.cmd' package
```

```powershell
Set-Location 'D:\毕业\毕设\Personnel System\frontend'
& 'D:\node\npm.cmd' run build
& 'D:\node\node.exe' node_modules/typescript/bin/tsc --noEmit -p tsconfig.node.json
$env:PLAYWRIGHT_MODULE='C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules\playwright'
& 'D:\node\node.exe' frontend-smoke.cjs
& 'D:\node\node.exe' employee-live-smoke.cjs
```

浏览器脚本依赖本机Chrome。模拟测试使用5199端口；真实联调使用18080、5201端口，运行后关闭自己创建的进程。真实联调默认使用现有admin测试账号，可通过 `SMOKE_ADMIN_USER`、`SMOKE_ADMIN_PASSWORD` 环境变量覆盖。

## 已知限制与后续工作

- 当前为员工档案模块交付，MBTI测评、考勤请假等其他业务仍需后续开发。
- 没有配置lint任务，本次不声称已通过lint。
- 仍有既有Sass旧接口提示和Element Plus主包体积警告，未阻断构建；尚未执行压力测试或公网部署验收。
- 既有JWT签名密钥仍为开发用硬编码，权限拦截仍主要根据令牌角色判断，没有账号停用后的即时令牌失效机制；公网部署前应完成鉴权加固。
- 页面展示的在职/离职状态与账号启停分开；本次没有增加离职自动禁用账号的规则。
