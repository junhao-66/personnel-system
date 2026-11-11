# 企业员工 MBTI 人格测评与人才分析系统

面向企业人事管理场景的毕业设计项目，包含员工档案、组织岗位、考勤与请假、注册审核、MBTI 人格偏好测评、历史报告、人才统计、岗位偏好探索和人格知识图谱等功能。

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router
- 后端：Spring Boot、MyBatis Plus、MySQL、JWT
- 测试：JUnit、Playwright 验收脚本

## 项目结构

```text
frontend/   Vue 前端应用
backend/    Spring Boot 后端服务
tools/      代码规范与验收工具
third-party/第三方许可说明
```

## 本地运行

1. 根据 `backend/src/main/resources/application.yml` 配置所需环境变量和数据库。
2. 本地敏感配置请放在 `backend/src/main/resources/application-local.yml`，该文件不会提交到仓库。
3. 启动后端服务。
4. 进入 `frontend`，安装依赖并启动前端：

```bash
npm install
npm run dev
```

前端开发服务器默认访问 `http://localhost:5173`，并将 `/api` 请求代理到 `http://localhost:8080`。

## 构建与检查

```bash
cd frontend
npm run build
npm run standards:check
```

## 说明

MBTI 内容用于人格偏好了解和自我反思，不用于能力、绩效、录用或晋升判断。

