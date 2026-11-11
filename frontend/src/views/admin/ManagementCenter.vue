<script setup lang="ts">
const sections = [
  {
    title: '测评与发展配置',
    description: '维护题库、解释规则与员工发展建议。',
    items: [
      ['量表与题库', '导入、核验和发布测评版本', '/admin/assessment-scales'],
      ['人格知识库', '维护人格内容、关系和来源', '/admin/knowledge-graph'],
      ['发展建议模板', '编辑测评报告中的建议内容', '/admin/growth-templates'],
      ['岗位探索配置', '维护岗位偏好探索规则', '/admin/job-preferences'],
    ],
  },
  {
    title: '分析工具',
    description: '按需进入，辅助理解团队信息。',
    items: [
      ['部门协作分析', '查看部门偏好分布与协作建议', '/admin/team-analysis'],
      ['人才与团队问答', '围绕已有数据提出问题', '/admin/ai-assistant'],
    ],
  },
  {
    title: '系统管理',
    description: '发布通知、检查操作记录和维护运行配置。',
    items: [
      ['公告与测评开关', '发布工作台公告，设置测评开放状态', '/admin/system-settings'],
      ['操作记录', '查询管理员操作及处理结果', '/admin/operation-audits'],
      ['缓存维护', '检查和刷新静态内容缓存', '/admin/static-cache'],
    ],
  },
]
</script>

<template>
  <main class="management-center page-container">
    <header class="page-heading">
      <div>
        <h1>管理配置</h1>
        <p>按任务进入配置页面，人员与考勤请使用顶部导航。</p>
      </div>
    </header>
    <section
      v-for="section in sections"
      :key="section.title"
      class="management-section"
    >
      <header>
        <h2>{{ section.title }}</h2>
        <p>{{ section.description }}</p>
      </header>
      <div class="management-links">
        <RouterLink
          v-for="item in section.items"
          :key="item[2]"
          :to="item[2]!"
        >
          <span>
            <strong>{{ item[0] }}</strong>
            <small>{{ item[1] }}</small>
          </span>
        </RouterLink>
      </div>
    </section>
  </main>
</template>
<style scoped lang="scss">
.management-section {
  display: grid;
  grid-template-columns: minmax(160px, 0.65fr) minmax(0, 2fr);
  gap: 24px;
  padding: 24px;
  margin-bottom: 16px;
  background: var(--glass-panel);
  border: 1px solid var(--glass-line);
  border-radius: 20px;
  box-shadow: var(--glass-shadow);
}
.management-section h2 {
  font-size: 16px;
  line-height: 24px;
  font-weight: 500;
}
.management-section p {
  max-width: 28ch;
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-secondary);
}
.management-links {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 24px;
}
.management-links a {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 48px;
  color: var(--text-primary);
  text-decoration: none;
  border-radius: 12px;
  padding: 4px 12px;
  transition: background 200ms var(--ease-out);
}
.management-links a::after {
  content: '↗';
  font-size: 16px;
  color: var(--text-secondary);
}
.management-links a:hover {
  background: rgb(255 255 255 / 32%);
}
.management-links a span {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.management-links strong {
  font-size: 14px;
  line-height: 20px;
  font-weight: 500;
}
.management-links small {
  font-size: 12px;
  line-height: 16px;
  color: var(--text-secondary);
}
@media (max-width: 760px) {
  .management-section {
    grid-template-columns: minmax(0, 1fr);
    padding: 16px;
    gap: 12px;
  }
  .management-links {
    gap: 8px;
  }
  .management-links a {
    padding-inline: 0;
  }
}
@media (max-width: 480px) {
  .management-links {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
