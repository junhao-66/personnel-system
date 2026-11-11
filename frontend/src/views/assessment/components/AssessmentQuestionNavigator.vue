<script setup lang="ts">
// Answer overview for the current draft; navigation never changes or saves an answer.
import { computed } from 'vue'
import type { ScaleQuestionVO } from '@/types/assessment'

const props = defineProps<{
  questions: ScaleQuestionVO[]
  answers: Record<string, number>
  disabled: boolean
}>()
const emit = defineEmits<{ navigate: [index: number] }>()
const firstUnanswered = computed(() =>
  props.questions.findIndex((question) => props.answers[question.key] === undefined),
)
const unansweredCount = computed(
  () => props.questions.filter((question) => props.answers[question.key] === undefined).length,
)
const handleNavigate = (index: number) => {
  if (!props.disabled && index >= 0 && index < props.questions.length) emit('navigate', index)
}
</script>

<template>
  <nav
    aria-label="答题题号导航"
    class="question-navigator"
  >
    <div class="navigator-heading">
      <p>
        答题概览：还有
        {{ unansweredCount }} 题未答。题号中的“已答”仅表示本页已选择，保存状态以上方提示为准。
      </p>
      <el-button
        :disabled="disabled || firstUnanswered < 0"
        @click="handleNavigate(firstUnanswered)"
      >
        定位首个未答题
      </el-button>
    </div>
    <ol class="question-number-list">
      <li
        v-for="(question, index) in questions"
        :key="question.key"
      >
        <button
          type="button"
          :disabled="disabled"
          :aria-label="
            '前往第' +
            (index + 1) +
            '题，' +
            (answers[question.key] === undefined ? '未答' : '已答')
          "
          :class="{ answered: answers[question.key] !== undefined }"
          @click="handleNavigate(index)"
        >
          {{ index + 1 }}
          <span>{{ answers[question.key] === undefined ? '未答' : '已答' }}</span>
        </button>
      </li>
    </ol>
  </nav>
</template>

<style scoped lang="scss">
.question-navigator {
  margin-top: 20px;
  padding: 16px;
  border: 1px solid var(--border-default);
  border-radius: 14px;
}
.navigator-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  justify-content: space-between;
}
p {
  margin: 0;
  line-height: 1.7;
  flex: 1 1 260px;
  font-size: 13px;
  color: var(--text-secondary);
}
.question-number-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(60px, 1fr));
  gap: 8px;
  padding: 0;
  margin: 16px 0 0;
  list-style: none;
}
button {
  width: 100%;
  min-height: 48px;
  padding: 6px;
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  background: white;
  color: var(--text-primary);
  cursor: pointer;
  font: inherit;
}
button span {
  display: block;
  font-size: 12px;
}
button.answered {
  background: var(--brand-100);
  border-color: var(--brand-500);
  color: var(--brand-600);
}
button:focus-visible {
  outline: 3px solid var(--brand-600);
  outline-offset: 2px;
}
button:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
