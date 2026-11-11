# 数据库中文注释维护说明

日期：2026-10-30。实际数据库：本机MySQL `personnel_system`。

## 已完成

- 实际15张业务表、149个字段均已写入中文COMMENT，并从information_schema读回逐项验证。
- 涵盖字段含义、账号/审核/在职/版本状态取值、关联编号、JSON结构用途、生成列和乐观锁、数据快照、请假与考勤口径等。
- 实际库只修改注释：字段类型、空值、默认值、生成列表达式、主外键和索引、表排序规则不变；修改前后业务数据的逻辑导出SHA256一致。
- 建表脚本补齐中文注释并明确新表DEFAULT CHARSET=utf8mb4；旧版补字段的迁移也带中文注释。
- 文件统一UTF-8，MySQL客户端连接使用utf8mb4；中文数据字典见项目根目录 `数据库中文数据字典.md`。

## 字符集说明

实际库原先14张表为utf8mb3_general_ci，系统配置表为utf8mb4_0900_ai_ci。两者均能保存中文，本次保持现有排序规则，没有把数据转换或字段比较规则变更混入注释维护。新建数据库/表建议utf8mb4。

在Navicat、DataGrip或其他客户端中，连接字符集选择UTF-8/utf8mb4，刷新表或重新打开表设计器，查看“注释/Comment”列。不要选择GBK或ANSI打开SQL/Markdown。

可查询：

```sql
SET NAMES utf8mb4;
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'personnel_system'
ORDER BY TABLE_NAME;

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'personnel_system'
ORDER BY TABLE_NAME, ORDINAL_POSITION;
```

## 维护工具与证据

`tools/database_comments.py` 是注释的统一映射和迁移工具。默认仅生成计划，指定 `--apply` 才修改数据库；现有表/字段超出已审核映射时拒绝执行。保留SHOW CREATE TABLE的精确定义构造ALTER，设置短锁等待并验证非注释结构与行数据摘要。

在项目根目录、当前进程已设置 `PERSONNEL_DB_PASSWORD` 后执行（密码不要放入命令行或提交到代码）：

```powershell
python tools/database_comments.py --database personnel_system --output output/comments-next-preview
# 审阅计划后，使用新的结果目录执行；已经应用过本轮注释的数据库不必再执行。
python tools/database_comments.py --database personnel_system --apply --output output/comments-next-apply
```

重复操作必须使用新输出目录，避免覆盖原始结构证据。DDL不支持普通事务回滚；应用前应确保没有其他DDL，尽量避开业务写入。数据摘要验证期间有并发业务写入也会导致不一致，不应忽略检查结果。

实际应用证据：`output/database-comments-live-20261030/result.json`。同目录 `before-schema.json` 保存原始建表定义，`apply-comments.sql` 是本次UTF-8迁移SQL，不含业务数据行。

独立测试验证通过：已有表的纯注释迁移、新库建表、重复初始化、旧版补字段迁移；专属临时测试库均已清理。测试入口 `--verify-schemas` 使用随项目保留的 `database-comments-baseline` 原始结构。

此次只涉及SQL元数据与初始化脚本，未修改Java/前端业务代码，未重启用户现有服务，也未进行全量业务回归。
