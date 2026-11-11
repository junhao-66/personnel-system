<script setup lang="ts">
import type { WeightedRule } from '@/types/analysis'
defineProps<{ evidence: WeightedRule }>()
const safeLink = (url: string) => (/^https?:\/\//i.test(url) ? url : undefined)
</script>
<template>
  <section class="rule-evidence">
    <h4>{{ evidence.context }} · 权重 {{ evidence.weight }}%</h4>
    <p>规则 {{ evidence.rule.id }}：{{ evidence.rule.explanation }}</p>
    <p>
      全部满足：{{
        evidence.rule.conditions
          .map((c) => `${c.dimension} 的 ${c.pole} 端 ≥ ${c.minimumPercent}%`)
          .join(' 且 ')
      }}
    </p>
    <p>可尝试的行动：{{ evidence.action }}</p>
    <details>
      <summary>查看关系路径与来源</summary>
      <p
        v-for="edge in evidence.edges"
        :key="edge.id"
      >
        {{ evidence.nodes.find((n) => n.id === edge.from)?.label || edge.from }} →
        {{ edge.relation }} →
        {{ evidence.nodes.find((n) => n.id === edge.to)?.label || edge.to }}：{{ edge.explanation }}
      </p>
      <p
        v-for="source in evidence.sources"
        :key="source.id"
      >
        <a
          v-if="safeLink(source.url)"
          :href="safeLink(source.url)"
          target="_blank"
          rel="noopener noreferrer"
        >
          {{ source.title }}
        </a>
        <span v-else>{{ source.title }}</span>
        · {{ source.author }} · {{ source.license }} · {{ source.evidenceLevel }} ·
        {{ source.contentVersion }}
        <br />
        {{ source.attribution }}
      </p>
    </details>
  </section>
</template>
<style scoped lang="scss">
.rule-evidence {
  padding: 12px 16px;
  background: var(--bg-subtle);
  border-radius: 12px;
  margin: 12px 0;
  overflow-wrap: anywhere;
}
h4 {
  margin: 0 0 8px;
}
p {
  line-height: 1.65;
}
summary {
  cursor: pointer;
}
a {
  color: var(--brand-600);
}
</style>
