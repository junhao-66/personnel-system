# 本地数据库备份与恢复演练

适用于当前单企业毕设系统的MySQL 8.0数据库。使用Python 3.10或以上版本及MySQL自带的`mysql`、`mysqldump`，无需安装额外Python依赖。工具入口为`tools/db_maintenance.py`，不通过网页开放数据库运维权限。

## 1. 连接设置

在项目根目录打开PowerShell，按实际安装位置设置：

```powershell
$env:PERSONNEL_MYSQL_BIN = 'D:\mysql\mysql-8.0.41-winx64\bin'
$env:PERSONNEL_DB_HOST = '127.0.0.1'
$env:PERSONNEL_DB_PORT = '3306'
$env:PERSONNEL_DB_USER = 'root'
$env:PERSONNEL_DB_NAME = 'personnel_system'
```

未设置`PERSONNEL_DB_PASSWORD`时，备份和恢复命令会交互式提示输入数据库密码，输入不回显。自动化任务可以注入该环境变量；密码不会写入命令参数、清单或恢复报告。工具不自动读取Spring本地配置，也不备份数据库账号、JWT密钥或Redis配置。

备份账号须能读取全部业务表及元数据；恢复账号还须能创建新数据库及创建、写入表。演练默认使用现有本机账号；正式部署应配置专用运维账号。

## 2. 创建备份

```powershell
& 'D:\python\python.exe' tools/db_maintenance.py backup --backup-dir output/backups/demo-20261029
```

每次使用新目录名，已有目录不会覆盖。成功后目录包含：

- `database.sql`：完整表结构、数据、主外键、自增值及排序规则。
- `manifest.json`：UTC备份时间、源库名、SQL大小与SHA256、逐表行数、结构摘要和数据摘要；不含数据库密码。

采用`--single-transaction`读取InnoDB一致性快照，不停服务、不全局锁表。备份期间不要执行结构迁移或增删表。工具拒绝非InnoDB表、无主键表，以及视图、触发器、存储过程或事件，避免把遗漏对象的备份报告为成功。当前项目13张业务表满足这些条件。

备份包含用户密码散列及可能的个人资料、答卷和报告，应存入有访问限制的本地目录；不要提交代码仓库。建议演示前和修改数据库结构前各备份一次，并把完成的备份复制到另一存储位置。工具不会自动删除历史备份。

## 3. 离线检查完整性

```powershell
& 'D:\python\python.exe' tools/db_maintenance.py verify --backup-dir output/backups/demo-20261029
```

此命令不连接MySQL，不需要密码。SQL文件被截断、内容变化、大小或清单不匹配时返回非零退出码。SHA256用于检查意外损坏，不是签名认证；只恢复自己创建并妥善保管的可信备份，不能把他人提供的SQL视为安全文件。

## 4. 恢复到新的隔离库

```powershell
& 'D:\python\python.exe' tools/db_maintenance.py restore `
  --backup-dir output/backups/demo-20261029 `
  --target-database personnel_restore_demo_20261029 `
  --confirm-target personnel_restore_demo_20261029 `
  --report output/recovery-reports/demo-20261029.json
```

目标必须以`personnel_restore_`开头，不能是源库或已存在的库；`--confirm-target`必须重复准确的目标名称。报告路径也不能覆盖已有文件。这是命令参数的防误写校验，不要求额外网页审批。

导入后重新导出恢复库，核对全部表名、逐表行数、CREATE TABLE结构摘要及每行SQL数据摘要。中文、JSON报告快照、空值和二进制内容均参与校验；行数相同而内容不同也会失败。全部一致才输出`RECOVERY VERIFIED`并生成`result=VERIFIED`的报告。

恢复中断时只留下本次创建的隔离库供排查，不自动删除或重试覆盖。选择新的目标库和报告路径重新演练；失败产生的空报告不表示成功。工具不提供覆盖业务库或自动切换数据源的操作。

## 5. 演示恢复后的业务

需要通过页面验收恢复数据时，先对独立后端测试进程设置`SPRING_DATASOURCE_URL`为恢复库、设置数据库账号密码，并使用不同端口启动；设置`SPRING_SQL_INIT_MODE=never`避免初始化改变恢复库。不要修改当前IDE正在运行的进程配置。JWT密钥独立时重新登录是正常现象。

核对登录、题库/图谱版本、答卷续答、历史报告、人事、考勤、请假和审计后，再决定实际故障恢复时的数据源切换。切换业务库及公网恢复演练不包含在本工具的自动化操作中。

## 6. 回归测试

仅文件完整性与防误写测试：

```powershell
& 'D:\python\python.exe' -m unittest discover -s tools -p test_db_maintenance.py -v
```

执行真实MySQL演练前设置前述连接信息及密码，再设置：

```powershell
$env:PERSONNEL_MAINTENANCE_IT = '1'
& 'D:\python\python.exe' -m unittest discover -s tools -p test_db_maintenance.py -v
Remove-Item Env:PERSONNEL_MAINTENANCE_IT
```

集成测试只读取现有业务库，创建随机UUID命名的临时数据库，在其中恢复和校验，并清理自己创建的测试库；测试账号需要建库/删库权限。不停止业务服务，不修改业务库。
