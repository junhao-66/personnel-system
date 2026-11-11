<script setup lang="ts">
// Edit an isolated copy; saving always creates a new, unpublished server version.
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { importAssessmentScale } from '@/api/assessment'
import type { AssessmentScaleVO, ScaleDefinitionVO, ScaleQuestionVO } from '@/types/assessment'

const props = defineProps<{ source?: AssessmentScaleVO }>()
const emit = defineEmits<{ saved: [value: AssessmentScaleVO]; close: [] }>()
type Metadata = Omit<ScaleDefinitionVO, 'questions'>
type EditableQuestion = ScaleQuestionVO & { editorId: number }
const fields: {
  key: Exclude<keyof Metadata, 'accessedDate' | 'scoringVersion'>
  label: string
  max: number
  multiline?: boolean
}[] = [
  { key: 'name', label: '量表名称', max: 100 },
  { key: 'version', label: '新版本号', max: 64 },
  { key: 'sourceTitle', label: '来源标题', max: 100 },
  { key: 'author', label: '作者或机构', max: 100 },
  { key: 'sourceUrl', label: '来源链接', max: 500 },
  { key: 'license', label: '使用许可', max: 100 },
  { key: 'licenseEvidence', label: '使用及分发授权依据', max: 1000, multiline: true },
  { key: 'chineseVersion', label: '中文译本版本', max: 200 },
  { key: 'translationEvidence', label: '中文译本许可及校核依据', max: 1000, multiline: true },
  { key: 'scoringEvidence', label: '评分规则核验依据', max: 1000, multiline: true },
  { key: 'retentionPolicy', label: '数据保存期限与处理规则', max: 500, multiline: true },
  { key: 'contact', label: '有效联系渠道', max: 200 },
]
const dimensions = ['EI', 'SN', 'TF', 'JP'] as const
const oejtsPoles = { EI: 'I', SN: 'N', TF: 'T', JP: 'P' } as const
const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date())
const empty: Metadata = {
  name: '',
  version: '',
  sourceTitle: '',
  author: '',
  sourceUrl: '',
  accessedDate: today,
  license: '',
  licenseEvidence: '',
  chineseVersion: '',
  translationEvidence: '',
  scoringVersion: 'WEIGHTED_BIPOLAR_1_5_V1',
  scoringEvidence: '',
  retentionPolicy: '',
  contact: '',
}
const source = props.source?.definition
const form = reactive<Metadata>(
  source
    ? (Object.fromEntries(
        Object.keys(empty).map((key) => [key, source[key as keyof Metadata]]),
      ) as unknown as Metadata)
    : { ...empty },
)
if (source) form.version = source.version.slice(0, 58) + '-copy'
let nextEditorId = 0
const rows = ref<EditableQuestion[]>(
  (source?.questions || []).map((question) => ({ ...question, editorId: ++nextEditorId })),
)
const questionForm = reactive<ScaleQuestionVO>({
  key: '',
  leftText: '',
  rightText: '',
  dimension: 'EI',
  highPole: 'E',
  weight: 1,
})
const editingId = ref<number>()
const keyword = ref('')
const dimensionFilter = ref('')
const isSaving = ref(false)
const isQuestionEditing = ref(false)
const isPreviewing = ref(false)
const baseline = JSON.stringify({ form, rows: rows.value })
const isDirty = computed(
  () => JSON.stringify({ form, rows: rows.value }) !== baseline || isQuestionEditing.value,
)
const isOejts = computed(() => form.scoringVersion === 'OEJTS_OPENJUNG_32_V1')
const filteredRows = computed(() =>
  rows.value.filter(
    (question) =>
      (!dimensionFilter.value || question.dimension === dimensionFilter.value) &&
      (!keyword.value ||
        `${question.key} ${question.leftText} ${question.rightText}`
          .toLowerCase()
          .includes(keyword.value.toLowerCase())),
  ),
)
const counts = computed(() =>
  dimensions
    .map(
      (dimension) =>
        `${dimension} ${rows.value.filter((row) => row.dimension === dimension).length}题`,
    )
    .join(' · '),
)
const payload = () => ({
  ...form,
  questions: rows.value.map(({ editorId: _editorId, ...question }) => ({ ...question })),
})
const preview = computed(() => JSON.stringify(payload(), null, 2))

