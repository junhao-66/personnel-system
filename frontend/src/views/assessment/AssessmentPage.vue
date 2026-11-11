<script setup lang="ts">
// Employee assessment: server drafts, serialized autosave, explicit consent and immutable results.
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AssessmentReport from './components/AssessmentReport.vue'
import AssessmentQuestionNavigator from './components/AssessmentQuestionNavigator.vue'
import {
  getAssessmentScales,
  getAssessmentSession,
  getCurrentAssessment,
  saveAssessmentAnswers,
  startAssessment,
  submitAssessment,
} from '@/api/assessment'
import { getSystemSettings } from '@/api/systemSettings'
import type { SystemSettingsVO } from '@/types/systemSettings'
import type { AssessmentResultVO, AssessmentScaleVO, AssessmentSessionVO } from '@/types/assessment'

const scales = ref<AssessmentScaleVO[]>([])
const availability = ref<SystemSettingsVO>()
const isAvailabilityFailed = ref(false)
const isAvailabilityLoading = ref(false)
const session = ref<AssessmentSessionVO>()
const result = ref<AssessmentResultVO>()
const answers = reactive<Record<string, number>>({})
const consent = reactive({ accepted: false, scaleId: undefined as number | undefined })
const isLoading = ref(false)
const isLoadFailed = ref(false)
const isSubmitting = ref(false)
const isSaving = ref(false)
const isSaveFailed = ref(false)
const isDirty = ref(false)
const lastSavedAt = ref('')
const selectedScale = computed(() => scales.value.find((s) => s.id === consent.scaleId))
const isDemo = computed(
  () => (session.value?.scale || selectedScale.value?.definition)?.license === 'PROJECT-DEMO-ONLY',
)
const isAcademic = computed(
  () =>
    (session.value?.scale || selectedScale.value?.definition)?.scoringVersion ===
    'OEJTS_OPENJUNG_32_V1',
)
const answeredCount = computed(
  () => session.value?.scale.questions.filter((q) => answers[q.key] !== undefined).length || 0,
)
const progress = computed(() =>
  session.value
    ? Math.round((answeredCount.value / session.value.scale.questions.length) * 100)
    : 0,
)
const isComplete = computed(
  () => !!session.value && answeredCount.value === session.value.scale.questions.length,
)
let saveTimer: ReturnType<typeof setTimeout> | undefined
let pendingSave: Promise<boolean> | undefined
let editRevision = 0
let disposed = false

