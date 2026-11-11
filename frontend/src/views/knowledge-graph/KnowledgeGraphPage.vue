<script setup lang="ts">
// Bounded graph exploration, with a keyboard-operable text alternative and traceable edge evidence.
import { computed, onMounted, reactive, ref } from 'vue'
import { getKnowledgeGraph } from '@/api/knowledgeGraph'
import GraphCanvas from './components/GraphCanvas.vue'
import type { GraphEdge, GraphNode, KnowledgeGraphVO } from '@/types/knowledgeGraph'
const graph = ref<KnowledgeGraphVO>()
const filters = reactive({ keyword: '', relation: '' })
const selectedId = ref('I')
const selectedEdgeId = ref('')
const isLoading = ref(false)
const isLoadFailed = ref(false)
const labels: Record<string, string> = {
  MEASURES: '题目计分归属',
  ASSOCIATED_WITH: '关联',
  SUGGESTS: '可探索建议',
  IN_CONTEXT: '适用情境',
  SUPPORTED_BY: '依据',
}
const selected = computed(() => graph.value?.nodes.find((n) => n.id === selectedId.value))
const selectedEdge = computed(() => graph.value?.edges.find((e) => e.id === selectedEdgeId.value))
const source = computed(() =>
  graph.value?.sources.find(
    (s) => s.id === (selectedEdge.value?.sourceId || selected.value?.sourceId),
  ),
)
const foundNodes = computed(
  () =>
    graph.value?.nodes.filter(
      (n) =>
        !filters.keyword ||
        (n.label + n.description + n.id).toLowerCase().includes(filters.keyword.toLowerCase()),
    ) || [],
)
const related = computed(
  () =>
    graph.value?.edges.filter(
      (e) =>
        (e.from === selectedId.value || e.to === selectedId.value) &&
        (!filters.relation || e.relation === filters.relation),
    ) || [],
)
const visibleNodes = computed(() => {
  const ids = new Set([selectedId.value])
  related.value.forEach((e) => {
    ids.add(e.from)
    ids.add(e.to)
  })
  const neighbors =
    graph.value?.nodes.filter((n) => ids.has(n.id) && n.id !== selectedId.value).slice(0, 24) || []
  return selected.value ? [selected.value, ...neighbors] : []
})
const visibleEdges = computed(() => {
  const ids = new Set(visibleNodes.value.map((n) => n.id))
  return related.value.filter((e) => ids.has(e.from) && ids.has(e.to))
})
const nodeLabel = (id: string) => graph.value?.nodes.find((n) => n.id === id)?.label || id
const handleSelect = (node: GraphNode) => {
  selectedId.value = node.id
  selectedEdgeId.value = ''
}
const handleExpand = (edge: GraphEdge) => {
  const node = graph.value?.nodes.find(
    (n) => n.id === (edge.from === selectedId.value ? edge.to : edge.from),
  )
  if (node) handleSelect(node)
}
const getData = async () => {
  isLoading.value = true
  isLoadFailed.value = false
  try {
    graph.value = (await getKnowledgeGraph()).data.data || undefined
    if (!graph.value?.nodes.some((n) => n.id === selectedId.value))
      selectedId.value = graph.value?.nodes[0]?.id || ''
  } catch {
    graph.value = undefined
    isLoadFailed.value = true
  } finally {
    isLoading.value = false
  }
}
onMounted(getData)
</script>
<template>
  <main
    v-loading="isLoading"
    class="graph-page"
  >
    <header class="graph-heading">
      <div>
        <RouterLink to="/workbench">返回个人工作台</RouterLink>
        <h1>人格知识关系图</h1>
        <p>题目 → 偏好维度 → 多维条件 → 情境 → 行动实验。点击节点展开相邻关系。</p>
      </div>
      <el-button
        :loading="isLoading"
        @click="getData"
      >
        刷新图谱
      </el-button>
    </header>
    <el-alert
      title="图谱用于解释关联，不表示因果。项目演示关系未经实证验证；题库来源不等于职业和成长建议的依据。"
      type="warning"
      :closable="false"
    />
    <el-alert
      v-if="isLoadFailed"
      title="图谱加载失败，请刷新重试"
      type="error"
      :closable="false"
    />
    <template v-if="graph">
      <div class="graph-toolbar">
        <p>
          {{ graph.title }} · {{ graph.version }} · {{ graph.nodes.length }}节点 /
          {{ graph.edges.length }}关系 / {{ graph.rules.length }}多维规则
        </p>
        <div class="filters">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索题目、偏好、情境或建议"
            clearable
          />
          <el-select
            v-model="filters.relation"
            placeholder="关系类型"
            clearable
          >
            <el-option
              v-for="(label, key) in labels"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
        </div>
      </div>
      <div class="graph-layout">
        <aside class="node-list">
          <p>搜索结果 {{ foundNodes.length }} 个</p>
          <button
            v-for="node in foundNodes"
            :key="node.id"
            :class="{ active: selectedId === node.id }"
            @click="handleSelect(node)"
          >
            {{ node.label }}
            <small>{{ node.type }}</small>
          </button>
        </aside>
        <section
          class="canvas-panel"
          aria-label="关系图"
        >
          <GraphCanvas
            :key="`${graph.contentHash}:${selectedId}:${filters.relation}`"
            :nodes="visibleNodes"
            :edges="visibleEdges"
            :selected-id="selectedId"
            @select="handleSelect"
          />
          <p class="hint">
            箭头为记录方向，圆图最多展示24个邻居；完整相邻关系见下方列表。同一规则的全部前提须同时成立。
          </p>
        </section>
        <aside class="graph-inspector">
          <section
            v-if="selected"
            class="inspector-section"
          >
            <span class="inspector-label">当前节点</span>
            <h2>{{ selected.label }}</h2>
            <p>{{ selected.description }}</p>
            <small>{{ selected.id }} · {{ selected.type }}</small>
            <div class="relationships">
              <article
                v-for="edge in related"
                :key="edge.id"
              >
                <button @click="selectedEdgeId = edge.id">
                  {{ nodeLabel(edge.from) }} → {{ labels[edge.relation] }} →
                  {{ nodeLabel(edge.to) }}
                </button>
                <p>{{ edge.explanation }}</p>
                <el-button
                  size="small"
                  @click="handleExpand(edge)"
                >
                  展开相邻节点
                </el-button>
              </article>
            </div>
          </section>
          <section
            v-if="source"
            class="inspector-section evidence-panel"
          >
            <span class="inspector-label">{{ selectedEdge ? '关系依据' : '节点来源' }}</span>
            <el-tag :type="source.evidenceLevel === 'PROJECT_DEMO' ? 'warning' : 'info'">
              {{ source.evidenceLevel === 'PROJECT_DEMO' ? '项目演示假设' : '来源结构参考' }}
            </el-tag>
            <p>{{ source.title }} · {{ source.author }}</p>
            <p>{{ source.attribution }}</p>
            <p>
              许可：{{ source.license }} · 版本：{{ source.contentVersion }} · 访问：{{
                source.accessedDate
              }}
            </p>
            <a
              :href="source.url"
              target="_blank"
              rel="noopener noreferrer"
            >
              查看来源
            </a>
          </section>
          <details class="rules-panel">
            <summary>多维规则说明 · {{ graph.rules.length }}</summary>
            <article
              v-for="rule in graph.rules"
              :key="rule.id"
            >
              <h3>{{ rule.id }}</h3>
              <p>
                {{
                  rule.conditions.map((c) => c.pole + '端≥' + c.minimumPercent + '%').join(' 且 ')
                }}
              </p>
              <p>{{ rule.explanation }}</p>
            </article>
          </details>
        </aside>
      </div>
    </template>
    <p v-else-if="!isLoading && !isLoadFailed">尚无已发布图谱，请联系管理员导入并发布。</p>
  </main>
