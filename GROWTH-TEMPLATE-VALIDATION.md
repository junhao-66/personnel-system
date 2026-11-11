# 第8批：独立建议模板管理

日期：2026-10-29。

入口：管理员后台 → 建议模板。可基于当前生效模板或任意历史版本复制新版本，预览、新建草稿、核查发布、停用。版本内容不可原地覆盖；修改使用新版本号。BUILTIN_V1为保留的内置版本。

模板维护标题、建议周期、观察目标、复盘提示，以及未命中图谱规则时的通用情境和行动。命中规则时情境、行动和来源仍来自当次图谱；模板不改变规则、得分或科学依据。固定说明不能在编辑器中删除：这是项目通用行动模板，不是量表作者结论，也不表示干预效果已经验证。内容仍须管理员核校。

生效顺序为已发布模板中创建编号最新的一份，与现有图谱版本策略一致。发布更旧模板不会覆盖仍在发布状态的更新模板；停用最新模板会退回下一份已发布模板，均不可用时采用内置模板。草稿不会直接参与报告生成。

报告在提交生成时读取生效模板，将文案、templateVersion、templateContentHash保存进报告JSON快照；新报告格式为GRAPH_GROWTH_PLAN_V3。读取历史报告不会重新查询或补算模板内容。旧报告没有模板元数据时不显示虚构版本。模板摘要为规范化模板请求内容的SHA-256；内置模板无数据库摘要。

后端结构：GrowthTemplateController → GrowthTemplateService → GrowthTemplateMapper；DTO/DO/VO映射集中于GrowthTemplateConvert。GrowthPlanService通过模板Service读取一次生效版本，为本次所有行动计划复用，不逐行动查库。数据库使用独立growth_template表；无原地内容修改或删除接口。

接口仅限有效管理员：

- GET /api/admin/growth-templates：版本列表。
- GET /api/admin/growth-templates/effective：当前生效版本或内置模板。
- POST /api/admin/growth-templates：新建草稿。
- PUT /api/admin/growth-templates/{id}/publish 或 /archive：发布或停用。

后端校验角色、账号状态、字段长度/空白、版本号及状态转移；数据库唯一键拦截重复版本。写接口使用内容维护限流（默认每用户10次/分钟）。TEMPLATE_CREATE、TEMPLATE_PUBLISH、TEMPLATE_ARCHIVE与业务同事务审计，日志仅存字段名。管理员审计页已有中文类型及字段映射；审计失败回滚业务。

验证结果：

- 147项单元/MockMvc测试通过，包括自建模板不替换图谱命中行动及旧报告反序列化兼容。
- 16项本地MySQL集成测试通过：GrowthTemplateDatabaseIT 7项、AssessmentDatabaseIT 4项、KnowledgeGraphDatabaseIT 5项。覆盖模板生命周期与回退、重复/保留版本拒绝、权限与校验、真实认证操作者、审计筛选、3类审计故障回滚、新报告采用提交时模板及历史报告不变。合成业务数据事务回滚；仅保留幂等创建的新表结构。
- 前端类型检查和生产构建通过。
- 41项浏览器检查通过：growth-template-smoke.cjs 17项，graph-smoke.cjs 24项。包括后台新建/复制/预览/发布/停用、失败保留表单、访问权限、移动端、冻结模板版本/摘要展示、旧报告不补造元数据，以及原图谱/报告回归。浏览器API为合成响应，持久化与事务验证由MySQL测试完成。

部署：application.yml已将growth-template-schema.sql加入启动建表列表（CREATE TABLE IF NOT EXISTS）。原项目需重新编译并重启后端才能启用新接口，静态前端需重新构建部署；没有停止或重启现有IDE应用。关闭自动建表的环境需先执行该脚本。新功能不要求预置模板数据，空表自动使用内置模板。

来源/规则独立表单及最终部署、备份恢复、并发验收仍待后续完成。
