<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TalentFilters from './components/TalentFilters.vue'
import { getTalentDashboard } from '@/api/talent'
import type { TalentDashboardVO, TalentQueryReqDTO } from '@/types/talent'
import { parseTalentQuery, talentRate, talentRouteQuery, talentTime } from '@/utils/talent'

const route = useRoute()
const router = useRouter()
const data = ref<TalentDashboardVO>()
const isLoading = ref(false)
const isLoadFailed = ref(false)
const sectionCapsuleMotion = ref('')
let sectionMotionTimer = 0
let sectionSettleTimer = 0
const section = computed(() =>
  route.query.view === 'types' || route.query.view === 'scope' || route.query.view === 'departments'
    ? route.query.view
    : 'overview',
)
const sectionOrder = ['overview', 'departments', 'types', 'scope'] as const
const sectionIndex = computed(() => sectionOrder.indexOf(section.value))
const sectionCapsuleStyle = computed(() => ({
  transform: `translate3d(${sectionIndex.value * 100}%, 0, 0)`,
}))
const query = computed(() => parseTalentQuery(route.query))
let sequence = 0
const getDashboard = async () => {
  const current = ++sequence
  isLoading.value = true
  isLoadFailed.value = false
  data.value = undefined
  try {
    const response = await getTalentDashboard(query.value)
    if (current === sequence) data.value = response.data.data
  } catch {
    if (current === sequence) isLoadFailed.value = true
  } finally {
    if (current === sequence) isLoading.value = false
  }
}
const handleSearch = async (value: TalentQueryReqDTO) => {
  const location = {
    path: '/admin/talent-dashboard',
    query: { ...talentRouteQuery(value), view: route.query.view },
  }
  if (router.resolve(location).fullPath === route.fullPath) await getDashboard()
  else await router.push(location)
}
const handleDrill = (changes: Partial<TalentQueryReqDTO> = {}) => {
  if (!data.value || isLoading.value || isLoadFailed.value) return
  void router.push({
    path: '/admin/talent-employees',
    query: talentRouteQuery({ ...query.value, ...changes, pageNum: 1 }),
  })
}
const startSectionNavigation = (targetIndex: number) => {
  const currentIndex = sectionIndex.value
  if (targetIndex === currentIndex) return
  const direction = targetIndex > currentIndex ? 'forward' : 'backward'
  sectionCapsuleMotion.value = `is-moving is-${direction}`
  window.clearTimeout(sectionMotionTimer)
  window.clearTimeout(sectionSettleTimer)
  sectionMotionTimer = window.setTimeout(() => {
    sectionCapsuleMotion.value = 'is-settling'
    sectionSettleTimer = window.setTimeout(() => (sectionCapsuleMotion.value = ''), 140)
  }, 400)
}
onBeforeUnmount(() => {
  window.clearTimeout(sectionMotionTimer)
  window.clearTimeout(sectionSettleTimer)
})
// URL is the applied filter state, including browser back/forward.
watch(() => route.fullPath, getDashboard, { immediate: true })
</script>

