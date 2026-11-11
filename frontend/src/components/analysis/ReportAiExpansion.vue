<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { expandAiReport } from '@/api/ai'
import type { AiAnswerVO } from '@/types/ai'
import AiAnswer from './AiAnswer.vue'
const props = defineProps<{ resultId: number }>()
const answer = ref<AiAnswerVO>(),
  busy = ref(false),
  failed = ref(false)
let sequence = 0
watch(
  () => props.resultId,
  () => {
    sequence++
    answer.value = undefined
    busy.value = false
    failed.value = false
  },
)
onBeforeUnmount(() => {
  sequence++
})
async function expand() {
  if (busy.value) return
  const current = ++sequence
  busy.value = true
  failed.value = false
  answer.value = undefined
  try {
    const response = await expandAiReport(props.resultId)
    if (current === sequence) answer.value = response.data.data
  } catch {
    if (current === sequence) failed.value = true
  } finally {
    if (current === sequence) busy.value = false
  }
}
</script>
<template>
  <section class="expansion">
    <h3>辅助理解与行动整理</h3>
    <p>
      点击后按后端配置使用本地Mock或真实模型整理。真实模式仅发送脱敏后的偏好与规则上下文；原始报告不会改写。
    </p>
    <el-button
      :loading="busy"
      @click="expand"
    >
      整理本次报告
    </el-button>
    <el-alert
      v-if="failed"
      title="辅助整理暂不可用，可重试；原始报告仍可正常阅读"
      type="warning"
      :closable="false"
    />
    <AiAnswer
      v-if="answer"
      :answer="answer"
    />
  </section>
</template>
<style scoped lang="scss">
.expansion {
  border-top: 1px solid var(--accent-100);
  padding-top: 16px;
  margin-top: 24px;
}
p {
  line-height: 1.7;
}
.el-alert {
  margin-top: 12px;
}
</style>
