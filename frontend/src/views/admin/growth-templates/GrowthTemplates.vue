<script setup lang="ts">
// Versioned general guidance; graph evidence and saved reports remain independent.
import { nextTick, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  archiveGrowthTemplate,
  createGrowthTemplate,
  getEffectiveGrowthTemplate,
  getGrowthTemplates,
  publishGrowthTemplate,
} from '@/api/growthTemplate'
import type { GrowthTemplateCreateReqDTO, GrowthTemplateVO } from '@/types/growthTemplate'

const fields: {
  key: keyof GrowthTemplateCreateReqDTO
  label: string
  max: number
  rows: number
}[] = [
  { key: 'version', label: '新版本号', max: 64, rows: 1 },
  { key: 'title', label: '模板标题', max: 100, rows: 1 },
  { key: 'suggestedCycle', label: '建议周期', max: 500, rows: 3 },
  { key: 'observationTarget', label: '观察目标', max: 1000, rows: 3 },
  { key: 'reviewPrompt', label: '复盘提示', max: 1000, rows: 3 },
  { key: 'fallbackContext', label: '无匹配规则时的通用情境', max: 200, rows: 2 },
  { key: 'fallbackAction', label: '无匹配规则时的通用行动', max: 1000, rows: 3 },
]
const versions = ref<GrowthTemplateVO[]>([])
const effective = ref<GrowthTemplateVO>()
const selected = ref<GrowthTemplateVO>()
const isLoading = ref(false)
const isLoadFailed = ref(false)
const isSubmitting = ref(false)
const isDialogOpen = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<GrowthTemplateCreateReqDTO>({
  version: '',
  title: '',
  suggestedCycle: '',
  observationTarget: '',
  reviewPrompt: '',
  fallbackContext: '',
  fallbackAction: '',
})
const rules: FormRules = Object.fromEntries(
  fields.map((field) => [
    field.key,
    [
      { required: true, whitespace: true, message: `请填写${field.label}`, trigger: 'blur' },
      { max: field.max, message: `最多${field.max}字`, trigger: 'blur' },
    ],
  ]),
)
rules.version = [
  {
    required: true,
    pattern: /^[A-Za-z0-9._-]{1,64}$/,
    message: '版本号仅支持1至64位字母、数字、点、下划线和短横线',
    trigger: 'blur',
  },
]
const statusLabels = { DRAFT: '待发布', PUBLISHED: '已发布', ARCHIVED: '已停用' }
const getData = async () => {
  isLoading.value = true
  isLoadFailed.value = false
  try {
    const [list, current] = await Promise.all([getGrowthTemplates(), getEffectiveGrowthTemplate()])
    versions.value = list.data.data
    effective.value = current.data.data
  } catch {
    versions.value = []
    effective.value = undefined
    isLoadFailed.value = true
  } finally {
    isLoading.value = false
  }
}
const handleCreate = async (source?: GrowthTemplateVO) => {
  const value = source || effective.value
  if (!value) return
  for (const field of fields) form[field.key] = field.key === 'version' ? '' : value[field.key]
  isDialogOpen.value = true
  await nextTick()
  formRef.value?.clearValidate()
}
const handleClose = async () => {
  if (isSubmitting.value) return
  try {
    await ElMessageBox.confirm('放弃尚未保存的模板修改？', '退出编辑', {
      confirmButtonText: '放弃修改',
      cancelButtonText: '继续编辑',
    })
  } catch {
    return
  }
  isDialogOpen.value = false
}
const handleSubmit = async () => {
  if (isSubmitting.value || !(await formRef.value?.validate().catch(() => false))) return
  if (
    form.version.toUpperCase() === 'BUILTIN_V1' ||
    versions.value.some((row) => row.version.toLowerCase() === form.version.toLowerCase())
  ) {
    ElMessage.warning('版本号已存在或为内置版本，请使用新版本号')
    return
  }
  isSubmitting.value = true
  try {
    const response = await createGrowthTemplate({ ...form })
    selected.value = response.data.data
    isDialogOpen.value = false
    ElMessage.success('建议模板草稿已保存，请核查后发布')
    await getData()
  } catch {
    /* Shared request errors; retain form for manual retry. */
  } finally {
    isSubmitting.value = false
  }
}
const handleStatus = async (row: GrowthTemplateVO, publish: boolean) => {
  if (row.id === null || isSubmitting.value) return
  try {
    await ElMessageBox.confirm(
      publish
        ? '确认发布？新报告采用导入编号最新的已发布模板，历史报告保持原文。模板不改变图谱命中规则或科学依据。'
        : '确认停用？新报告改用其余已发布模板中编号最新的一份；没有可用版本时回到内置模板。历史报告不受影响。',
      publish ? '发布建议模板' : '停用建议模板',
      { confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  isSubmitting.value = true
  try {
    if (publish) await publishGrowthTemplate(row.id)
    else await archiveGrowthTemplate(row.id)
    selected.value = undefined
    await getData()
  } catch {
    /* Shared request errors. */
  } finally {
    isSubmitting.value = false
  }
}
onBeforeRouteLeave(async () => {
  if (isSubmitting.value) return false
  if (!isDialogOpen.value) return true
  await handleClose()
  return !isDialogOpen.value
})
onMounted(getData)
</script>

<template>
  <main class="template-page">
    <header>
      <h1>建议模板管理</h1>
      <div>
        <el-button
          :loading="isLoading"
          :disabled="isSubmitting"
          @click="getData"
        >
          刷新模板
        </el-button>
        <el-button
          :disabled="isLoading || isLoadFailed || isSubmitting"
          type="primary"
          @click="handleCreate()"
        >
          新建模板版本
        </el-button>
      </div>
    </header>
    <el-alert
      title="模板仅维护周期、观察、复盘与无图谱匹配时的通用建议。情境、行动及来源依据仍由图谱提供，不代表能力评价或已验证的干预效果。"
      type="info"
      :closable="false"
    />
    <el-alert
      v-if="isLoadFailed"
      title="模板加载失败，请刷新重试"
      type="error"
      :closable="false"
    />
    <el-card
      v-if="effective && !selected"
      class="panel"
    >
      <h2>当前生效：{{ effective.title }} · {{ effective.version }}</h2>
      <p>
        {{
          effective.id === null
            ? '当前使用系统内置模板。'
            : '新生成报告将保存此模板版本与内容摘要。'
        }}
        历史报告不补算。
      </p>
      <el-button @click="selected = effective">预览当前模板</el-button>
    </el-card>
    <el-card
      v-if="!selected"
      class="panel"
    >
      <el-table
        v-loading="isLoading"
        :data="versions"
        row-key="id"
        empty-text="暂无自建模板，可基于内置模板创建新版本"
      >
        <el-table-column
          prop="version"
          label="版本"
          min-width="140"
        />
        <el-table-column
          prop="title"
          label="标题"
          min-width="160"
        />
        <el-table-column
          label="状态"
          min-width="100"
        >
          <template #default="{ row }">
            {{ statusLabels[(row as GrowthTemplateVO).status] }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          min-width="260"
        >
          <template #default="{ row }">
            <el-button
              link
              @click="selected = row as GrowthTemplateVO"
            >
              预览
            </el-button>
            <el-button
              :disabled="isSubmitting"
              link
              @click="handleCreate(row as GrowthTemplateVO)"
            >
              复制为新版本
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              :disabled="isSubmitting"
              link
              type="primary"
              @click="handleStatus(row as GrowthTemplateVO, true)"
            >
              发布
            </el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              :disabled="isSubmitting"
              link
              type="danger"
              @click="handleStatus(row as GrowthTemplateVO, false)"
            >
              停用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-card
      v-if="selected"
      class="panel preview"
    >
      <el-button @click="selected = undefined">← 返回模板列表</el-button>
      <h2>模板预览 · {{ selected.version }}</h2>
      <dl>
        <template
          v-for="field in fields"
          :key="field.key"
        >
          <dt>{{ field.key === 'version' ? '版本号' : field.label }}</dt>
          <dd>{{ selected[field.key] }}</dd>
        </template>
      </dl>
      <p v-if="selected.contentHash">内容摘要：{{ selected.contentHash }}</p>
      <p>
        固定说明：本项目通用行动模板，不是量表作者结论，也不代表干预效果已经验证。命中图谱规则时不会使用上述通用情境和通用行动替换规则内容。
      </p>
    </el-card>
    <el-dialog
      v-model="isDialogOpen"
      title="新建建议模板版本"
      width="min(720px, 94vw)"
      :before-close="handleClose"
      :close-on-click-modal="false"
      :close-on-press-escape="!isSubmitting"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        :disabled="isSubmitting"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item
          v-for="field in fields"
          :key="field.key"
          :label="field.label"
          :prop="field.key"
        >
          <el-input
            v-model="form[field.key]"
            :aria-label="field.label"
            :type="field.rows > 1 ? 'textarea' : 'text'"
            :rows="field.rows"
            :maxlength="field.max"
            show-word-limit
          />
        </el-form-item>
        <p>保存为新草稿，不覆盖旧版本。内容不得将偏好描述为能力、绩效或因果结论。</p>
        <el-button
          :loading="isSubmitting"
          type="primary"
          native-type="submit"
        >
          保存模板草稿
        </el-button>
        <el-button
          :disabled="isSubmitting"
          @click="handleClose"
        >
          取消编辑
        </el-button>
      </el-form>
    </el-dialog>
  </main>
</template>

<style scoped lang="scss">
.template-page {
  max-width: 1280px;
  margin: auto;
  padding: clamp(16px, 4vw, 48px);
  overflow-wrap: anywhere;
}
header {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}
h1,
h2 {
  color: var(--brand-600);
}
.panel {
  margin: 20px 0;
  border-radius: 14px;
}
p,
dd {
  line-height: 1.8;
  white-space: pre-wrap;
}
dt {
  font-weight: 600;
}
dd {
  margin: 8px 0 18px;
}
</style>
