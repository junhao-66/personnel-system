<script setup lang="ts">
// Source and rule dialogs edit copies; changes are emitted to the enclosing version draft.
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { GraphEdge, GraphNode, GraphRule, GraphSource } from '@/types/knowledgeGraph'
import { validateGraphRule } from './graphRuleValidation'

const props = defineProps<{
  sources: GraphSource[]
  rules: GraphRule[]
  nodes: GraphNode[]
  edges: GraphEdge[]
  disabled: boolean
}>()
const emit = defineEmits<{ 'update:sources': [GraphSource[]]; 'update:rules': [GraphRule[]] }>()
const dialog = ref<'source' | 'rule' | ''>('')
const editingId = ref('')
const source = reactive<GraphSource>({
  id: '',
  title: '',
  author: '',
  url: '',
  accessedDate: '',
  license: '',
  attribution: '',
  contentVersion: '',
  evidenceLevel: 'PROJECT_DEMO',
})
const rule = reactive<GraphRule>({
  id: '',
  actionId: '',
  contextId: '',
  explanation: '',
  conditions: [],
  edgeIds: [],
})
const actions = computed(() => props.nodes.filter((n) => n.type === 'ACTION'))
const contexts = computed(() => props.nodes.filter((n) => n.type === 'CONTEXT'))
const today = () => {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
const label = (id: string) => props.nodes.find((n) => n.id === id)?.label || id
const handleSource = (value?: GraphSource) => {
  editingId.value = value?.id || ''
  Object.assign(
    source,
    value || {
      id: '',
      title: '',
      author: '',
      url: '',
      accessedDate: today(),
      license: '',
      attribution: '',
      contentVersion: '',
      evidenceLevel: 'PROJECT_DEMO',
    },
  )
  dialog.value = 'source'
}
const handleRule = (value?: GraphRule) => {
  editingId.value = value?.id || ''
  Object.assign(
    rule,
    JSON.parse(
      JSON.stringify(
        value || {
          id: '',
          actionId: '',
          contextId: '',
          explanation: '',
          conditions: [
            { dimension: 'EI', pole: 'E', nodeId: 'E', minimumPercent: 56 },
            { dimension: 'SN', pole: 'S', nodeId: 'S', minimumPercent: 56 },
          ],
          edgeIds: [],
        },
      ),
    ),
  )
  dialog.value = 'rule'
}
const handleAddCondition = () => {
  const dimension = ['EI', 'SN', 'TF', 'JP'].find(
    (d) => !rule.conditions.some((c) => c.dimension === d),
  )
  if (dimension)
    rule.conditions.push({
      dimension,
      pole: dimension[0]!,
      nodeId: dimension[0]!,
      minimumPercent: 56,
    })
}
const handleDimension = (index: number) => {
  const condition = rule.conditions[index]!
  condition.pole = condition.dimension[0]!
  condition.nodeId = condition.pole
}
const handleSave = () => {
  if (props.disabled) return
  if (dialog.value === 'source') {
    if (!/^[A-Za-z0-9_-]{1,40}$/.test(source.id)) {
      ElMessage.warning('来源标识须为1至40位字母、数字、下划线或短横线')
      return
    }
    if (!editingId.value && props.sources.some((s) => s.id === source.id)) {
      ElMessage.warning('来源标识已存在')
      return
    }
    if (!editingId.value && props.sources.length >= 32) {
      ElMessage.warning('每个版本最多32个来源')
      return
    }
    const limits = {
      title: 200,
      author: 100,
      url: 500,
      license: 200,
      attribution: 1000,
      contentVersion: 100,
    } as const
    for (const key of Object.keys(limits) as (keyof typeof limits)[]) {
      if (!source[key].trim() || source[key].length > limits[key]) {
        ElMessage.warning('请完整填写来源资料并遵守长度限制')
        return
      }
    }
    try {
      const url = new URL(source.url)
      if (
        !['http:', 'https:'].includes(url.protocol) ||
        !url.hostname ||
        url.username ||
        url.password
      )
        throw new Error()
    } catch {
      ElMessage.warning('来源链接须为不含账号密码的HTTP或HTTPS地址')
      return
    }
    if (
      !/^\d{4}-\d{2}-\d{2}$/.test(source.accessedDate) ||
      Number.isNaN(Date.parse(source.accessedDate)) ||
      source.accessedDate > today()
    ) {
      ElMessage.warning('访问日期须为今天或以前的有效日期')
      return
    }
    const copy = { ...source }
    emit(
      'update:sources',
      editingId.value
        ? props.sources.map((s) => (s.id === editingId.value ? copy : s))
        : [...props.sources, copy],
    )
  } else {
    if (!editingId.value && props.rules.some((r) => r.id === rule.id)) {
      ElMessage.warning('规则标识已存在')
      return
    }
    if (!editingId.value && props.rules.length >= 32) {
      ElMessage.warning('每个版本最多32条规则')
      return
    }
    const error = validateGraphRule(rule, props.nodes, props.edges)
    if (error) {
      ElMessage.warning(error)
      return
    }
    const copy: GraphRule = JSON.parse(JSON.stringify(rule))
    emit(
      'update:rules',
      editingId.value
        ? props.rules.map((r) => (r.id === editingId.value ? copy : r))
        : [...props.rules, copy],
    )
  }
  dialog.value = ''
}
const handleDelete = async (id: string, isSource: boolean) => {
  if (props.disabled) return
  if (
    isSource &&
    (props.nodes.some((n) => n.sourceId === id) || props.edges.some((e) => e.sourceId === id))
  ) {
    ElMessage.warning('来源仍被节点或关系引用，请先处理引用')
    return
  }
  if (isSource && props.sources.length <= 1) {
    ElMessage.warning('至少保留一个来源')
    return
  }
  try {
    await ElMessageBox.confirm(`确认从编辑副本删除 ${id}？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  if (isSource)
    emit(
      'update:sources',
      props.sources.filter((s) => s.id !== id),
    )
  else
    emit(
      'update:rules',
      props.rules.filter((r) => r.id !== id),
    )
}
</script>

<template>
  <div class="evidence-editor">
    <p>
      条件同时满足才命中规则；每个偏好条件及情境须沿所选有向关系到达行动。项目演示关系不代表因果、能力或绩效结论。
    </p>
    <h3>来源（{{ sources.length }}）</h3>
    <el-button
      :disabled="disabled"
      @click="handleSource()"
    >
      新增来源
    </el-button>
    <el-table
      :data="sources"
      row-key="id"
      max-height="320"
    >
      <el-table-column
        prop="id"
        label="来源标识"
        min-width="140"
      />
      <el-table-column
        prop="title"
        label="标题"
        min-width="180"
      />
      <el-table-column
        label="证据级别"
        min-width="120"
      >
        <template #default="{ row }">
          {{ row.evidenceLevel === 'PROJECT_DEMO' ? '项目演示' : '整理参考' }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="160"
      >
        <template #default="{ row }">
          <el-button
            :disabled="disabled"
            link
            @click="handleSource(row as GraphSource)"
          >
            编辑来源
          </el-button>
          <el-button
            :disabled="disabled"
            link
            type="danger"
            @click="handleDelete(row.id, true)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <h3>多维规则（{{ rules.length }}）</h3>
    <el-button
      :disabled="disabled"
      @click="handleRule()"
    >
      新增规则
    </el-button>
    <el-table
      :data="rules"
      row-key="id"
      max-height="400"
    >
      <el-table-column
        prop="id"
        label="规则标识"
        min-width="160"
      />
      <el-table-column
        label="行动 / 情境"
        min-width="200"
      >
        <template #default="{ row }">
          {{ label(row.actionId) }} / {{ label(row.contextId) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="explanation"
        label="说明"
        min-width="220"
      />
      <el-table-column
        label="操作"
        width="160"
      >
        <template #default="{ row }">
          <el-button
            :disabled="disabled"
            link
            @click="handleRule(row as GraphRule)"
          >
            编辑规则
          </el-button>
          <el-button
            :disabled="disabled"
            link
            type="danger"
            @click="handleDelete(row.id, false)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog
      :model-value="!!dialog"
      :title="dialog === 'source' ? '来源资料' : '多维规则资料'"
      width="min(760px, 94vw)"
      append-to-body
      :close-on-click-modal="false"
      @close="dialog = ''"
    >
      <el-form
        label-position="top"
        :disabled="disabled"
        @submit.prevent="handleSave"
      >
        <template v-if="dialog === 'source'">
          <el-form-item label="来源标识">
            <el-input
              v-model="source.id"
              :disabled="!!editingId"
              maxlength="40"
            />
          </el-form-item>
          <el-form-item label="来源标题">
            <el-input
              v-model="source.title"
              maxlength="200"
            />
          </el-form-item>
          <el-form-item label="作者或机构">
            <el-input
              v-model="source.author"
              maxlength="100"
            />
          </el-form-item>
          <el-form-item label="来源链接">
            <el-input
              v-model="source.url"
              maxlength="500"
            />
          </el-form-item>
          <el-form-item label="访问日期">
            <el-date-picker
              v-model="source.accessedDate"
              type="date"
              value-format="YYYY-MM-DD"
              :disabled-date="(date: Date) => date > new Date()"
            />
          </el-form-item>
          <el-form-item label="许可说明">
            <el-input
              v-model="source.license"
              maxlength="200"
            />
          </el-form-item>
          <el-form-item label="归属与使用说明">
            <el-input
              v-model="source.attribution"
              type="textarea"
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="内容版本">
            <el-input
              v-model="source.contentVersion"
              maxlength="100"
            />
          </el-form-item>
          <el-form-item label="证据级别">
            <el-select v-model="source.evidenceLevel">
              <el-option
                value="PROJECT_DEMO"
                label="项目演示"
              />
              <el-option
                value="CURATED_REFERENCE"
                label="整理参考"
              />
            </el-select>
          </el-form-item>
          <p>整理参考仅表示整理了来源，不表示效果已获科学验证。请核对许可与署名。</p>
        </template>
        <template v-else>
          <el-form-item label="规则标识">
            <el-input
              v-model="rule.id"
              :disabled="!!editingId"
              maxlength="40"
            />
          </el-form-item>
          <el-form-item label="行动节点">
            <el-select
              v-model="rule.actionId"
              filterable
            >
              <el-option
                v-for="node in actions"
                :key="node.id"
                :value="node.id"
                :label="`${node.label} (${node.id})`"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="情境节点">
            <el-select
              v-model="rule.contextId"
              filterable
            >
              <el-option
                v-for="node in contexts"
                :key="node.id"
                :value="node.id"
                :label="`${node.label} (${node.id})`"
              />
            </el-select>
          </el-form-item>
          <div
            v-for="(condition, index) in rule.conditions"
            :key="index"
            class="condition-row"
          >
            <el-form-item :label="`条件${index + 1}维度`">
              <el-select
                v-model="condition.dimension"
                @change="handleDimension(index)"
              >
                <el-option
                  v-for="dimension in ['EI', 'SN', 'TF', 'JP']"
                  :key="dimension"
                  :value="dimension"
                  :label="dimension"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="偏好">
              <el-select
                v-model="condition.pole"
                @change="condition.nodeId = condition.pole"
              >
                <el-option
                  v-for="pole in condition.dimension.split('')"
                  :key="pole"
                  :value="pole"
                  :label="pole"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="最低百分比">
              <el-input-number
                v-model="condition.minimumPercent"
                :min="56"
                :max="100"
                :precision="0"
              />
            </el-form-item>
            <el-button
              :disabled="rule.conditions.length <= 2"
              @click="rule.conditions.splice(index, 1)"
            >
              移除条件
            </el-button>
          </div>
          <el-button
            :disabled="rule.conditions.length >= 4"
            @click="handleAddCondition"
          >
            添加条件
          </el-button>
          <el-form-item label="路径关系（2至32条）">
            <el-select
              v-model="rule.edgeIds"
              multiple
              filterable
              :multiple-limit="32"
              collapse-tags
              collapse-tags-tooltip
            >
              <el-option
                v-for="edge in edges"
                :key="edge.id"
                :value="edge.id"
                :label="`${edge.id}: ${label(edge.from)} → ${label(edge.to)}`"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="规则说明">
            <el-input
              v-model="rule.explanation"
              type="textarea"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </template>
        <el-button
          type="primary"
          native-type="submit"
        >
          应用资料到副本
        </el-button>
        <el-button @click="dialog = ''">取消</el-button>
      </el-form>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.evidence-editor {
  min-width: 0;
}
.condition-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
}
.condition-row :deep(.el-select) {
  width: 100px;
}
p {
  line-height: 1.8;
}
</style>
