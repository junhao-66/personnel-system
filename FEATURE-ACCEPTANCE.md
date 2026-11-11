# 本地一键功能验收

本任务用于毕业设计的本地功能回归，不执行公网部署，不停止或重启IDE中运行的项目，不调用真实付费AI。入口为 `tools/run_feature_acceptance.py`。

## 检查范围

1. 自动收集后端 `*Test`、`*DatabaseIT` 和 `StaticCacheUnavailableIT`，运行单元与真实MySQL数据库集成测试。覆盖账号权限、组织人事、测评草稿与报告、图谱与模板、考勤请假、审计、岗位探索、团队聚合、PDF、静态缓存及AI规则/契约。
2. 执行 `npm run build`，包含TypeScript检查和Vite生产构建。
3. 执行八组浏览器检查：PDF导出及预览、岗位与团队分析、图谱交互、缓存及AI页面、账号中心与系统配置、测评保存恢复及导航、题库表单、请假与审批。
4. 输出 `output/acceptance/<本次目录>/RESULT.md` 和 `result.json`，分别记录通过、失败、未执行和隔离库清理状态。

浏览器脚本使用夹具API，检查交互、提示、权限异常及页面状态，**不是浏览器连接真实后端的端到端验收**。真实数据库业务由后端集成测试覆盖，两者不能合并描述成全链路测试。AI只覆盖Mock、约束规则、故障降级及本地HTTP契约，不等于真实模型调用成功。

50用户真实HTTP并发验收仍由 `tools/run_concurrency_check.py` 单独负责；本入口刻意不自动运行该长流程，复跑步骤见 `CONCURRENCY-VALIDATION.md`。

## 环境要求

- Python 3.10+、JDK 17+、Maven及现有离线依赖、Node/npm、已安装的前端依赖。
- MySQL 8可连接；测试账号有创建/删除测试库权限。只使用本次新建的随机数据库，不传入现有业务库名。
- 本地Redis可用，并沿用项目的Redis连接配置；测试使用隔离缓存命名空间。缓存不可用的故障测试使用独立的不可用端口。
- 已安装Playwright和Chrome；将 `PLAYWRIGHT_MODULE` 指向可供Node加载的Playwright模块目录。
- 浏览器测试端口5203、5204、5205、5218、5219、5220、5221、5222未占用。占用时直接拒绝验收，不结束占用端口的进程。

脚本默认读取本机D盘工具位置；其他电脑通过 `--maven`、`--java-home`、`--node`、`--mysql-bin` 指定实际路径。数据库连接参数支持 `--host`、`--port`、`--user`。Windows的Maven命令行连接地址不含 `&`，避免被批处理解释为命令分隔符。

## 执行

在项目根目录打开PowerShell。密码只给当前进程，不写入命令行参数或验收报告：

```powershell
$env:PERSONNEL_MYSQL_BIN = 'D:\mysql\mysql-8.0.41-winx64\bin'
$env:PLAYWRIGHT_MODULE = 'C:\实际路径\node_modules\playwright'
$taskDbSecret = Read-Host 'MySQL密码' -AsSecureString
$env:PERSONNEL_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $taskDbSecret).Password
try {
    D:/python/python.exe tools/run_feature_acceptance.py
    if ($LASTEXITCODE -ne 0) { throw '验收未通过，请查看本次RESULT.md' }
} finally {
    Remove-Item Env:PERSONNEL_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Variable taskDbSecret -ErrorAction SilentlyContinue
}
```

可用 `--report-dir output/acceptance/本次新目录` 指定报告位置；只允许该父目录下尚不存在的子目录，防止覆盖旧证据。每次失败后使用新目录重新执行。

## 隔离与失败处理

- 新库名固定前缀 `personnel_acceptance_` 加随机标识；建库成功后记录本次所有权。只删除本次创建的精确库名，不清空、恢复或覆盖业务库。
- 数据源以进程环境和JVM参数指定到隔离库，初始化表与教学种子仅在该库中进行；AI默认强制Mock、关闭外部调用并清空外部Key。
- 子任务失败、超时或中断即停止后续步骤；未执行的步骤不能记为通过。JUnit报告必须由本次运行生成，零用例、跳过、失败或过期报告均不接受。
- 浏览器检查必须返回非空检查项且通过数量一致；不能仅凭退出码0认定成功。
- 正常退出、失败和中断都尝试删除隔离库。清理失败会将最终状态设为FAILED，保留本次库名供人工确认后清理，不能宣称环境已恢复。
- 环境预检查失败时只返回诊断，不新建报告目录或数据库；进入正式验收后才持续更新分项报告。
- 超时或中断仅结束脚本自己启动的子进程树，不按端口或进程名结束其他项目。系统强制关机或直接结束Python进程仍可能绕过清理，需要人工检查本次测试资源。
- 分享的报告不包含原始Java XML、控制台输出、个人答卷或密码。失败时查看本地 `backend/target/surefire-reports` 或单独复跑对应前端脚本；不要直接上传整个target目录。
- PDF集成测试会更新项目 `output/pdf` 下的合成示例文件；不含真实员工报告。

## 验收脚本自身测试

```powershell
D:/python/python.exe -m unittest discover -s tools -p test_feature_acceptance.py -v
```

覆盖空浏览器结果、数量不符、过期/缺失JUnit、跳过/失败/零用例、报告路径防覆盖、独立并发测试排除、失败后的清理与未执行标识，以及清理失败不能假通过。此测试不连接真实数据库。

## 尚需人工验收

2026-10-30本机最终回归：246项Java测试、64项浏览器检查及前端类型检查/生产构建全部通过，隔离测试库删除成功。8项验收脚本自身测试通过，并用真实的专属Python父子进程验证了超时后进程树清理。分项证据见 `output/acceptance/batch16-20261030/RESULT.md` 和同目录 `result.json`；本次早期失败也按失败报告保留，没有覆盖成成功证据。

按 `DEMO-GUIDE.md` 用真实演示账号和实际组织数据走查；真实AI需有效服务端配置与另行授权的调用验收。公网HTTPS、异机恢复、长期/混合负载仍保留到所有功能完成后的部署阶段。
# 第19—20批验收补充

一键验收新增 `scale-editor-smoke.cjs`（5222端口），PDF检查增加在线预览及资源释放覆盖。最终报告位置：`output/acceptance/batch19-20-20261030/RESULT.md`。浏览器用夹具API，后端数据库测试单独验证，不等同于真实账号端到端演示。
