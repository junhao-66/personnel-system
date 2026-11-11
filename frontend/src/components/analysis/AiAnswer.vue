<script setup lang="ts">
import type { AiAnswerVO } from '@/types/ai'
defineProps<{ answer: AiAnswerVO }>()
const labels = {
  MOCK: '本地Mock演示（非真实模型生成）',
  REMOTE: '真实模型辅助整理',
  RULE_FALLBACK: '规则模板降级',
  REFUSED: '超出可回答范围',
}
const reasons: Record<string, string> = {
  LOCAL_DEMO: '当前未调用外部模型',
  AI_DISABLED: 'AI增强已关闭',
  EXTERNAL_DISABLED: '外部调用未开启',
  REMOTE_NOT_CONFIGURED: '真实API配置不完整',
  REMOTE_UNAVAILABLE: '模型超时、不可用或返回不合规内容',
  GROUNDED_SELECTION: '模型仅选择已有依据与表达框架',
  OUT_OF_SCOPE: '缺少可回答依据或问题超出范围',
}
</script>
<template>
  <section
    class="answer"
    aria-live="polite"
  >
    <h3>{{ labels[answer.mode] }}</h3>
    <p>{{ reasons[answer.reason] || answer.reason }}</p>
    <article
      v-for="(paragraph, index) in answer.paragraphs"
      :key="`${paragraph.evidenceId}-${index}`"
    >
      <p>{{ paragraph.text }}</p>
      <small>依据 {{ paragraph.evidenceId }}：{{ paragraph.basis }}</small>
    </article>
    <p class="meta">
      提示词 {{ answer.promptVersion }} · 上下文 {{ answer.contextVersion }}
      <span v-if="answer.resultId">· 报告 #{{ answer.resultId }}</span>
    </p>
    <p class="meta">{{ answer.notice }}</p>
  </section>
</template>
<style scoped lang="scss">
.answer {
  margin-top: 20px;
  padding: 20px;
  background: var(--accent-50);
  border: 1px solid var(--accent-100);
  border-radius: 14px;
  overflow-wrap: anywhere;
}
p {
  line-height: 1.8;
}
article {
  margin-bottom: 18px;
}
small,
.meta {
  color: var(--text-secondary);
  font-size: 12px;
}
</style>
