<script setup lang="ts">
import PersonalityVisual from '@/components/PersonalityVisual.vue'
</script>

<template>
  <main class="mbti-module">
    <header class="module-heading">
      <div>
        <h1>MBTI人格偏好</h1>
        <p>先认识不同的偏好，再了解自己的工作与沟通方式。</p>
      </div>
      <RouterLink
        to="/assessment"
        class="module-history"
      >
        测试我的MBTI人格
      </RouterLink>
    </header>
    <div class="mbti-introduction">
      <span class="module-label">人格概念导览</span>
      <span>选择类型或切换四个偏好维度，了解16种人格视角。</span>
    </div>
    <div class="mbti-atlas-panel"><PersonalityVisual /></div>
    <p class="mbti-boundary">
      人格偏好用于自我了解与沟通反思，不代表能力、绩效或发展潜力，也不用于招聘淘汰与晋升判断。
    </p>
  </main>
</template>

<style scoped lang="scss">
.mbti-module {
  max-width: 1400px;
  margin: auto;
  padding: 32px;
}
.module-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 32px;
}
.module-heading h1 {
  margin: 8px 0 12px;
}
.module-heading > div > p:last-child {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 24px;
}
.module-history {
  font-size: 14px;
  text-decoration: none;
  white-space: nowrap;
}
.mbti-introduction {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-secondary);
  margin-bottom: 16px;
}
.module-label {
  background: var(--bg-subtle);
  color: var(--brand-500);
  padding: 4px 8px;
  border-radius: 4px;
}
.mbti-atlas-panel {
  background: #fff;
  border: 1px solid var(--border-light);
  border-radius: 16px;
  padding: 24px;
}
.mbti-test-entry {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 32px;
  margin-top: 24px;
  padding: 32px;
  background: var(--bg-subtle);
  border: 1px solid var(--border-light);
  border-radius: 16px;
}
.mbti-test-entry h2 {
  margin: 8px 0 12px;
}
.mbti-test-entry p:last-child {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 24px;
}
.test-mbti-button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 12px 16px;
  border-radius: 8px;
  background: var(--text-primary);
  color: #fff;
  text-decoration: none;
  white-space: nowrap;
  font-size: 14px;
  line-height: 20px;
}
.test-mbti-button:hover {
  background: var(--text-secondary);
}
.test-mbti-button:active {
  transform: scale(0.98);
}
.mbti-boundary {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 20px;
  margin: 16px 0 0;
}
@media (max-width: 768px) {
  .mbti-module {
    padding: 24px 16px;
  }
  .module-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
  }
  .mbti-introduction {
    flex-wrap: wrap;
    gap: 8px;
  }
  .mbti-atlas-panel {
    padding: 16px;
  }
  .mbti-test-entry {
    flex-direction: column;
    align-items: stretch;
    gap: 24px;
    padding: 24px;
  }
}
</style>