const handleAddQuestion = () => {
  if (isSaving.value) return
  if (rows.value.length >= 128) {
    ElMessage.warning('每个版本最多128题')
    return
  }
  editingId.value = undefined
  Object.assign(questionForm, {
    key: '',
    leftText: '',
    rightText: '',
    dimension: 'EI',
    highPole: isOejts.value ? 'I' : 'E',
    weight: 1,
  })
  isQuestionEditing.value = true
}
const handleEditQuestion = (question: EditableQuestion) => {
  editingId.value = question.editorId
  Object.assign(questionForm, {
    key: question.key,
    leftText: question.leftText,
    rightText: question.rightText,
    dimension: question.dimension,
    highPole: question.highPole,
    weight: question.weight,
  })
  isQuestionEditing.value = true
}
const handleDimension = () => {
  questionForm.highPole = isOejts.value
    ? oejtsPoles[questionForm.dimension]
    : questionForm.dimension[0]
}
const handleApplyQuestion = () => {
  const value = {
    ...questionForm,
    key: questionForm.key.trim(),
    leftText: questionForm.leftText.trim(),
    rightText: questionForm.rightText.trim(),
  }
  if (!/^[A-Za-z0-9_-]{1,32}$/.test(value.key)) {
    ElMessage.warning('题目标识须为1至32位字母、数字、下划线或短横线')
    return
  }
  if (!value.leftText || !value.rightText) {
    ElMessage.warning('请填写题目左右端文字')
    return
  }
  if (rows.value.some((row) => row.key === value.key && row.editorId !== editingId.value)) {
    ElMessage.warning('题目标识不能重复')
    return
  }
  if (
    !Number.isInteger(value.weight) ||
    value.weight < 1 ||
    value.weight > 10 ||
    !value.dimension.includes(value.highPole)
  ) {
    ElMessage.warning('题目维度、方向或权重不合法')
    return
  }
  if (isOejts.value && (value.weight !== 1 || value.highPole !== oejtsPoles[value.dimension])) {
    ElMessage.warning('OpenJung版本要求各题权重1且5分方向为I/N/T/P')
    return
  }
  if (editingId.value === undefined) rows.value.push({ ...value, editorId: ++nextEditorId })
  else
    rows.value = rows.value.map((row) =>
      row.editorId === editingId.value ? { ...value, editorId: row.editorId } : row,
    )
  isQuestionEditing.value = false
}
const handleRemoveQuestion = async (question: EditableQuestion) => {
  try {
    await ElMessageBox.confirm(
      `从本地副本移除题目 ${question.key}？原版本、旧答卷和报告不受影响。`,
      '移除副本题目',
      { confirmButtonText: '移除题目', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  rows.value = rows.value.filter((row) => row.editorId !== question.editorId)
}
const validate = () => {
  for (const field of fields) if (!form[field.key]?.trim()) return `请填写${field.label}`
  if (!/^[A-Za-z0-9._-]{1,64}$/.test(form.version))
    return '新版本号须为1至64位字母、数字、点、下划线或短横线'
  if (source && form.name === source.name && form.version === source.version)
    return '请使用新版本号，不能覆盖原量表'
  try {
    const url = new URL(form.sourceUrl)
    if (
      !['http:', 'https:'].includes(url.protocol) ||
      !url.hostname ||
      url.username ||
      url.password
    )
      return '来源必须为有效的HTTP/HTTPS链接'
  } catch {
    return '来源必须为有效的HTTP/HTTPS链接'
  }
  if (!form.accessedDate || form.accessedDate > today) return '访问日期不能为空或晚于今天'
  if (
    rows.value.length < 8 ||
    rows.value.length > 128 ||
    dimensions.some(
      (dimension) => rows.value.filter((row) => row.dimension === dimension).length < 2,
    )
  )
    return '每个维度至少两题，总题量8至128题'
  if (
    isOejts.value &&
    (dimensions.some(
      (dimension) => rows.value.filter((row) => row.dimension === dimension).length !== 8,
    ) ||
      rows.value.some((row) => row.weight !== 1 || row.highPole !== oejtsPoles[row.dimension]))
  )
    return 'OpenJung版本要求每维8题、权重1且5分方向为I/N/T/P'
  return ''
}
const handleSave = async () => {
  if (isSaving.value) return
  if (isQuestionEditing.value) {
    ElMessage.warning('请先保存或取消正在编辑的题目')
    return
  }
  const message = validate()
  if (message) {
    ElMessage.warning(message)
    return
  }
  isSaving.value = true
  try {
    const response = await importAssessmentScale(payload())
    ElMessage.success('新版本已保存为待核验，原版本及历史报告未修改')
    emit('saved', response.data.data)
  } catch {
    /* Keep the entire local copy after backend rejection or a lost response. */
  } finally {
    isSaving.value = false
  }
}
const confirmClose = async () => {
  if (isSaving.value) return false
  if (!isDirty.value) return true
  try {
    await ElMessageBox.confirm('放弃未保存的题库编辑副本？', '退出题库编辑', {
      confirmButtonText: '放弃副本',
      cancelButtonText: '继续编辑',
    })
    return true
  } catch {
    return false
  }
}
const handleClose = async () => {
  if (await confirmClose()) emit('close')
}
defineExpose({ confirmClose })
</script>

<template>
  <section
    class="scale-editor"
    aria-label="题库表单编辑器"
  >
    <header class="editor-heading">
      <div>
        <h2>{{ source ? '复制量表并另存新版本' : '新建量表版本' }}</h2>
        <p v-if="source">复制自 {{ source.name }} · {{ source.version }}，不修改原版本。</p>
      </div>
      <el-button
        :disabled="isSaving"
        @click="handleClose"
      >
        退出题库编辑
      </el-button>
    </header>
    <el-alert
      title="保存仅创建待核验的新版本，须独立核验发布后才对员工开放。正式量表应保留原题完整性与评分依据。"
      type="info"
      :closable="false"
    />
    <el-form
      :model="form"
      :disabled="isSaving"
      label-position="top"
      @submit.prevent="handleSave"
    >
      <div class="metadata-grid">
        <el-form-item
          v-for="field in fields"
          :key="field.key"
          :label="field.label"
          :class="{ wide: field.multiline }"
        >
          <el-input
            v-model="form[field.key]"
            :type="field.multiline ? 'textarea' : 'text'"
            :rows="2"
            :maxlength="field.max"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="来源访问日期">
          <el-input
            v-model="form.accessedDate"
            type="date"
            :max="today"
          />
        </el-form-item>
        <el-form-item label="评分版本">
          <el-select v-model="form.scoringVersion">
            <el-option
              label="五级双极加权评分"
              value="WEIGHTED_BIPOLAR_1_5_V1"
            />
            <el-option
              label="OpenJung固定32题评分"
              value="OEJTS_OPENJUNG_32_V1"
            />
          </el-select>
        </el-form-item>
      </div>
      <h3>题目维护</h3>
      <p>共 {{ rows.length }} 题 · {{ counts }}</p>
      <p v-if="isOejts">
        OpenJung固定版本要求每维8题、完整32题、权重1、5分方向I/N/T/P，不可任意删改。
      </p>
      <div class="question-filters">
        <el-input
          v-model="keyword"
          placeholder="搜索题目标识或文字"
          clearable
        />
        <el-select
          v-model="dimensionFilter"
          placeholder="全部维度"
          clearable
        >
          <el-option
            v-for="dimension in dimensions"
            :key="dimension"
            :label="dimension"
            :value="dimension"
          />
        </el-select>
        <el-button
          :disabled="isQuestionEditing || rows.length >= 128"
          @click="handleAddQuestion"
        >
          新增题目
        </el-button>
      </div>
      <el-table
        :data="filteredRows"
        row-key="editorId"
        empty-text="暂无符合条件的题目"
        class="question-table"
      >
        <el-table-column
          prop="key"
          label="题目标识"
          min-width="110"
        />
        <el-table-column
          prop="leftText"
          label="左端文字"
          min-width="180"
        />
        <el-table-column
          prop="rightText"
          label="右端文字"
          min-width="180"
        />
        <el-table-column
          prop="dimension"
          label="维度"
          width="75"
        />
        <el-table-column
          prop="highPole"
          label="5分方向"
          width="90"
        />
        <el-table-column
          prop="weight"
          label="权重"
          width="70"
        />
        <el-table-column
          label="操作"
          width="155"
        >
          <template #default="{ row }">
            <el-button
              :disabled="isQuestionEditing || isSaving"
              link
              @click="handleEditQuestion(row as EditableQuestion)"
            >
              编辑题目
            </el-button>
            <el-button
              :disabled="isQuestionEditing || isSaving"
              link
              type="danger"
              @click="handleRemoveQuestion(row as EditableQuestion)"
            >
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <footer class="editor-actions">
        <el-button
          :disabled="isSaving"
          @click="isPreviewing = !isPreviewing"
        >
          {{ isPreviewing ? '收起版本预览' : '预览完整版本JSON' }}
        </el-button>
        <el-button
          :loading="isSaving"
          type="primary"
          native-type="submit"
        >
          保存为待核验新版本
        </el-button>
      </footer>
      <pre
        v-if="isPreviewing"
        class="json-preview"
        >{{ preview }}</pre
      >
    </el-form>
    <el-dialog
      v-model="isQuestionEditing"
      :title="editingId === undefined ? '新增题目' : '编辑题目'"
      width="min(620px, 94vw)"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <el-form
        :model="questionForm"
        label-position="top"
        @submit.prevent="handleApplyQuestion"
      >
        <el-form-item label="题目标识">
          <el-input
            v-model="questionForm.key"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item label="左端文字（选择1时）">
          <el-input
            v-model="questionForm.leftText"
            type="textarea"
            maxlength="300"
            :rows="2"
          />
        </el-form-item>
        <el-form-item label="右端文字（选择5时）">
          <el-input
            v-model="questionForm.rightText"
            type="textarea"
            maxlength="300"
            :rows="2"
          />
        </el-form-item>
        <div class="metadata-grid">
          <el-form-item label="题目维度">
            <el-select
              v-model="questionForm.dimension"
              @change="handleDimension"
            >
              <el-option
                v-for="dimension in dimensions"
                :key="dimension"
                :label="dimension"
                :value="dimension"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="5分偏向">
            <el-select v-model="questionForm.highPole">
              <el-option
                v-for="pole in questionForm.dimension.split('')"
                :key="pole"
                :label="pole"
                :value="pole"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="题目权重">
            <el-input-number
              v-model="questionForm.weight"
              :min="1"
              :max="10"
              :precision="0"
            />
          </el-form-item>
        </div>
        <el-button @click="isQuestionEditing = false">取消题目编辑</el-button>
        <el-button
          type="primary"
          native-type="submit"
        >
          保存题目到副本
        </el-button>
      </el-form>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.scale-editor {
  margin-top: 24px;
  padding: clamp(16px, 3vw, 28px);
  background: #ffffffb0;
  border: 1px solid var(--border-default);
  border-radius: 16px;
  min-width: 0;
  overflow-wrap: anywhere;
}
.editor-heading,
.editor-actions,
.question-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  justify-content: space-between;
}
.metadata-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
  margin-top: 20px;
}
.wide {
  grid-column: 1 / -1;
}
.question-filters {
  margin: 16px 0;
}
.question-filters > :first-child {
  flex: 1 1 200px;
}
.question-filters .el-select {
  width: 160px;
}
.metadata-grid .el-select {
  width: 100%;
}
.editor-actions {
  margin-top: 20px;
}
.json-preview {
  max-height: 380px;
  overflow: auto;
  padding: 16px;
  background: var(--bg-subtle);
  font-size: 13px;
}
h2,
h3 {
  color: var(--brand-600);
}
p {
  line-height: 1.8;
}
@media (max-width: 640px) {
  .metadata-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
