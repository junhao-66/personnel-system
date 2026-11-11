# 前端问题修复与验收

日期：2026-10-28

## 修复范围

- Axios 统一检查业务码，失败时拒绝 Promise 并提示，避免密码错误、重复用户名等 HTTP 200 错误被当作成功。
- 401 清理本地 Token、Pinia 用户状态并跳转登录；403 提示权限不足。
- 损坏的用户 JSON 缓存安全处理，不再造成路由或状态初始化白屏。
- 管理员统一导航、返回首页、退出登录；后台登录保留原目标路由。
- 登录、注册、创建用户、部门与岗位新增表单校验；提交状态防止重复操作。
- 创建用户弹窗关闭清空敏感输入；部门切换清除原岗位，仅显示关联岗位及通用岗位。
- 审核确认框取消正常结束，不产生未处理拒绝。
- 首页健康检查通过 API 请求判断服务状态，不再展示模拟成功。
- 完整 HTML 文档、UTF-8、手机 viewport、中文 Element Plus 文案、按钮配色可读性。

本次仅修改 frontend 下源码、HTML、验收脚本、本文及重新构建的 dist；未修改后端源码、数据库或用户配置，未升级依赖。

## 已执行验证

1. `D:\node\npm.cmd run build`：通过，包含 vue-tsc 应用类型检查及 Vite 生产构建。
2. `D:\node\node.exe node_modules/typescript/bin/tsc --noEmit -p tsconfig.node.json`：通过。
3. `frontend-smoke.cjs`：在本机 Chrome 无头浏览器下通过 16 项检查，桌面视口 1440×900、手机视口 390×844。
4. 手机后台截图已人工查看，导航、标题、创建按钮及表格可见，无整页水平溢出；宽表内容使用表格内部滚动。

浏览器检查包括：未登录跳转、空表单阻止请求、错误密码、正确登录与跳回、用户校验、弹窗密码清除、部门岗位联动、创建失败、取消审核、组织导航、窄屏、401、损坏缓存、注册校验、健康检查请求和运行时异常检查。

浏览器测试使用可控模拟 API 响应，没有修改真实数据库。401 测试预期产生网络 401 控制台信息，不属于页面脚本异常。

## 重跑浏览器验证（PowerShell）

需要安装 Playwright 和本机 Chrome；通过环境变量指定已有 Playwright 包，不要求将测试工具加入生产依赖。

```powershell
$env:PLAYWRIGHT_MODULE='C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules\playwright'
# 可选：指定其他 Chrome 可执行文件
# $env:CHROME_PATH='C:\Program Files\Google\Chrome\Application\chrome.exe'
& 'D:\node\node.exe' frontend-smoke.cjs
```

测试启动独立 Vite 端口 5199，退出时关闭自己创建的服务和浏览器。

## 限制

- 本次未重跑真实数据库的用户创建、审核等写操作；前端交互和 API 契约通过模拟验证，不能代替完整生产联调。
- 当前工程没有 Git 仓库，无法生成基于提交的历史 diff；已按实际补丁及文件范围检查，没有声称完成 Git diff 验证。
- 工程未配置 ESLint，未执行 lint；应用及 Vite 配置类型检查均已执行。
- 原有 Sass legacy API 弃用提示和全量 Element Plus 主包较大的构建警告仍存在，构建通过；本次未升级依赖或隐藏告警。
- 前端角色守卫仅用于页面导航，服务端授权仍为数据安全边界。
