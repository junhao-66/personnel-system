<script setup lang="ts">
// Administrators import immutable versions and attest rights/translation/scoring before publication.
import { onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ScaleVersionEditor from './components/ScaleVersionEditor.vue'
import {
  archiveAssessmentScale,
  getAdminAssessmentScales,
  importAssessmentScale,
  publishAssessmentScale,
} from '@/api/assessment'
import type { AssessmentScaleVO, ScaleImportReqDTO, ScalePublishReqDTO } from '@/types/assessment'

const scales = ref<AssessmentScaleVO[]>([])
const isEditing = ref(false)
const editorSource = ref<AssessmentScaleVO>()
const editorRef = ref<InstanceType<typeof ScaleVersionEditor>>()
const editorKey = ref(0)
const handleEditVersion = async (row?: AssessmentScaleVO) => {
  if (isSubmitting.value) return
  if (isEditing.value && !(await editorRef.value?.confirmClose())) return
  editorSource.value = row
  editorKey.value++
  isEditing.value = true
}
const handleEditorSaved = async (value: AssessmentScaleVO) => {
  isEditing.value = false
  handleSelect(value)
  await getScales()
}
onBeforeRouteLeave(
  () =>
    !localStorage.getItem('accessToken') ||
    !isEditing.value ||
    editorRef.value?.confirmClose() ||
    false,
)
const importText = ref('')
const isLoading = ref(false)
const isSubmitting = ref(false)
const isLoadFailed = ref(false)
const selected = ref<AssessmentScaleVO>()
const verification = reactive<ScalePublishReqDTO>({
  commercialUseVerified: false,
  translationVerified: false,
  scoringVerified: false,
})
const statusLabels = { DRAFT: '待核验', PUBLISHED: '已发布', ARCHIVED: '已停用' }
const schemaExample = JSON.stringify(
  {
    name: '实际量表名称',
    version: 'zh-1.0',
    sourceTitle: '原始来源标题',
    author: '作者或机构',
    sourceUrl: 'https://example.org/actual-source',
    accessedDate: '2026-10-29',
    license: '实际使用许可',
    licenseEvidence: '企业使用与分发授权的具体依据，不能仅填写免费或开源',
    chineseVersion: '中文译本名称与版本',
    translationEvidence: '中文译本使用许可和校核依据',
    scoringVersion: 'WEIGHTED_BIPOLAR_1_5_V1',
    scoringEvidence: '原量表评分规则与本算法一致的核验记录',
    retentionPolicy: '经企业确认的保存期限及处理规则',
    contact: '有效联系渠道',
    questions: [
      {
        key: 'q1',
        leftText: '题目左端原文',
        rightText: '题目右端原文',
        dimension: 'EI',
        highPole: 'E',
        weight: 1,
      },
    ],
  },
  null,
  2,
)
const getScales = async () => {
  isLoading.value = true
  isLoadFailed.value = false
  try {
    scales.value = (await getAdminAssessmentScales()).data.data
  } catch {
    isLoadFailed.value = true
    scales.value = []
  } finally {
    isLoading.value = false
  }
}
const handleImport = async () => {
  if (isSubmitting.value || !importText.value.trim()) return
  let data: unknown
  try {
    data = JSON.parse(importText.value)
  } catch {
    ElMessage.warning('JSON格式不正确，请检查逗号、引号及括号')
    return
  }
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    ElMessage.warning('量表必须为JSON对象')
    return
  }
  isSubmitting.value = true
  try {
    await importAssessmentScale(data as ScaleImportReqDTO)
    importText.value = ''
    ElMessage.success('量表新版本已导入，核验发布前员工不可使用')
    showImport.value = false
    await getScales()
  } catch {
    /* Server validates every metadata field, question, scoring direction and version. */
  } finally {
    isSubmitting.value = false
  }
}
const handleSelect = (row: AssessmentScaleVO) => {
  selected.value = row
  Object.assign(verification, {
    commercialUseVerified: false,
    translationVerified: false,
    scoringVerified: false,
  })
}
const handlePublish = async () => {
  if (!selected.value || isSubmitting.value) return
  isSubmitting.value = true
  try {
    try {
      await ElMessageBox.confirm(
        '确认已依据原始资料核验企业使用许可、中文译本与评分规则？发布后此版本不可覆盖，内容变更须导入新版本。',
        '发布量表',
        { confirmButtonText: '确认发布', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    await publishAssessmentScale(selected.value.id, { ...verification })
    selected.value = undefined
    ElMessage.success('量表已发布')
    await getScales()
  } catch {
    /* Retain evidence panel for review on failure. */
  } finally {
    isSubmitting.value = false
  }
}
const handleArchive = async (row: AssessmentScaleVO) => {
  if (isSubmitting.value) return
  isSubmitting.value = true
  try {
    try {
      await ElMessageBox.confirm(
        '停用后不再允许新建该版本测评。已有答卷继续使用当时的题目及评分快照，历史报告保留。',
        '停用量表',
        { confirmButtonText: '确认停用', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    await archiveAssessmentScale(row.id)
    selected.value = undefined
    ElMessage.success('量表已停用')
    await getScales()
  } catch {
    /* Request layer displays errors. */
  } finally {
    isSubmitting.value = false
  }
}
const showImport = ref(false)
onMounted(getScales)
</script>

<template>
  <main class="scale-page">
    <nav
      class="section-navigation"
      aria-label="题库管理"
    >
      <button
        type="button"
        :aria-pressed="!showImport"
        @click="showImport = false"
      >
        量表版本
      </button>
      <button
        type="button"
        :aria-pressed="showImport"
        @click="showImport = true"
      >
        导入量表
      </button>
    </nav>
    <header class="page-heading">
      <div>
        <h1>量表版本与题库</h1>
        <p>
          来源、企业使用许可、中文版本和评分规则须逐项核验。内容变更创建新版本，保护旧答卷及报告。
        </p>
      </div>
      <el-button
        :loading="isLoading"
        :disabled="isSubmitting"
        @click="getScales"
      >
        刷新量表
      </el-button>
    </header>
    <el-button
      :disabled="isSubmitting"
      @click="handleEditVersion()"
    >
      表单新建量表
    </el-button>
    <ScaleVersionEditor
      v-if="isEditing"
      :key="editorKey"
      ref="editorRef"
      :source="editorSource"
      @saved="handleEditorSaved"
      @close="isEditing = false"
    />
    <el-card
      v-if="showImport && !isEditing"
      class="panel"
    >
      <h2>导入新版本</h2>
      <el-alert
        title="已内置16题毕设演示版，可直接体验完整流程。演示题未经信效度验证；正式量表仍需核验许可、中文版本和评分规则后导入发布。"
        type="info"
        :closable="false"
      />
      <p>
        仅支持已确认适用五级双极题算法的量表；每个维度至少两题，共8至128题。所有题目保留原量表完整性，不应为凑题量随意删改。
      </p>
      <details>
        <summary>查看JSON结构与评分约定（结构示例，不是正式题库）</summary>
        <pre>{{ schemaExample }}</pre>
        <p>
          答案1对应左端、5对应右端；highPole表示选择5时偏向的字母。反向题按6－答案转换，乘weight后汇总。每维归一化至0～100；恰好相同按E/S/T/J编码并标注均衡，距中点不超过5个百分点标注倾向较弱。仅导入与该规则一致且已核验的量表。
        </p>
      </details>
      <el-input
        v-model="importText"
        type="textarea"
        :rows="5"
        :disabled="isSubmitting"
        maxlength="250000"
        placeholder="粘贴包含来源许可、中文版本、评分依据及全部题目的量表JSON"
      />
      <el-button
        type="primary"
        :loading="isSubmitting"
        :disabled="!importText.trim()"
        @click="handleImport"
        class="import-button"
      >
        导入量表版本
      </el-button>
    </el-card>
    <el-card
      v-if="!showImport && !isEditing && !selected"
      class="panel"
    >
      <el-alert
        v-if="isLoadFailed"
        title="量表加载失败，请刷新重试"
        type="error"
        :closable="false"
      />
      <el-table
        v-loading="isLoading"
        :data="scales"
        row-key="id"
        empty-text="暂无量表版本"
      >
        <el-table-column
          label="名称"
          min-width="180"
        >
          <template #default="scope">{{ scope.row.definition.name }}</template>
        </el-table-column>
        <el-table-column
          label="版本"
          min-width="110"
        >
          <template #default="scope">{{ scope.row.definition.version }}</template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="100"
        >
          <template #default="scope">
            {{ statusLabels[scope.row.status as keyof typeof statusLabels] }}
          </template>
        </el-table-column>
        <el-table-column
          label="许可"
          min-width="150"
        >
          <template #default="scope">{{ scope.row.definition.license }}</template>
        </el-table-column>
        <el-table-column
          label="操作"
          min-width="290"
        >
          <template #default="scope">
            <el-button
              :disabled="isSubmitting"
              link
              type="primary"
              @click="handleSelect(scope.row as AssessmentScaleVO)"
            >
              查看与核验
            </el-button>
            <el-button
              :disabled="isSubmitting"
              link
              type="primary"
              @click="handleEditVersion(scope.row as AssessmentScaleVO)"
            >
              复制并编辑
            </el-button>
            <el-button
              v-if="scope.row.status === 'PUBLISHED'"
              :disabled="isSubmitting"
              link
              type="danger"
              @click="handleArchive(scope.row as AssessmentScaleVO)"
            >
              停用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-card
      v-if="selected"
      class="panel evidence-panel"
    >
      <el-button @click="selected = undefined">← 返回量表版本</el-button>
      <h2>{{ selected.definition.name }} · {{ selected.definition.version }}</h2>
      <p>
        原始来源：
        <a
          :href="selected.definition.sourceUrl"
          target="_blank"
          rel="noopener noreferrer"
        >
          {{ selected.definition.sourceTitle }}
        </a>
        ；{{ selected.definition.author }}；{{ selected.definition.accessedDate }}
      </p>
      <p>
        使用许可：{{ selected.definition.license }}；依据：{{ selected.definition.licenseEvidence }}
      </p>
      <p>
        中文版本：{{ selected.definition.chineseVersion }}；依据：{{
          selected.definition.translationEvidence
        }}
      </p>
      <p>
        评分版本：{{ selected.definition.scoringVersion }}；依据：{{
          selected.definition.scoringEvidence
        }}
      </p>
      <p>
        保存期限：{{ selected.definition.retentionPolicy }}；联系渠道：{{
          selected.definition.contact
        }}
      </p>
      <p>
        题目数：{{ selected.definition.questions.length }}；内容摘要：{{ selected.contentHash }}
      </p>
      <details>
        <summary>检查全部题目与评分方向</summary>
        <ol>
          <li
            v-for="question in selected.definition.questions"
            :key="question.key"
          >
            {{ question.key }}：{{ question.leftText }} ↔ {{ question.rightText }}；{{
              question.dimension
            }}；5分偏向{{ question.highPole }}；权重{{ question.weight }}
          </li>
        </ol>
      </details>
      <template v-if="selected.status === 'DRAFT'">
        <el-checkbox
          v-model="verification.commercialUseVerified"
          :disabled="isSubmitting"
        >
          已核验企业使用及分发授权
        </el-checkbox>
        <el-checkbox
          v-model="verification.translationVerified"
          :disabled="isSubmitting"
        >
          已核验中文译本许可及完整性
        </el-checkbox>
        <el-checkbox
          v-model="verification.scoringVerified"
          :disabled="isSubmitting"
        >
          已核验全部题目、方向、权重及评分规则与本算法一致
        </el-checkbox>
        <el-button
          type="primary"
          :loading="isSubmitting"
          :disabled="
            !verification.commercialUseVerified ||
            !verification.translationVerified ||
            !verification.scoringVerified
          "
          @click="handlePublish"
        >
          核验并发布
        </el-button>
      </template>
    </el-card>
  </main>
</template>

<style scoped lang="scss">
.scale-page {
  max-width: 1200px;
  margin: auto;
  padding: clamp(16px, 4vw, 48px);
  color: var(--text-strong);
}
.page-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
h1,
h2 {
  color: var(--brand-600);
}
.panel {
  margin-top: 24px;
  border-radius: 20px;
  overflow-wrap: anywhere;
}
p,
li {
  line-height: 1.8;
}
details {
  margin: 20px 0;
}
pre {
  overflow-x: auto;
  padding: 12px;
  background: rgb(255 255 255 / 70%);
}
.import-button {
  margin-top: 16px;
}
.evidence-panel :deep(.el-checkbox) {
  display: flex;
  height: auto;
  margin: 16px 0;
  white-space: normal;
}
.evidence-panel :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.8;
}
</style>
