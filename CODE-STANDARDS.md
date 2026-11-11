# 代码规范整理说明

日期：2026-10-30。依据用户提供的SpringBoot Java后端分层规范与Vue3 + Vite前端规范。

## 本轮完成

- 统一 `frontend/src` 中Vue、TypeScript、SCSS以及 `backend/src/main/java` 中Java的排版。前端两空格、Java四空格；Vue模板属性分行，展开单行挤写的方法、模板和样式。自动生成的类型声明不手改。
- 新增根目录 `.editorconfig`，统一UTF-8、换行、缩进与结尾换行约定。
- 固定开发依赖Prettier 3.6.2、prettier-plugin-java 2.7.1，配置纳入项目与锁文件。不是生产运行依赖。
- 新增可执行源码检查：Controller不导入Mapper/DO/数据库API、不放业务事务；Mapper不反向依赖Service/Controller；Service/Mapper不获取HttpServletRequest/Response；不留System.out/err；XML禁用SELECT *。
- 前端通过Vue和TypeScript语法解析检查：有脚本的组件使用script setup与TypeScript、不使用显式any或var、不留console.log、axios只在统一request模块导入、同元素不混用v-if/v-for、循环有key。
- 桌面功能说明源文件在项目根目录 `功能说明与测试清单.md`，便于后续同步更新。

## 开发命令

在frontend目录执行：

```powershell
npm install
npm run format       # 统一前端及后端主源码格式，会修改文件
npm run format:check # 只检查格式
npm run standards:check # 只检查已覆盖的源码规则
npm run type-check
npm run check        # 格式检查 + 源码规则 + TypeScript
npm run build
```

检查脚本在 `tools/check_code_standards.cjs`，使用项目已有Vue/TypeScript解析器，不执行浏览器或连接业务数据库。

## 保持与边界

保留接口地址、DTO/VO对外字段、数据库结构、计分规则、权限、事务和历史快照行为；不重命名已有路由、批量改业务状态机或重新设计UI。测试源码及部署配置不做无关批量修改，不格式化或同步本地密钥配置。

本次工具不等同于Alibaba/Sonar或完整Java语义分析：跨业务Mapper归属、事务所需一致性、N+1、日志脱敏、嵌套深度、所有命名和注释质量等仍需结合代码语义复核。源码检查不证明零安全问题，也不代替后端权限测试。未为符合“只读无事务”的表面规则删除保障一致性的锁/事务。

## 验证

`npm run check` 已通过，源码检查覆盖90个前端文件与213个Java文件。完整回归259项Java测试、148项浏览器检查、前端构建通过；8项验收脚本自身测试通过，临时数据库清理成功。证据见 `output/acceptance/code-standards-20261030/RESULT.md`；浏览器使用夹具API，实际演示账号的人工验收按桌面清单进行。

同步回原项目时逐文件比较整理前基线SHA256，遇到用户另行修改立即拒绝覆盖。未停止或重启IDE服务。
