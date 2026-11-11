<script setup lang="ts">
defineProps<{ register?: boolean }>()
</script>

<template>
  <main class="public-auth">
    <!-- 品牌区：深蓝黑 + 青绿 mint 点缀 -->
    <aside class="auth-brand-panel">
      <RouterLink
        to="/"
        class="auth-brand"
      >
        <span
          class="brand-symbol"
          aria-hidden="true"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
          </svg>
        </span>
        人事系统
      </RouterLink>

      <div class="brand-story">
        <h1>让人员有序，<br />让协作清晰。</h1>
        <p class="brand-intro">
          从员工档案到组织管理，从考勤记录到请假审批，
          再到人格测评与人才分析。一个工作空间，连接人员与日常工作。
        </p>
      </div>

      <ul class="brand-features">
        <li>
          <span class="feature-index">01</span>
          <span>
            <strong>组织人事</strong>
            <small>档案、部门、岗位与角色统一管理</small>
          </span>
        </li>
        <li>
          <span class="feature-index">02</span>
          <span>
            <strong>考勤休假</strong>
            <small>打卡记录与请假审批清晰可循</small>
          </span>
        </li>
        <li>
          <span class="feature-index">03</span>
          <span>
            <strong>人才测评</strong>
            <small>人格偏好洞察助力团队协作</small>
          </span>
        </li>
      </ul>

      <p class="brand-footnote">Personnel System · 企业人事管理工作空间</p>
    </aside>

    <!-- 表单区 -->
    <section class="auth-form-side">
      <div class="auth-form-panel">
        <div class="auth-form-inner">
          <p class="form-eyebrow">{{ register ? '创建账号' : '登录' }}</p>
          <slot />
        </div>
      </div>
      <p class="auth-copyright">© 2026 Personnel System</p>
    </section>
  </main>
</template>

<style scoped lang="scss">
.public-auth {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  min-height: 100dvh;
  background: var(--bg-card);
}

// ---------- 品牌区：深蓝黑 + mint 点缀 ----------
.auth-brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: var(--space-8) clamp(32px, 6vw, 88px);
  background: var(--brand-700); // 深墨绿黑
  color: #9aa3a0;
  overflow: hidden;
}
// 青绿光斑点缀
.auth-brand-panel::before {
  content: '';
  position: absolute;
  top: -140px;
  right: -140px;
  width: 380px;
  height: 380px;
  border-radius: 50%;
  background: radial-gradient(circle, rgb(78 201 164 / 26%), transparent 70%);
  pointer-events: none;
}
.auth-brand-panel::after {
  content: '';
  position: absolute;
  bottom: -180px;
  left: -120px;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  background: radial-gradient(circle, rgb(58 169 140 / 20%), transparent 70%);
  pointer-events: none;
}
.auth-brand {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  text-decoration: none;
  color: #fff;
  font-size: var(--font-size-xl);
  line-height: var(--line-height-xl);
  font-weight: 700;
}
.brand-symbol {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 11px;
  background: linear-gradient(135deg, var(--accent-400), var(--accent-600));
  color: #fff;
  box-shadow: 0 6px 16px rgb(78 201 164 / 40%);
}
.brand-symbol svg {
  width: 20px;
  height: 20px;
}
.brand-story {
  position: relative;
  z-index: 1;
  margin-top: auto;
  padding-top: var(--space-8);
}
.brand-story h1 {
  font-size: clamp(32px, 4vw, 46px);
  line-height: 1.18;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #fff;
  margin: 0 0 var(--space-5);
}
.brand-intro {
  font-size: var(--font-size-base);
  line-height: var(--line-height-lg);
  color: #a9b3af;
  max-width: 40ch;
  margin: 0;
}
.brand-features {
  position: relative;
  z-index: 1;
  list-style: none;
  margin: var(--space-7) 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.brand-features li {
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
}
.feature-index {
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 20px;
  color: var(--accent-400);
  padding-top: 2px;
  font-weight: 600;
}
.brand-features strong {
  display: block;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-base);
  font-weight: 600;
  color: #e8ecea;
}
.brand-features small {
  display: block;
  font-size: 12px;
  line-height: 20px;
  color: #8f9995;
  margin-top: 2px;
}
.brand-footnote {
  position: relative;
  z-index: 1;
  margin: var(--space-7) 0 0;
  font-size: 12px;
  line-height: 16px;
  color: #6f7a76;
}

// ---------- 表单区 ----------
.auth-form-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-8) var(--space-6);
  background: var(--bg-page);
}
.auth-form-panel {
  width: min(420px, 100%);
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  padding: var(--space-8);
  box-shadow: var(--shadow-md);
}
.auth-form-inner {
  width: 100%;
}
.form-eyebrow {
  font-size: 12px;
  line-height: 16px;
  color: var(--text-disabled);
  margin: 0 0 var(--space-5);
  font-weight: 500;
}
.auth-copyright {
  margin: var(--space-5) 0 0;
  font-size: 12px;
  line-height: 16px;
  color: var(--text-disabled);
}

// 表单内元素（由 login/register 的 slot 提供）
.auth-form-inner :deep(h1) {
  font-size: var(--font-size-3xl);
  line-height: var(--line-height-2xl);
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 0 0 var(--space-2);
}
.auth-form-inner :deep(.tip) {
  font-size: var(--font-size-base);
  line-height: var(--line-height-base);
  color: var(--text-secondary);
  margin: 0 0 var(--space-6);
}
.auth-form-inner :deep(.el-form-item) {
  margin-bottom: var(--space-5);
}
.auth-form-inner :deep(.el-form-item__label) {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--text-primary);
  padding-bottom: var(--space-2);
}
.auth-form-inner :deep(.el-input__wrapper) {
  min-height: 42px;
  border-radius: var(--radius-md);
}
.auth-form-inner :deep(.submit-button) {
  width: 100%;
  min-height: 42px;
  margin: var(--space-2) 0 var(--space-5);
  font-size: var(--font-size-base);
  font-weight: 600;
}
.auth-form-inner :deep(.auth-meta) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-5);
  margin-bottom: var(--space-5);
  border-bottom: 1px solid var(--border-light);
  font-size: 12px;
  line-height: 20px;
  color: var(--text-secondary);
}
.auth-form-inner :deep(.auth-switch) {
  text-align: center;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-base);
  color: var(--text-secondary);
  margin: 0;
}
.auth-form-inner :deep(.auth-switch a) {
  margin-left: 4px;
  font-weight: 600;
}
.auth-form-inner :deep(.form-note) {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-secondary);
  margin: var(--space-2) 0 var(--space-4);
}

@media (max-width: 860px) {
  .public-auth {
    grid-template-columns: 1fr;
  }
  .auth-brand-panel {
    display: none;
  }
  .auth-form-side {
    padding: var(--space-6) var(--space-4);
  }
}
</style>