</template>
<style scoped lang="scss">
.graph-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 1480px;
  height: calc(100dvh - 152px);
  min-height: 600px;
  margin: auto;
  padding: 12px 24px 16px;
  color: var(--text-primary);
  overflow: hidden;
  overflow-wrap: anywhere;
}
.graph-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 10px;
}
.graph-heading h1 {
  margin: 4px 0;
  font-size: 24px;
  line-height: 32px;
}
.graph-heading p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
}
.graph-heading > div > a {
  font-size: 12px;
}
h1,
h2,
h3 {
  color: var(--brand-600);
}
p {
  line-height: 1.5;
}
.graph-page > :deep(.el-alert) {
  flex-shrink: 0;
  margin-bottom: 10px;
  padding-block: 8px;
}
.graph-toolbar {
  display: grid;
  grid-template-columns: minmax(280px, 0.8fr) minmax(480px, 1.4fr);
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
  margin-bottom: 10px;
}
.graph-toolbar > p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
}
.filters {
  display: grid;
  grid-template-columns: minmax(240px, 1.4fr) minmax(180px, 0.8fr);
  gap: 8px;
  margin: 0;
}
.filters > * {
  min-width: 0;
}
.graph-layout {
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr) 270px;
  gap: 12px;
  flex: 1;
  min-height: 0;
}
.node-list {
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--border-light);
  background: rgb(255 255 255 / 78%);
  border-radius: 12px;
  padding: 8px;
}
.node-list > p {
  position: sticky;
  top: -8px;
  z-index: 1;
  margin: 0 0 8px;
  padding: 8px;
  background: rgb(255 255 255 / 94%);
  color: var(--text-secondary);
  font-size: 12px;
}
button {
  cursor: pointer;
  color: var(--brand-600);
  background: #fff;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  padding: 8px 10px;
  text-align: left;
  font: inherit;
}
button:focus-visible {
  outline: 3px solid var(--brand-500);
}
.node-list button {
  display: block;
  width: 100%;
  margin-bottom: 6px;
  transition:
    background-color 250ms var(--ease-out),
    border-color 250ms var(--ease-out),
    transform 250ms var(--ease-out);
}
.node-list button:hover {
  border-color: rgb(36 32 51 / 16%);
  background: #f4f2f7;
  transform: translate3d(2px, 0, 0);
}
.node-list .active {
  border-color: #242033;
  background: #242033;
  color: #fff;
}
.node-list .active small {
  color: rgb(255 255 255 / 64%);
}
small {
  display: block;
  color: var(--text-secondary);
}
.canvas-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--border-light);
  background: rgb(255 255 255 / 82%);
  border-radius: 12px;
  padding: 10px;
  overflow: hidden;
}
.canvas-panel :deep(.controls) {
  flex-shrink: 0;
}
.canvas-panel :deep(svg) {
  flex: 1;
  width: 100%;
  min-height: 0;
}
.canvas-panel > .hint,
.canvas-panel :deep(.hint) {
  flex-shrink: 0;
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 16px;
}
.graph-inspector {
  min-width: 0;
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--border-light);
  border-radius: 12px;
  background: rgb(255 255 255 / 84%);
  padding: 12px;
}
.inspector-section + .inspector-section,
.rules-panel {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}
.inspector-label {
  display: block;
  margin-bottom: 6px;
  color: var(--text-secondary);
  font-size: 11px;
}
.inspector-section h2 {
  margin: 0 0 6px;
  font-size: 16px;
  line-height: 24px;
}
.inspector-section > p,
.inspector-section > small {
  margin: 4px 0;
  font-size: 12px;
}
.relationships {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}
.relationships article {
  padding: 10px;
  border: 0;
  border-radius: 8px;
  background: #f7f6f9;
}
.relationships article > button {
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  font-size: 12px;
  font-weight: 500;
}
.relationships article p {
  margin: 6px 0;
  color: var(--text-secondary);
  font-size: 11px;
}
.rules-panel summary {
  color: var(--text-regular);
  font-size: 12px;
  cursor: pointer;
}
.rules-panel article {
  padding: 10px 0;
}
.rules-panel h3,
.rules-panel p {
  margin: 4px 0;
  font-size: 11px;
}
.hint {
  font-size: 12px;
  color: var(--text-secondary);
}
article {
  border-bottom: 1px solid var(--border-default);
  padding: 12px 0;
}
@media (max-width: 1180px) {
  .graph-layout {
    grid-template-columns: 180px minmax(0, 1fr) 240px;
  }
}
@media (max-width: 680px) {
  .graph-page {
    height: auto;
    min-height: 0;
    padding: 16px 12px;
    overflow: visible;
  }
  .graph-heading,
  .graph-toolbar {
    display: flex;
    align-items: stretch;
    flex-direction: column;
  }
  .filters,
  .graph-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .node-list {
    max-height: 220px;
  }
  .canvas-panel {
    min-height: 480px;
  }
  .graph-inspector {
    max-height: none;
  }
}
</style>
