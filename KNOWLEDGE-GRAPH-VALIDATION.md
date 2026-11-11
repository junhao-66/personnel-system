# 图谱阶段验证记录

2026-10-29，开发副本personnel-assessment，对应原项目D:\毕业\毕设\Personnel System。

## 完成的验证

- `mvn -o package`：88项单元测试通过，后端可执行JAR打包成功。
- `mvn -o -Dtest=KnowledgeGraphDatabaseIT,DemoAssessmentDatabaseIT,AssessmentDatabaseIT,LeaveDatabaseIT,AttendanceDatabaseIT,EmployeeDatabaseIT test`：24项真实MySQL集成测试通过。
- `npm run build`：Vue TypeScript检查与Vite生产构建通过。
- `node graph-smoke.cjs`：15项模拟接口浏览器检查通过。
- `node assessment-smoke.cjs`：17项既有测评浏览器检查通过。
- 已查看桌面和390px手机截图，图谱基础布局可用，无整页横向溢出；UI精细设计按用户要求留待后续。

## 图谱与评分重点

KnowledgeGraphDatabaseIT共5个测试方法，内部覆盖：32题固定题号/维度归属/方向/权重、16型极端答案、全中点ESFJ和全4分INTP、TF均衡取F、百分比互补；多维AND规则、缺少一个前提不命中、接近阈值和四舍五入不误触发；没有已发布图谱的基础报告降级；管理员导入/发布/停用、非管理员拦截、重复版本及断路规则拒绝；旧报告缺少graphExplanation字段仍可读取；图谱更换后历史报告的版本、路径与来源快照不变；他人报告权限不变。

图谱浏览器检查包括：登录保护、100节点152边加载、搜索、关系筛选、节点连续展开、作者/许可/版本追溯、390px布局、错误重试与无图谱状态、错误JSON、取消发布、发布/停用、历史报告展示命中前提、展开当次冻结路径及进入当前图谱。

浏览器使用合成报告和模拟接口检验交互；真实计分、报告保存、数据库权限和图谱版本行为由MySQL集成测试验证。测试通过证明软件按约定工作，不证明中文题库或演示关系的心理测量效度。

## 数据及交付范围

数据库保留新增图谱表、32题非商业参考版及演示图谱种子。事务测试回滚临时账号、答卷、版本与状态变更；并发测试仅按本次独立账号/量表ID清理测试数据，不删除真实员工数据。

交付脚本sync-graph-changes.ps1限定40个代码、题库、图谱和说明文件；同步前逐一检查原项目文件仍与graph-baseline一致，同步后逐一校验SHA-256。数据库本地配置、node_modules、构建产物、截图和无关文件不在同步范围。源码版本的启动会自动执行幂等种子SQL；管理员停用的版本不会因重启自动恢复。

项目和测试服务在验证后保持停止。使用方式见KNOWLEDGE-GRAPH.md；内容署名和许可见third-party/oejts/ATTRIBUTION.md。
