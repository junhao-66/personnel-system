<script setup lang="ts">
// Display the saved report snapshot; never recompute employee scores in the browser.
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAssessmentPdf } from '@/api/assessment'
import type { AssessmentResultVO } from '@/types/assessment'
import ReportAiExpansion from '@/components/analysis/ReportAiExpansion.vue'
import ReportPdfPreview from './ReportPdfPreview.vue'

const props = defineProps<{ result: AssessmentResultVO }>()
const isExporting = ref(false)
let isDisposed = false
const sections = computed(() =>
  [
    { title: '人格偏好解释', items: props.result.report.preferenceSummary },
    { title: '优势倾向', items: props.result.report.strengths },
    { title: '待发展方向', items: props.result.report.developmentDirections },
    { title: '工作方式偏好', items: props.result.report.workPreferences },
    { title: '职业发展建议', items: props.result.report.careerSuggestions },
    { title: '成长行动建议', items: props.result.report.growthSuggestions },
  ].filter(
    (section) => section.title !== '成长行动建议' || !props.result.report.growthPlans?.length,
  ),
)
const formatTime = (value: string) => value.replace('T', ' ').slice(0, 16)
const handleExport = async () => {
  if (isExporting.value) return
  isExporting.value = true
  const id = props.result.id
  try {
    const response = await getAssessmentPdf(id)
    if (isDisposed || id !== props.result.id) return
    try {
      const url = URL.createObjectURL(response.data)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `assessment-report-${id}.pdf`
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch {
      ElMessage.error('文件下载失败，请检查浏览器下载设置后重试')
    }
  } catch {
    /* Shared request layer displays authorization, rate-limit and generation errors. */
  } finally {
    isExporting.value = false
  }
}
onBeforeUnmount(() => {
  isDisposed = true
})
</script>

<template>
  <article class="report">
    <header>
      <p>本次偏好结果</p>
      <h2>{{ result.typeCode }}</h2>
      <p>
        {{ result.scaleName }} · {{ result.scaleVersion }} · {{ formatTime(result.submittedAt) }}
      </p>
    </header>
    <el-button
      :loading="isExporting"
      type="primary"
      @click="handleExport"
    >
      导出PDF报告
    </el-button>
    <ReportPdfPreview :result-id="result.id" />
    <ReportAiExpansion :result-id="result.id" />
    <el-alert
      :title="result.report.disclaimer"
      type="info"
      :closable="false"
    />
    <h3>四维偏好得分</h3>
    <p class="hint">两端合计100，仅为归一化偏好刻度，不是能力、概率或优劣。接近50时倾向较弱。</p>
    <div class="dimensions">
      <section
        v-for="item in result.dimensions"
        :key="item.dimension"
        class="dimension"
      >
        <div class="dimension-heading">
          <strong>{{ item.dimension[0] }} {{ item.firstPercent }}%</strong>
          <strong>{{ item.dimension[1] }} {{ item.secondPercent }}%</strong>
        </div>
        <el-progress
          :percentage="item.firstPercent"
          :show-text="false"
        />
        <p>
          {{
            item.balanced
              ? '恰好均衡，类型字母仅按约定编码'
              : item.weakPreference
                ? '倾向较弱，请结合情境理解'
                : '本次可能更偏好 ' + item.preferredPole
          }}
        </p>
      </section>
    </div>
    <section
      v-for="section in sections"
      :key="section.title"
    >
      <h3>{{ section.title }}</h3>
      <ul>
        <li
          v-for="item in section.items"
          :key="item"
        >
          {{ item }}
        </li>
      </ul>
    </section>
    <section
      v-if="result.report.growthPlans?.length"
      class="growth-plans"
    >
      <h3>成长行动计划</h3>
      <article
        v-for="plan in result.report.growthPlans"
        :key="plan.id"
      >
        <h4>{{ plan.context }}</h4>
        <dl>
          <dt>尝试行动</dt>
          <dd>{{ plan.action }}</dd>
          <dt>建议周期</dt>
          <dd>{{ plan.suggestedCycle }}</dd>
          <dt>观察目标</dt>
          <dd>{{ plan.observationTarget }}</dd>
          <dt>复盘提示</dt>
          <dd>{{ plan.reviewPrompt }}</dd>
        </dl>
        <p class="hint">{{ plan.basis }}</p>
        <p
          v-if="plan.templateVersion"
          class="hint"
        >
          当次建议模板：{{ plan.templateVersion }}
          <span v-if="plan.templateContentHash">· 摘要：{{ plan.templateContentHash }}</span>
        </p>
        <p class="hint">
          {{
            plan.evidenceLevel === 'PROJECT_DEMO'
              ? '项目演示假设，未经实证验证'
              : plan.evidenceLevel === 'PROJECT_TEMPLATE'
                ? '项目通用模板'
                : '来源参考，效果未经验证'
          }}
        </p>
        <a
          v-if="plan.ruleId"
          :href="`#graph-rule-${plan.ruleId}`"
        >
          查看当次规则依据（{{ plan.graphVersion }}）
        </a>
      </article>
    </section>
    <h3>依据与来源</h3>
    <p>{{ result.report.sourceBasis }}</p>
    <section
      v-if="result.report.graphExplanation"
      class="graph-evidence"
    >
      <h3>知识图谱解释路径</h3>
      <p>{{ result.report.graphExplanation.notice }}</p>
      <p>当次图谱版本：{{ result.report.graphExplanation.graphVersion || '无可用版本' }}</p>
      <article
        v-for="match in result.report.graphExplanation.matches"
        :id="`graph-rule-${match.ruleId}`"
        :key="match.ruleId"
      >
        <h4>如果处于“{{ match.context }}”情境</h4>
        <p>{{ match.action }}</p>
        <p>命中前提：{{ match.matchedConditions.join(' 且 ') }}</p>
        <p>{{ match.explanation }}</p>
        <details>
          <summary>展开当次依据路径和来源</summary>
          <ul>
            <li
              v-for="edge in match.edges"
              :key="edge.id"
            >
              {{ match.nodes.find((n) => n.id === edge.from)?.label }} → {{ edge.relation }} →
              {{ match.nodes.find((n) => n.id === edge.to)?.label }}：{{ edge.explanation }}
            </li>
          </ul>
          <p
            v-for="source in match.sources"
            :key="source.id"
          >
            {{ source.title }} · {{ source.author }} ·
            {{
              source.evidenceLevel === 'PROJECT_DEMO' ? '项目演示假设，未经实证验证' : '来源参考'
            }}
            · {{ source.license }} · {{ source.contentVersion }} · {{ source.accessedDate }}。{{
              source.attribution
            }}
            <a
              :href="source.url"
              target="_blank"
              rel="noopener noreferrer"
            >
              查看来源
            </a>
          </p>
        </details>
      </article>
      <p v-if="!result.report.graphExplanation.matches.length">
        本次未命中多维规则，不据此判断能力、优势或发展潜力。
      </p>
      <RouterLink to="/knowledge-graph">探索当前已发布关系图（可能与当次版本不同）</RouterLink>
    </section>
    <p v-if="result.scale.license === 'PROJECT-DEMO-ONLY'">
      题目来源：本项目原创教学演示内容 · {{ result.scale.chineseVersion }} · 非正式人格评估
    </p>
    <p v-else>
      <a
        :href="result.scale.sourceUrl"
        target="_blank"
        rel="noopener noreferrer"
      >
        查看量表来源
      </a>
      · {{ result.scale.license }} · {{ result.scale.chineseVersion }}
    </p>
    <p v-if="result.scoringVersion === 'OEJTS_OPENJUNG_32_V1'">
      题目及中文适配署名：Eric Jorgenson / Open Psychometrics；OpenJung contributors。CC BY-NC-SA
      4.0，仅限本项目非商业毕设使用；题目标题合并到左右端显示，分数显示保留两位小数，中文译本未经独立验证。
      <a
        href="https://creativecommons.org/licenses/by-nc-sa/4.0/"
        target="_blank"
        rel="noopener noreferrer"
      >
        数据许可
      </a>
    </p>
    <h3>测评局限与数据使用</h3>
    <p>{{ result.report.limitations }}</p>
    <p class="hint">
      评分版本：{{ result.scoringVersion }}；报告版本：{{
        result.report.reportVersion
      }}。以上内容为当次保存的快照。
    </p>
  </article>
</template>

<style scoped lang="scss">
.report {
  color: var(--text-strong);
  line-height: 1.8;
  overflow-wrap: anywhere;
}
h2 {
  color: var(--brand-600);
  font-size: 40px;
  margin: 0;
}
h3 {
  margin: 28px 0 12px;
  color: var(--brand-600);
}
.hint,
.dimension p {
  font-size: 13px;
  color: var(--text-secondary);
}
.dimensions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.dimension {
  padding: 16px;
  background: rgb(255 255 255 / 65%);
  border-radius: 12px;
}
.dimension-heading {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  gap: 10px;
}
li {
  margin-bottom: 8px;
}
.growth-plans article {
  padding: 16px;
  margin: 12px 0;
  border: 1px solid var(--border-default);
  border-radius: 12px;
}
dl {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr);
  gap: 8px;
}
dt {
  font-weight: 600;
}
dd {
  margin: 0;
}
@media (max-width: 400px) {
  dl {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 600px) {
  .dimensions {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