<template>
  <main class="dashboard-page talent-statistics">
    <header class="page-heading">
      <div>
        <h1>测评统计</h1>
        <p>查看员工测评完成情况，点击人数进入人员明细。</p>
      </div>
      <el-button
        :loading="isLoading"
        @click="getDashboard"
      >
        刷新看板
      </el-button>
    </header>
    <nav
      class="section-navigation"
      aria-label="测评统计视图"
    >
      <span
        class="section-nav-capsule"
        :class="sectionCapsuleMotion"
        :style="sectionCapsuleStyle"
        aria-hidden="true"
      />
      <RouterLink
        :to="{ path: route.path, query: { ...route.query, view: undefined } }"
        :aria-current="section === 'overview' ? 'page' : undefined"
        @click="startSectionNavigation(0)"
      >
        <span>参与概览</span>
      </RouterLink>
      <RouterLink
        :to="{ path: route.path, query: { ...route.query, view: 'departments' } }"
        :aria-current="section === 'departments' ? 'page' : undefined"
        @click="startSectionNavigation(1)"
      >
        <span>部门明细</span>
      </RouterLink>
      <RouterLink
        :to="{ path: route.path, query: { ...route.query, view: 'types' } }"
        :aria-current="section === 'types' ? 'page' : undefined"
        @click="startSectionNavigation(2)"
      >
        <span>人格分布</span>
      </RouterLink>
      <RouterLink
        :to="{ path: route.path, query: { ...route.query, view: 'scope' } }"
        :aria-current="section === 'scope' ? 'page' : undefined"
        @click="startSectionNavigation(3)"
      >
        <span>统计说明</span>
      </RouterLink>
    </nav>
    <el-card class="panel">
      <TalentFilters
        :query="query"
        :is-loading="isLoading"
        @search="handleSearch"
      />
    </el-card>
    <el-alert
      v-if="isLoadFailed"
      title="看板加载失败，请刷新重试。当前不展示旧统计。"
      type="error"
      :closable="false"
      class="error-notice"
    />
    <p
      v-if="isLoading"
      role="status"
    >
      正在计算当前范围的统计…
    </p>
    <template v-if="data">
      <section
        v-if="section === 'overview'"
        class="metrics"
        aria-label="测评参与统计"
      >
        <button
          :disabled="!data.employeeCount"
          @click="handleDrill()"
          class="metric"
        >
          <span>审核通过人数</span>
          <strong>{{ data.employeeCount }}</strong>
          <small>当前筛选范围 · 查看人员</small>
        </button>
        <button
          :disabled="!data.completedCount"
          @click="handleDrill({ assessmentStatus: 'COMPLETED' })"
          class="metric"
        >
          <span>已完成人数</span>
          <strong>{{ data.completedCount }}</strong>
          <small>至少一次有效提交 · 查看人员</small>
        </button>
        <button
          :disabled="!data.notCompletedCount"
          @click="handleDrill({ assessmentStatus: 'NOT_COMPLETED' })"
          class="metric"
        >
          <span>未完成人数</span>
          <strong>{{ data.notCompletedCount }}</strong>
          <small>无有效提交 · 查看人员</small>
        </button>
        <article class="metric">
          <span>完成率</span>
          <strong class="rate">{{ talentRate(data.completionRate) }}</strong>
          <small>已完成人数 ÷ 当前范围人数</small>
        </article>
        <article class="metric">
          <span>有效测评次数</span>
          <strong>{{ data.assessmentCount }}</strong>
          <small>重复测评累加次数，不累加人数</small>
        </article>
      </section>
      <el-card
        v-if="section === 'scope'"
        class="panel"
      >
        <h2>统计口径</h2>
        <p class="notice">{{ data.scopeNotice }}</p>
        <p class="notice">
          类型分布分母为已完成人数；不同量表仅汇总类型，不比较维度分值。人数和提交次数不包含待审核、拒绝审核人员。更新时间：{{
            talentTime(data.generatedAt)
          }}（Asia/Shanghai）。
        </p>
      </el-card>
      <el-card
        v-if="section === 'types'"
        class="panel"
      >
        <h2>最新人格偏好分布</h2>
        <p>每位员工只计最新一次有效结果。点击类型查看对应人员。</p>
        <el-empty
          v-if="!data.completedCount"
          description="暂无已完成测评，类型分布暂无数据"
        />
        <div
          v-else
          class="type-grid"
        >
          <button
            v-for="item in data.typeDistribution"
            :key="item.typeCode"
            :disabled="!item.employeeCount"
            @click="handleDrill({ typeCode: item.typeCode, assessmentStatus: 'COMPLETED' })"
            class="type-item"
          >
            <span>{{ item.typeCode }}</span>
            <strong>{{ item.employeeCount }} 人</strong>
            <el-progress
              :percentage="item.percent ?? 0"
              :show-text="false"
            />
            <small>{{ talentRate(item.percent) }}</small>
          </button>
        </div>
      </el-card>
      <el-card
        v-if="section === 'departments' || section === 'overview'"
        class="panel"
      >
        <div class="department-section-heading">
          <h2>部门参与分布</h2>
          <p v-if="section === 'departments'">按员工当前部门归属汇总，未分配人员单独列出。</p>
          <RouterLink
            v-else
            :to="{ path: route.path, query: { ...route.query, view: 'departments' } }"
          >
            查看全部部门
          </RouterLink>
        </div>
        <el-table
          :data="
            section === 'overview'
              ? data.departmentDistribution.slice(0, 3)
              : data.departmentDistribution
          "
          :max-height="260"
          empty-text="暂无符合条件的部门人员"
        >
          <el-table-column
            prop="departmentName"
            label="部门"
            min-width="140"
          />
          <el-table-column
            prop="employeeCount"
            label="人数"
            min-width="85"
          />
          <el-table-column
            prop="completedCount"
            label="已完成"
            min-width="85"
          />
          <el-table-column
            prop="notCompletedCount"
            label="未完成"
            min-width="85"
          />
          <el-table-column
            prop="assessmentCount"
            label="提交次数"
            min-width="95"
          />
          <el-table-column
            label="完成率"
            min-width="115"
          >
            <template #default="{ row }">{{ talentRate(row.completionRate) }}</template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="110"
          >
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                @click="handleDrill({ departmentId: row.departmentId ?? 0 })"
              >
                查看人员
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </main>
</template>

