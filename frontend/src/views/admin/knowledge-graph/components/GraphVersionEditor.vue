<script setup lang="ts">
// Edit a local copy and import a new immutable version through the existing validated API.
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import GraphEvidenceEditor from './GraphEvidenceEditor.vue'
import { validateGraphRule } from './graphRuleValidation'
import { importGraphVersion } from '@/api/knowledgeGraph'
import type {
  GraphEdge,
  GraphImportReqDTO,
  GraphNode,
  KnowledgeGraphVO,
} from '@/types/knowledgeGraph'

const props = defineProps<{ graph: KnowledgeGraphVO }>()
const emit = defineEmits<{ saved: [value: KnowledgeGraphVO]; cancel: []; busy: [value: boolean] }>()
const draft = reactive<GraphImportReqDTO>(
  JSON.parse(
    JSON.stringify({
      version: '',
      title: props.graph.title,
      sources: props.graph.sources,
      nodes: props.graph.nodes,
      edges: props.graph.edges,
      rules: props.graph.rules,
    }),
  ),
)
const tab = ref('nodes')
const keyword = ref('')
const isSubmitting = ref(false)
const isDialogOpen = ref(false)
const editingId = ref<string>()
const kind = ref<'node' | 'edge'>('node')
const nodeForm = reactive<GraphNode>({
  id: '',
  type: 'ACTION',
  label: '',
  description: '',
  sourceId: '',
})
const edgeForm = reactive<GraphEdge>({
  id: '',
  from: '',
  to: '',
  relation: 'ASSOCIATED_WITH',
  explanation: '',
  sourceId: '',
})
const nodeTypes: Record<string, string> = {
  QUESTION: '题目',
  DIMENSION: '维度',
  TYPE: '人格类型',
  PREFERENCE: '偏好',
  TENDENCY: '倾向',
  WORK_STYLE: '工作方式',
  DEVELOPMENT: '发展方向',
  ACTION: '行动建议',
  CONTEXT: '情境',
  SOURCE: '来源',
}
const relations: Record<string, string> = {
  ASSOCIATED_WITH: '关联',
  SUGGESTS: '可探索建议',
  IN_CONTEXT: '适用情境',
  SUPPORTED_BY: '依据',
  MEASURES: '题目计分归属',
}
const filteredNodes = computed(() =>
  draft.nodes.filter((row) =>
    `${row.id} ${row.label} ${row.description}`.toLowerCase().includes(keyword.value.toLowerCase()),
  ),
)
const filteredEdges = computed(() =>
  draft.edges.filter((row) =>
    `${row.id} ${row.from} ${row.to} ${row.explanation}`
      .toLowerCase()
      .includes(keyword.value.toLowerCase()),
  ),
)
const label = (id: string) => draft.nodes.find((row) => row.id === id)?.label || id
const sourceLabel = (id: string) => draft.sources.find((row) => row.id === id)?.title || id
const handleNode = (row?: GraphNode) => {
  kind.value = 'node'
  editingId.value = row?.id
  Object.assign(
    nodeForm,
    row || {
      id: '',
      type: 'ACTION',
      label: '',
      description: '',
      sourceId: draft.sources[0]?.id || '',
    },
  )
  isDialogOpen.value = true
}
const handleEdge = (row?: GraphEdge) => {
  kind.value = 'edge'
  editingId.value = row?.id
  Object.assign(
    edgeForm,
    row || {
      id: '',
      from: '',
      to: '',
      relation: 'ASSOCIATED_WITH',
      explanation: '',
      sourceId: draft.sources[0]?.id || '',
    },
  )
  isDialogOpen.value = true
}
const handleSaveItem = () => {
  const value = kind.value === 'node' ? nodeForm : edgeForm
  if (!/^[A-Za-z0-9_-]{1,40}$/.test(value.id)) {
    ElMessage.warning('标识须为1至40位字母、数字、下划线或短横线')
    return
  }
  if (!draft.sources.some((source) => source.id === value.sourceId)) {
    ElMessage.warning('请选择有效来源')
    return
  }
  if (kind.value === 'node') {
    if (!nodeForm.label.trim() || !nodeForm.description.trim()) {
      ElMessage.warning('请填写节点名称和说明')
      return
    }
    if (!editingId.value && draft.nodes.some((row) => row.id === value.id)) {
      ElMessage.warning('节点标识已存在')
      return
    }
    if (!editingId.value && draft.nodes.length >= 128) {
      ElMessage.warning('每个版本最多128个节点')
      return
    }
    const referenced = draft.rules.some(
      (rule) =>
        rule.actionId === value.id ||
        rule.contextId === value.id ||
        rule.conditions.some((condition) => condition.nodeId === value.id),
    )
    const original = draft.nodes.find((row) => row.id === editingId.value)
    if (referenced && original && original.type !== nodeForm.type) {
      ElMessage.warning('规则引用的节点不能变更类型，请通过完整版本导入调整规则')
      return
    }
    const copy = {
      ...nodeForm,
      label: nodeForm.label.trim(),
      description: nodeForm.description.trim(),
    }
    if (editingId.value)
      draft.nodes.splice(
        draft.nodes.findIndex((row) => row.id === editingId.value),
        1,
        copy,
      )
    else draft.nodes.push(copy)
  } else {
    if (
      !draft.nodes.some((row) => row.id === edgeForm.from) ||
      !draft.nodes.some((row) => row.id === edgeForm.to)
    ) {
      ElMessage.warning('请选择有效起点和终点')
      return
    }
    if (!edgeForm.explanation.trim()) {
      ElMessage.warning('请填写关系说明')
      return
    }
    if (!editingId.value && draft.edges.some((row) => row.id === value.id)) {
      ElMessage.warning('关系标识已存在')
      return
    }
    if (!editingId.value && draft.edges.length >= 256) {
      ElMessage.warning('每个版本最多256条关系')
      return
    }
    const original = draft.edges.find((row) => row.id === editingId.value)
    if (
      original &&
      draft.rules.some((rule) => rule.edgeIds.includes(original.id)) &&
      (original.from !== edgeForm.from || original.to !== edgeForm.to)
    ) {
      ElMessage.warning('规则引用的关系不能改变端点，请通过完整版本导入调整规则')
      return
    }
    const copy = { ...edgeForm, explanation: edgeForm.explanation.trim() }
    if (editingId.value)
      draft.edges.splice(
        draft.edges.findIndex((row) => row.id === editingId.value),
        1,
        copy,
      )
    else draft.edges.push(copy)
  }
  isDialogOpen.value = false
}
const handleDelete = async (row: GraphNode | GraphEdge, isNode: boolean) => {
  const referenced = isNode
    ? draft.edges.some((edge) => edge.from === row.id || edge.to === row.id) ||
      draft.rules.some(
        (rule) =>
          rule.actionId === row.id ||
          rule.contextId === row.id ||
          rule.conditions.some((condition) => condition.nodeId === row.id),
      )
    : draft.rules.some((rule) => rule.edgeIds.includes(row.id))
  if (referenced) {
    ElMessage.warning(
      isNode
        ? '节点仍被关系或规则引用，请先处理引用'
        : '关系仍被规则引用，请通过完整版本导入调整规则',
    )
    return
  }
  if ((isNode ? draft.nodes : draft.edges).length <= 1) {
    ElMessage.warning('至少保留一个节点和一条关系')
    return
  }
  try {
    await ElMessageBox.confirm(`确认从本地编辑副本删除 ${row.id}？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  if (isNode) draft.nodes = draft.nodes.filter((value) => value.id !== row.id)
  else draft.edges = draft.edges.filter((value) => value.id !== row.id)
}
const handleSubmit = async () => {
  if (isSubmitting.value) return
  if (!/^[A-Za-z0-9._-]{1,64}$/.test(draft.version)) {
    ElMessage.warning('请输入1至64位的新版本号，仅支持字母、数字、点、下划线和短横线')
    return
  }
  if (draft.version === props.graph.version) {
    ElMessage.warning('请使用不同于原版本的新版本号')
    return
  }
  if (!draft.title.trim()) {
    ElMessage.warning('请填写图谱标题')
    return
  }
  for (const rule of draft.rules) {
    const error = validateGraphRule(rule, draft.nodes, draft.edges)
    if (error) {
      ElMessage.warning(`${rule.id}: ${error}`)
      return
    }
  }
  isSubmitting.value = true
  emit('busy', true)
  try {
    const result = await importGraphVersion({ ...draft, title: draft.title.trim() })
    ElMessage.success('新版本草稿已保存，核查后可发布')
    emit('saved', result.data.data)
  } catch {
    /* Shared request layer reports errors; retain the entire editing copy for retry. */
  } finally {
    isSubmitting.value = false
    emit('busy', false)
  }
}
const handleCancel = async () => {
  try {
    await ElMessageBox.confirm('放弃当前编辑副本？尚未保存的修改将丢失。', '退出编辑', {
      confirmButtonText: '放弃修改',
      cancelButtonText: '继续编辑',
    })
  } catch {
    return
  }
  emit('cancel')
}
</script>

<template>
  <el-card class="graph-editor">
    <h2>编辑副本 · 基于 {{ graph.version }}</h2>
    <p>
      保存时创建待发布的新版本，原版本与历史报告保持不变。来源、节点、关系和多维规则均可在编辑副本中维护；修改引用结构前请先处理相关规则。
    </p>
    <el-form
      label-position="top"
      :disabled="isSubmitting"
      @submit.prevent="handleSubmit"
    >
      <div class="version-fields">
        <el-form-item label="新版本号">
          <el-input
            v-model="draft.version"
            maxlength="64"
            placeholder="例如 graph-v2"
          />
        </el-form-item>
        <el-form-item label="图谱标题">
          <el-input
            v-model="draft.title"
            maxlength="100"
          />
        </el-form-item>
      </div>
      <el-input
        v-model="keyword"
        placeholder="搜索节点或关系标识、名称、说明"
        clearable
      />
      <el-tabs v-model="tab">
        <el-tab-pane
          :label="`节点（${draft.nodes.length}）`"
          name="nodes"
        >
          <el-button
            :disabled="isSubmitting"
            @click="handleNode()"
          >
            新增节点
          </el-button>
          <el-table
            :data="filteredNodes"
            row-key="id"
            max-height="480"
          >
            <el-table-column
              prop="id"
              label="节点标识"
              min-width="120"
            />
            <el-table-column
              prop="label"
              label="名称"
              min-width="140"
            />
            <el-table-column
              label="类型"
              min-width="100"
            >
              <template #default="{ row }">{{ nodeTypes[row.type] || row.type }}</template>
            </el-table-column>
            <el-table-column
              label="来源"
              min-width="140"
            >
              <template #default="{ row }">{{ sourceLabel(row.sourceId) }}</template>
            </el-table-column>
            <el-table-column
              label="操作"
              width="150"
            >
              <template #default="{ row }">
                <el-button
                  :disabled="isSubmitting"
                  link
                  @click="handleNode(row as GraphNode)"
                >
                  编辑节点
                </el-button>
                <el-button
                  :disabled="isSubmitting"
                  link
                  type="danger"
                  @click="handleDelete(row as GraphNode, true)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane
          :label="`关系（${draft.edges.length}）`"
          name="edges"
        >
          <el-button
            :disabled="isSubmitting"
            @click="handleEdge()"
          >
            新增关系
          </el-button>
          <el-table
            :data="filteredEdges"
            row-key="id"
            max-height="480"
          >
            <el-table-column
              prop="id"
              label="关系标识"
              min-width="120"
            />
            <el-table-column
              label="起点 → 终点"
              min-width="220"
            >
              <template #default="{ row }">{{ label(row.from) }} → {{ label(row.to) }}</template>
            </el-table-column>
            <el-table-column
              label="关系"
              min-width="130"
            >
              <template #default="{ row }">{{ relations[row.relation] || row.relation }}</template>
            </el-table-column>
            <el-table-column
              label="操作"
              width="150"
            >
              <template #default="{ row }">
                <el-button
                  :disabled="isSubmitting"
                  link
                  @click="handleEdge(row as GraphEdge)"
                >
                  编辑关系
                </el-button>
                <el-button
                  :disabled="isSubmitting"
                  link
                  type="danger"
                  @click="handleDelete(row as GraphEdge, false)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane
          :label="`依据与规则（${draft.rules.length}）`"
          name="rules"
        >
          <GraphEvidenceEditor
            v-model:sources="draft.sources"
            v-model:rules="draft.rules"
            :nodes="draft.nodes"
            :edges="draft.edges"
            :disabled="isSubmitting"
          />
        </el-tab-pane>
      </el-tabs>
      <div class="editor-actions">
        <el-button
          :loading="isSubmitting"
          type="primary"
          native-type="submit"
        >
          保存为新版本草稿
        </el-button>
        <el-button
          :disabled="isSubmitting"
          @click="handleCancel"
        >
          退出编辑
        </el-button>
      </div>
    </el-form>
    <el-dialog
      v-model="isDialogOpen"
      :title="kind === 'node' ? '节点资料' : '关系资料'"
      width="min(620px, 94vw)"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-form
        label-position="top"
        @submit.prevent="handleSaveItem"
      >
        <template v-if="kind === 'node'">
          <el-form-item label="节点标识">
            <el-input
              v-model="nodeForm.id"
              :disabled="!!editingId"
              maxlength="40"
            />
          </el-form-item>
          <el-form-item label="节点名称">
            <el-input
              v-model="nodeForm.label"
              maxlength="100"
            />
          </el-form-item>
          <el-form-item label="节点类型">
            <el-select v-model="nodeForm.type">
              <el-option
                v-for="(text, value) in nodeTypes"
                :key="value"
                :value="value"
                :label="text"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="节点说明">
            <el-input
              v-model="nodeForm.description"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="节点来源">
            <el-select v-model="nodeForm.sourceId">
              <el-option
                v-for="source in draft.sources"
                :key="source.id"
                :value="source.id"
                :label="source.title"
              />
            </el-select>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="关系标识">
            <el-input
              v-model="edgeForm.id"
              :disabled="!!editingId"
              maxlength="40"
            />
          </el-form-item>
          <el-form-item label="起点节点">
            <el-select
              v-model="edgeForm.from"
              filterable
            >
              <el-option
                v-for="node in draft.nodes"
                :key="node.id"
                :value="node.id"
                :label="`${node.label} (${node.id})`"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="终点节点">
            <el-select
              v-model="edgeForm.to"
              filterable
            >
              <el-option
                v-for="node in draft.nodes"
                :key="node.id"
                :value="node.id"
                :label="`${node.label} (${node.id})`"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="关系类型">
            <el-select v-model="edgeForm.relation">
              <el-option
                v-for="(text, value) in relations"
                :key="value"
                :value="value"
                :label="text"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="关系说明">
            <el-input
              v-model="edgeForm.explanation"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="关系来源">
            <el-select v-model="edgeForm.sourceId">
              <el-option
                v-for="source in draft.sources"
                :key="source.id"
                :value="source.id"
                :label="source.title"
              />
            </el-select>
          </el-form-item>
        </template>
        <el-button
          type="primary"
          native-type="submit"
        >
          应用到编辑副本
        </el-button>
        <el-button @click="isDialogOpen = false">取消</el-button>
      </el-form>
    </el-dialog>
  </el-card>
</template>

<style scoped lang="scss">
.graph-editor {
  margin: 20px 0;
  border-radius: 14px;
  min-width: 0;
}
.version-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.version-fields :deep(.el-form-item) {
  flex: 1 1 240px;
}
.editor-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 20px;
}
p {
  line-height: 1.8;
}
pre {
  max-height: 400px;
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
