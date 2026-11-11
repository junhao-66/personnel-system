<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

const root = ref<HTMLElement>()
const lines = [
  ['理解', '每一种', '不同，'],
  ['让', '协作', '多一种', '可能。'],
]
let observer: IntersectionObserver | undefined
onMounted(() => {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          observer?.unobserve(entry.target)
        }
      })
    },
    { rootMargin: '0px 0px -15% 0px', threshold: 1 },
  )
  root.value?.querySelectorAll('.benefit-word').forEach((word) => observer?.observe(word))
})
onUnmounted(() => observer?.disconnect())
</script>

<template>
  <section
    ref="root"
    class="benefit-reveal"
    aria-label="理解每一种不同，让协作多一种可能。"
  >
    <p class="benefit-caption">看见标签之外的每一个人</p>
    <h2 aria-hidden="true">
      <span
        v-for="(line, index) in lines"
        :key="index"
        class="benefit-line"
      >
        <span
          v-for="(word, wordIndex) in line"
          :key="word"
          class="benefit-word"
          :class="'word-' + wordIndex"
        >
          {{ word }}
        </span>
      </span>
    </h2>
    <p>人格偏好是沟通的起点。看见不同，也为每个人保留成长的空间。</p>
  </section>
</template>

<style scoped lang="scss">
.benefit-reveal {
  padding: 80px 0 0;
  max-width: 680px;
}
.benefit-caption {
  color: var(--text-disabled);
  font-size: 12px;
  line-height: 16px;
  letter-spacing: 0;
  margin: 0 0 24px;
}
.benefit-reveal h2 {
  margin: 0 0 24px;
  font-size: 48px;
  line-height: 1.25;
  font-weight: 500;
  letter-spacing: -1px;
}
.benefit-line {
  display: block;
}
.benefit-word {
  color: var(--text-strong);
}
.benefit-reveal > p:last-child {
  font-size: 14px;
  line-height: 20px;
  color: var(--text-secondary);
}
@media (prefers-reduced-motion: no-preference) {
  .benefit-word {
    opacity: 0.3;
    transition: opacity 800ms cubic-bezier(0.22, 1, 0.36, 1);
  }
  .benefit-word.is-visible {
    opacity: 1;
  }
  .word-1 {
    transition-delay: 80ms;
  }
  .word-2 {
    transition-delay: 160ms;
  }
  .word-3 {
    transition-delay: 240ms;
  }
}
@media (max-width: 768px) {
  .benefit-reveal {
    padding-top: 48px;
  }
  .benefit-reveal h2 {
    font-size: 30px;
    line-height: 36px;
  }
}
</style>