const applySession = (value: AssessmentSessionVO) => {
  session.value = value
  for (const key of Object.keys(answers)) delete answers[key]
  Object.assign(answers, value.answers)
  isDirty.value = false
  isSaveFailed.value = false
  lastSavedAt.value = value.updatedAt.replace('T', ' ').slice(0, 19)
}
const getAvailability = async () => {
  isAvailabilityLoading.value = true
  isAvailabilityFailed.value = false
  try {
    const value = (await getSystemSettings()).data.data
    if (!value || typeof value.assessmentEnabled !== 'boolean')
      throw new Error('Invalid availability')
    availability.value = value
  } catch {
    availability.value = undefined
    isAvailabilityFailed.value = true
  } finally {
    isAvailabilityLoading.value = false
  }
}
const getData = async () => {
  isLoading.value = true
  isLoadFailed.value = false
  try {
    const [scaleResponse, currentResponse] = await Promise.all([
      getAssessmentScales(),
      getCurrentAssessment(),
      getAvailability(),
    ])
    scales.value = scaleResponse.data.data
    if (currentResponse.data.data) applySession(currentResponse.data.data)
    consent.scaleId = scales.value[0]?.id
  } catch {
    isLoadFailed.value = true
  } finally {
    isLoading.value = false
  }
}
const handleStart = async () => {
  if (
    isSubmitting.value ||
    isAvailabilityLoading.value ||
    !availability.value?.assessmentEnabled ||
    !consent.accepted ||
    !consent.scaleId
  )
    return
  isSubmitting.value = true
  try {
    applySession(
      (await startAssessment({ scaleId: consent.scaleId, consentAccepted: consent.accepted })).data
        .data,
    )
    result.value = undefined
  } catch {
    /* Request layer displays errors; consent is retained for retry. */
  } finally {
    isSubmitting.value = false
  }
}
const saveChanges = async (): Promise<boolean> => {
  if (pendingSave) {
    const success = await pendingSave
    if (!success) return false
    return isDirty.value ? saveChanges() : true
  }
  if (!session.value || !isDirty.value || result.value) return true
  isSaving.value = true
  pendingSave = (async () => {
    const localRevision = editRevision
    try {
      const response = await saveAssessmentAnswers(session.value!.id, {
        revision: session.value!.revision,
        answers: { ...answers },
      })
      session.value!.revision = response.data.data.revision
      lastSavedAt.value = response.data.data.updatedAt.replace('T', ' ').slice(0, 19)
      isDirty.value = localRevision !== editRevision
      isSaveFailed.value = false
      return true
    } catch {
      isSaveFailed.value = true
      return false
    } finally {
      isSaving.value = false
      pendingSave = undefined
    }
  })()
  const success = await pendingSave
  if (success && isDirty.value && !disposed) return saveChanges()
  return success
}
const handleAnswer = (key: string, value: number) => {
  answers[key] = value
  editRevision++
  isDirty.value = true
  clearTimeout(saveTimer)
  if (!isSaveFailed.value)
    saveTimer = setTimeout(() => {
      void saveChanges()
    }, 600)
}
const handleSave = async () => {
  clearTimeout(saveTimer)
  await saveChanges()
}
const handleNavigateQuestion = (index: number) => {
  if (
    isLoading.value ||
    isSubmitting.value ||
    !session.value ||
    !Number.isInteger(index) ||
    index < 0 ||
    index >= session.value.scale.questions.length
  )
    return
  const question = document.getElementById(`assessment-question-${index}`)
  question?.scrollIntoView({ block: 'center', behavior: 'instant' })
  question?.focus({ preventScroll: true })
}
const handleReload = async () => {
  if (!session.value || isSaving.value || isSubmitting.value) return
  try {
    if (isDirty.value)
      await ElMessageBox.confirm(
        '重新加载将用服务器进度替换本页未保存答案，是否继续？',
        '重新加载进度',
        { confirmButtonText: '重新加载', cancelButtonText: '取消' },
      )
  } catch {
    return
  }
  isLoading.value = true
  try {
    const value = (await getAssessmentSession(session.value.id)).data.data
    if (value.status === 'COMPLETED') {
      ElMessage.info('本次测评已提交，请到历史报告查看')
      session.value = undefined
      isDirty.value = false
      await getData()
    } else applySession(value)
  } catch {
    /* Keep local answers when reloading fails. */
  } finally {
    isLoading.value = false
  }
}
const handleSubmit = async () => {
  if (isSubmitting.value || !session.value || !isComplete.value) return
  try {
    await ElMessageBox.confirm(
      '提交后本次答案及结果将保留，不能修改；以后可重新测评。是否提交？',
      '确认提交测评',
      { confirmButtonText: '提交测评', cancelButtonText: '继续答题' },
    )
  } catch {
    return
  }
  isSubmitting.value = true
  try {
    clearTimeout(saveTimer)
    if (!(await saveChanges())) return
    result.value = (
      await submitAssessment(session.value.id, {
        revision: session.value.revision,
        answers: { ...answers },
      })
    ).data.data
    isDirty.value = false
    session.value = undefined
    ElMessage.success('测评已提交，结果已保存')
  } catch {
    /* May have committed on a lost response; history and reload remain available. */
  } finally {
    isSubmitting.value = false
  }
}
const handleNew = async () => {
  result.value = undefined
  consent.accepted = false
  await getData()
}
const handleBeforeUnload = (event: BeforeUnloadEvent) => {
  if (isDirty.value || isSaving.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
onBeforeRouteLeave(async () => {
  if (!localStorage.getItem('accessToken')) return true
  if (!isDirty.value && !isSaving.value) return true
  if (await saveChanges()) return true
  try {
    await ElMessageBox.confirm('还有答案未保存，离开后这些答案可能丢失。是否离开？', '进度未保存', {
      confirmButtonText: '仍然离开',
      cancelButtonText: '留在本页',
    })
    return true
  } catch {
    return false
  }
})
onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  void getData()
})
onBeforeUnmount(() => {
  disposed = true
  clearTimeout(saveTimer)
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<template>
  <main
    v-loading="isLoading"
    class="assessment-page"
  >
    <header class="page-heading">
      <div>
        <RouterLink to="/workbench">返回个人工作台</RouterLink>
        <h1>人格偏好测评</h1>
        <p>非官方 MBTI 测评 · 了解偏好，结合情境反思。</p>
      </div>
      <RouterLink to="/assessment/history">历次报告与结果对比</RouterLink>
    </header>
    <el-alert
      v-if="isDemo && !result"
      title="毕设演示模式：16道原创情境题，仅演示答题、评分与报告流程，未经信效度验证，不用于正式人格评估。"
      type="warning"
      :closable="false"
    />
    <el-alert
      v-if="isAcademic && !result"
      title="非商业毕设参考：32题OEJTS的OpenJung中文适配；题目及翻译为CC BY-NC-SA 4.0，保留原作者署名，中文版本未经独立验证。"
      type="info"
      :closable="false"
    />
    <el-alert
      v-if="isLoadFailed"
      title="测评数据加载失败，请刷新重试"
      type="error"
      :closable="false"
    />
    <el-button
      v-if="isLoadFailed"
      @click="getData"
    >
      重新加载测评
    </el-button>
    <el-alert
      v-if="availability && !availability.assessmentEnabled && !result"
      :title="
        '暂未开放新测评：' +
        availability.assessmentNotice +
        (session ? '。本次草稿仍可继续完成。' : '')
      "
      type="warning"
      :closable="false"
    />
    <div v-if="isAvailabilityFailed && !result">
      <el-alert
        title="测评开放状态加载失败，暂不可开始新测评；已有草稿可继续完成。"
        type="error"
        :closable="false"
      />
      <el-button
        :loading="isAvailabilityLoading"
        @click="getAvailability"
      >
        重新加载开放状态
      </el-button>
    </div>
    <el-card
      v-if="result"
      class="panel"
    >
      <AssessmentReport :result="result" />
      <el-button
        type="primary"
        @click="handleNew"
      >
        开始新的测评
      </el-button>
    </el-card>
    <el-card
      v-else-if="session"
      class="panel"
    >
      <header class="answer-heading">
        <div>
          <h2>{{ session.scale.name }} · {{ session.scale.version }}</h2>
          <p>已回答 {{ answeredCount }} / {{ session.scale.questions.length }} 题</p>
        </div>
        <el-button
          :disabled="isSaving || isSubmitting"
          @click="handleReload"
        >
          重新加载服务器进度
        </el-button>
      </header>
      <el-progress :percentage="progress" />
      <p
        role="status"
        class="save-status"
      >
        {{
          isSaveFailed
            ? '保存失败，答案尚未确认保存。请重试保存，若提示其他窗口修改则重新加载服务器进度。'
            : isSaving
              ? '正在保存答题进度…'
              : isDirty
                ? '有尚未保存的答案'
                : '进度已保存：' + lastSavedAt
        }}
      </p>
      <el-button
        :loading="isSaving"
        :disabled="isSubmitting || !isDirty"
        @click="handleSave"
      >
        保存当前进度
      </el-button>
      <AssessmentQuestionNavigator
        :questions="session.scale.questions"
        :answers="answers"
        :disabled="isSubmitting || isLoading"
        @navigate="handleNavigateQuestion"
      />
      <p class="hint">
        每题选择1至5：1最接近左侧描述，5最接近右侧描述，3表示介于两者之间。请按通常情况作答；保存成功后可退出并继续。
      </p>
      <fieldset
        v-for="(question, index) in session.scale.questions"
        :id="'assessment-question-' + index"
        :key="question.key"
        :disabled="isSubmitting || isLoading"
        tabindex="-1"
        class="question"
      >
        <legend>第 {{ index + 1 }} 题</legend>
        <div class="question-anchors">
          <span>{{ question.leftText }}</span>
          <span>{{ question.rightText }}</span>
        </div>
        <div class="answer-options">
          <label
            v-for="value in 5"
            :key="value"
          >
            <input
              :name="question.key"
              type="radio"
              :value="value"
              :checked="answers[question.key] === value"
              :aria-label="'第' + (index + 1) + '题，' + value + '分'"
              @change="handleAnswer(question.key, value)"
            />
            <span>{{ value }}</span>
          </label>
        </div>
      </fieldset>
      <el-button
        type="primary"
        :loading="isSubmitting"
        :disabled="!isComplete || isLoading"
        @click="handleSubmit"
      >
        提交测评
      </el-button>
      <p
        v-if="!isComplete"
        class="hint"
      >
        请完成全部必答题后提交。结果与本次量表版本一同保存。
      </p>
    </el-card>
    <el-card
      v-else-if="!isLoading && !isLoadFailed"
      class="panel"
    >
      <template v-if="scales.length">
        <h2>开始前请阅读</h2>
        <el-select
          v-model="consent.scaleId"
          placeholder="请选择量表"
          class="scale-select"
          @change="consent.accepted = false"
        >
          <el-option
            v-for="scale in scales"
            :key="scale.id"
            :label="scale.definition.name + ' · ' + scale.definition.version"
            :value="scale.id"
          />
        </el-select>
        <template v-if="selectedScale">
          <p>
            收集目的：为本人提供偏好分析、反思建议及历史对比。答案由系统保存，管理员可查看完整报告，部门负责人不因负责人身份获得他人报告权限。
          </p>
          <p>
            结果不代表能力、绩效、心理诊断或发展潜力，不用于招聘淘汰、晋升降级、绩效处罚或标签化管理。
          </p>
          <p>
            保存期限：{{ selectedScale.definition.retentionPolicy }}；联系渠道：{{
              selectedScale.definition.contact
            }}。
          </p>
          <p v-if="isDemo">
            题目来源：本项目原创中文演示题，非正式心理量表，无需导入即可体验。{{
              selectedScale.definition.version
            }}
          </p>
          <p v-else>
            量表来源：
            <a
              :href="selectedScale.definition.sourceUrl"
              target="_blank"
              rel="noopener noreferrer"
            >
              {{ selectedScale.definition.sourceTitle }}
            </a>
            · {{ selectedScale.definition.author }} · {{ selectedScale.definition.license }}
          </p>
          <p>
            中文版本：{{ selectedScale.definition.chineseVersion }}；评分版本：{{
              selectedScale.definition.scoringVersion
            }}；共 {{ selectedScale.definition.questions.length }} 题。
          </p>
          <p class="hint">
            {{
              isDemo
                ? '本演示使用五级双极题计分规则，仅用于说明程序算法。'
                : isAcademic
                  ? '本版本按固定OpenJung题序和方向计分，仅用于非商业毕设参考。'
                  : '此版本使用管理员核验的五级双极题计分规则。'
            }}偏好归一化显示；均衡时按{{ isAcademic ? 'E/S/F/J' : 'E/S/T/J' }}编码并明确标记均衡。
          </p>
          <p v-if="isAcademic">
            原量表：Eric Jorgenson / Open Psychometrics；中文适配：OpenJung
            contributors。上游中文左右端文本保留，问题标题合并到两端展示，分数保留两位小数。
            <a
              href="https://creativecommons.org/licenses/by-nc-sa/4.0/"
              target="_blank"
              rel="noopener noreferrer"
            >
              CC BY-NC-SA 4.0许可说明
            </a>
          </p>
          <el-checkbox v-model="consent.accepted">我已阅读并同意上述测评与数据使用说明</el-checkbox>
          <div class="start-action">
            <el-button
              type="primary"
              :disabled="
                !consent.accepted || !availability?.assessmentEnabled || isAvailabilityLoading
              "
              :loading="isSubmitting"
              @click="handleStart"
            >
              开始测评
            </el-button>
            <el-button
              :loading="isAvailabilityLoading"
              @click="getAvailability"
            >
              刷新开放状态
            </el-button>
          </div>
        </template>
      </template>
      <template v-else>
        <h2>暂未开放正式测评</h2>
        <p>
          管理员尚未发布已核验企业使用许可、中文版本及评分规则的量表。请联系管理员准备正式题库后再参与。
        </p>
        <p>已保存的历史结果仍可从“历次报告与结果对比”查看。</p>
        <el-button @click="getData">刷新量表状态</el-button>
      </template>
    </el-card>
  </main>
</template>

<style scoped lang="scss">
.assessment-page {
  max-width: 1060px;
  margin: auto;
  padding: clamp(16px, 4vw, 48px);
  color: var(--text-strong);
}
.page-heading,
.answer-heading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 16px;
}
h1,
h2 {
  color: var(--brand-600);
}
h1 {
  font-size: clamp(26px, 4vw, 36px);
}
.panel {
  margin-top: 24px;
  border-radius: 20px;
  overflow-wrap: anywhere;
}
p {
  line-height: 1.8;
}
.scale-select {
  width: 100%;
  max-width: 540px;
}
.hint,
.save-status {
  color: var(--text-secondary);
  font-size: 13px;
}
.start-action {
  margin-top: 16px;
}
.question {
  border: 1px solid rgb(0 29 111 / 15%);
  border-radius: 14px;
  padding: 16px;
  margin: 24px 0;
  min-width: 0;
  background: rgb(255 255 255 / 55%);
}
.question:focus {
  outline: 3px solid var(--brand-500);
  outline-offset: 3px;
}
legend {
  color: var(--brand-600);
  font-weight: 600;
}
.question-anchors {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  line-height: 1.7;
}
.question-anchors span:last-child {
  text-align: right;
}
.answer-options {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 16px;
}
.answer-options label {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 44px;
  flex: 1;
  border-radius: 8px;
  background: white;
  cursor: pointer;
}
.answer-options input {
  width: 18px;
  height: 18px;
  accent-color: var(--brand-600);
}
:deep(.el-checkbox) {
  white-space: normal;
  height: auto;
  line-height: 1.8;
}
:deep(.el-checkbox__label) {
  white-space: normal;
}
@media (max-width: 600px) {
  .question {
    padding: 12px;
  }
  .answer-options label {
    gap: 3px;
  }
}
</style>