<style scoped lang="scss">
.dashboard-page {
  max-width: 1440px;
  padding: clamp(16px, 4vw, 48px);
  margin: auto;
  color: var(--text-strong);
}
.page-heading {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.section-navigation {
  position: relative;
  display: grid !important;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0 !important;
  width: 368px !important;
  padding: 4px !important;
  overflow: hidden;
  isolation: isolate;
}
.section-navigation > a {
  position: relative;
  z-index: 1;
  justify-content: center;
  min-width: 0;
  padding-inline: 12px !important;
  color: var(--text-secondary) !important;
  background: transparent !important;
  box-shadow: none !important;
  white-space: nowrap;
  transition: color 260ms cubic-bezier(0.22, 1, 0.36, 1) !important;
}
.section-navigation > a:hover {
  color: var(--text-strong) !important;
  background: transparent !important;
}
.section-navigation > a[aria-current='page'] {
  color: #fff !important;
}
.section-navigation > a > span {
  display: block;
}
.section-navigation > a[aria-current='page'] > span {
  animation: section-nav-label 400ms cubic-bezier(0.22, 1.35, 0.36, 1);
}
.section-nav-capsule {
  position: absolute;
  inset: 4px auto 4px 4px;
  z-index: 0;
  width: calc((100% - 8px) / 4);
  border-radius: 20px;
  background: #242033;
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 16%),
    0 6px 14px rgb(36 32 51 / 14%);
  transform-origin: center;
  transition: transform 400ms cubic-bezier(0.22, 1.18, 0.36, 1);
  will-change: transform;
}
.section-nav-capsule::after {
  position: absolute;
  inset: 4px;
  content: '';
  border-radius: inherit;
  background: rgb(255 255 255 / 5%);
  opacity: 0;
  transition:
    transform 400ms cubic-bezier(0.22, 1.25, 0.36, 1),
    opacity 160ms;
}
.section-nav-capsule.is-moving {
  animation-duration: 400ms;
  animation-timing-function: cubic-bezier(0.22, 1, 0.36, 1);
}
.section-nav-capsule.is-moving::after {
  opacity: 1;
  transform: scaleX(1.18) scaleY(0.82);
}
.section-nav-capsule.is-forward {
  transform-origin: right center;
  animation-name: section-capsule-travel;
}
.section-nav-capsule.is-backward {
  transform-origin: left center;
  animation-name: section-capsule-travel;
}
.section-nav-capsule.is-settling {
  animation: section-capsule-settle 140ms cubic-bezier(0.34, 1.56, 0.64, 1);
}
@keyframes section-nav-label {
  0% {
    transform: scale(1);
  }
  38% {
    transform: scale(0.88);
  }
  100% {
    transform: scale(1);
  }
}
@keyframes section-capsule-travel {
  0% {
    scale: 1;
  }
  42% {
    scale: 1.14 0.88;
  }
  78% {
    scale: 1.04 0.96;
  }
  100% {
    scale: 1 0.94;
  }
}
@keyframes section-capsule-settle {
  0% {
    scale: 1 0.92;
  }
  65% {
    scale: 1.025 1.03;
  }
  100% {
    scale: 1;
  }
}
.eyebrow {
  font-size: 12px;
  color: var(--brand-600);
  letter-spacing: 1px;
  font-weight: 700;
}
h1,
h2 {
  color: var(--brand-600);
}
.panel {
  margin-top: 24px;
  border-radius: 18px;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
  margin-top: 24px;
}
.metric {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 24px 18px;
  background: rgb(255 255 255 / 85%);
  border: 1px solid white;
  border-radius: 16px;
  color: var(--text-strong);
  font: inherit;
  text-align: left;
  min-width: 0;
}
button.metric,
.type-item {
  cursor: pointer;
}
button:disabled {
  cursor: default;
  opacity: 0.65;
}
button:focus-visible {
  outline: 3px solid var(--brand-600);
  outline-offset: 3px;
}
.metric strong {
  font-size: clamp(22px, 2.5vw, 36px);
  color: var(--brand-600);
  overflow-wrap: anywhere;
}
.metric .rate {
  font-size: clamp(20px, 2vw, 30px);
}
.metric small,
.type-item small {
  color: var(--text-secondary);
  line-height: 1.6;
}
.notice {
  font-size: 14px;
  line-height: 1.9;
}
.error-notice {
  margin-top: 24px;
}
.type-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.type-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  text-align: left;
  border: 1px solid var(--border-default);
  border-radius: 12px;
  padding: 16px;
  background: var(--accent-50);
  color: var(--brand-600);
  font: inherit;
}
.type-item :deep(.el-progress) {
  grid-column: 1 / -1;
}
.type-item small {
  grid-column: 1 / -1;
}
@media (max-width: 1050px) {
  .metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 650px) {
  .section-navigation {
    width: 100% !important;
  }
  .metrics,
  .type-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .metric {
    padding: 18px 12px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .section-nav-capsule,
  .section-nav-capsule::after,
  .section-navigation > a,
  .section-navigation > a > span {
    transition: none !important;
    animation: none !important;
  }
}
</style>
