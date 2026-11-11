<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getTalentDashboard } from '@/api/talent'
import { getPendingUsers } from '@/api/user'
import { defaultTalentQuery, talentRate, talentTime } from '@/utils/talent'
import type { TalentDashboardVO } from '@/types/talent'
import type { UserVO } from '@/types/user'

const data = ref<TalentDashboardVO>()
const pending = ref<UserVO[]>([])
const isLoading = ref(false)
const isLoadFailed = ref(false)
const isReviewFailed = ref(false)
let loadSequence = 0

const load = async () => {
  const sequence = ++loadSequence
  isLoading.value = true
  isLoadFailed.value = false
  isReviewFailed.value = false
  const [dashboardResult, reviewResult] = await Promise.allSettled([
    getTalentDashboard(defaultTalentQuery()),
    getPendingUsers(),
  ])
  if (sequence !== loadSequence) return
  if (dashboardResult.status === 'fulfilled') {
    data.value = dashboardResult.value.data.data
  } else {
    data.value = undefined
    isLoadFailed.value = true
  }
  if (reviewResult.status === 'fulfilled') {
    pending.value = reviewResult.value.data.data
  } else {
    pending.value = []
    isReviewFailed.value = true
  }
  isLoading.value = false
}

const completion = computed(() => Math.min(100, Math.max(0, data.value?.completionRate ?? 0)))
const departments = computed(() => data.value?.departmentDistribution.slice(0, 5) ?? [])
const maxDepartment = computed(() =>
  Math.max(1, ...departments.value.map((item) => item.employeeCount)),
)
onMounted(load)
</script>
<template>
  <main
    class="dashboard-page"
    :aria-busy="isLoading"
  >
    <header class="page-heading">
      <div>
        <h1>人事总览</h1>
        <p>掌握在职人员、待办审核与各部门测评进度</p>
      </div>
      <div class="heading-actions">
        <span
          v-if="data"
          class="updated-at"
        >
          {{ talentTime(data.generatedAt) }} 更新
        </span>
        <el-button
          :loading="isLoading"
          @click="load"
        >
          刷新数据
        </el-button>
        <RouterLink
          class="action mint-action"
          to="/admin/employees"
        >
          查看员工档案 ↗
        </RouterLink>
      </div>
    </header>
    <el-alert
      v-if="isLoadFailed"
      title="人员统计加载失败，请刷新重试"
      type="error"
      :closable="false"
      class="error-notice"
    />
    <div class="dashboard-grid">
      <section
        class="personnel-card"
        aria-label="人员概况"
      >
        <div class="personnel-top">
          <span>在职员工</span>
          <RouterLink
            to="/admin/employees"
            aria-label="查看全部员工"
          >
            ↗
          </RouterLink>
        </div>
        <strong class="personnel-number">
          {{ data?.employeeCount ?? '—' }}
          <small>人</small>
        </strong>
        <div class="personnel-actions">
          <RouterLink
            class="action dark-action"
            to="/admin/employees"
          >
            员工档案
          </RouterLink>
          <RouterLink
            class="action white-action"
            to="/admin/organization"
          >
            部门岗位
          </RouterLink>
        </div>
        <div class="personnel-details">
          <div>
            <span>累计测评</span>
            <strong>
              {{ data?.assessmentCount ?? '—' }}
              <small>次</small>
            </strong>
          </div>
          <div>
            <span>测评完成率</span>
            <strong>{{ data ? talentRate(data.completionRate) : '—' }}</strong>
          </div>
        </div>
        <footer>仅统计当前权限范围内的人员</footer>
      </section>
      <section
        class="compact-stats"
        aria-label="测评人数"
      >
        <RouterLink
          :to="{ path: '/admin/talent-employees', query: { assessmentStatus: 'COMPLETED' } }"
          class="stat-card"
        >
          <div>
            <span class="stat-symbol">✓</span>
            <span>测评状态 ↗</span>
          </div>
          <p>已完成测评</p>
          <strong>
            {{ data?.completedCount ?? '—' }}
            <small>人</small>
          </strong>
          <span class="stat-status">已生成测评结果</span>
        </RouterLink>
        <RouterLink
          :to="{ path: '/admin/talent-employees', query: { assessmentStatus: 'NOT_COMPLETED' } }"
          class="stat-card"
        >
          <div>
            <span class="stat-symbol">−</span>
            <span>测评状态 ↗</span>
          </div>
          <p>尚未完成测评</p>
          <strong>
            {{ data?.notCompletedCount ?? '—' }}
            <small>人</small>
          </strong>
          <span class="stat-status muted">查看待测评人员</span>
        </RouterLink>
      </section>
      <section class="data-panel review-panel">
        <header class="panel-heading">
          <h2>注册待审核</h2>
          <RouterLink to="/admin/review">全部审核 ↗</RouterLink>
        </header>
        <div class="review-table-head">
          <span>申请人</span>
          <span>状态</span>
          <span>操作</span>
        </div>
        <p
          v-if="isLoading"
          class="empty-state"
        >
          正在加载审核申请…
        </p>
        <p
          v-else-if="isReviewFailed"
          class="empty-state"
        >
          审核列表加载失败
          <button @click="load">重试</button>
        </p>
        <p
          v-else-if="!pending.length"
          class="empty-state"
        >
          暂无待审核申请
        </p>
        <div
          v-else
          class="review-rows"
        >
          <RouterLink
            v-for="person in pending.slice(0, 4)"
            :key="person.id"
            to="/admin/review"
            class="review-row"
          >
            <span class="review-person">
              <span class="avatar">{{ (person.realName || person.userName).slice(0, 1) }}</span>
              <span>
                {{ person.realName || person.userName }}
                <small>{{ person.userName }}</small>
              </span>
            </span>
            <span class="review-status">待审核</span>
            <span class="review-go">审核 ↗</span>
          </RouterLink>
        </div>
        <footer class="review-total">
          {{ isReviewFailed ? '请刷新后重试' : '共 ' + pending.length + ' 项注册申请待处理' }}
        </footer>
      </section>
      <section class="data-panel department-panel">
        <header class="panel-heading">
          <h2>部门测评进度</h2>
          <RouterLink to="/admin/talent-dashboard?view=departments">部门统计 ↗</RouterLink>
        </header>
        <div class="chart-legend">
          <span>
            <i class="legend-completed" />
            已完成
          </span>
          <span>
            <i class="legend-pending" />
            未完成
          </span>
          <span class="chart-unit">单位：人</span>
        </div>
        <div
          v-if="departments.length"
          class="department-chart"
        >
          <RouterLink
            v-for="item in departments"
            :key="item.departmentId ?? 'none'"
            :to="{
              path: '/admin/talent-dashboard',
              query: {
                view: 'departments',
                ...(item.departmentId ? { departmentId: item.departmentId } : {}),
              },
            }"
            class="department-bar-row"
          >
            <span class="department-name">{{ item.departmentName }}</span>
            <div class="bar-grid">
              <div
                class="stacked-bar"
                :style="{ width: Math.max(1, (item.employeeCount / maxDepartment) * 100) + '%' }"
              >
                <span
                  class="bar-completed"
                  :style="{
                    width:
                      (item.employeeCount ? (item.completedCount / item.employeeCount) * 100 : 0) +
                      '%',
                  }"
                />
                <span class="bar-pending" />
              </div>
            </div>
            <span class="bar-value">
              {{ item.completedCount }}
              <small>/ {{ item.employeeCount }}</small>
            </span>
          </RouterLink>
          <div class="chart-axis">
            <span>0</span>
            <span>{{ Math.round(maxDepartment / 2) }}</span>
            <span>{{ maxDepartment }}</span>
          </div>
        </div>
        <p
          v-else
          class="empty-state"
        >
          {{ isLoading ? '正在加载部门数据…' : '暂无部门统计' }}
        </p>
        <footer class="chart-footer">
          <span>按部门对比测评覆盖情况</span>
          <RouterLink to="/admin/talent-dashboard">查看测评统计 →</RouterLink>
        </footer>
      </section>
      <section class="data-panel participation-panel">
        <header class="panel-heading">
          <h2>员工测评覆盖</h2>
          <RouterLink to="/admin/talent-dashboard">详情 ↗</RouterLink>
        </header>
        <div class="participation-content">
          <div
            class="donut"
            :style="{
              background:
                'conic-gradient(#63bbb2 0% ' + completion + '%, #eeedf3 ' + completion + '% 100%)',
            }"
            role="img"
            :aria-label="'测评完成率 ' + (data ? talentRate(data.completionRate) : '暂无数据')"
          >
            <div>
              <strong>{{ data ? talentRate(data.completionRate) : '—' }}</strong>
              <span>测评完成率</span>
            </div>
          </div>
          <div class="donut-legend">
            <RouterLink
              :to="{ path: '/admin/talent-employees', query: { assessmentStatus: 'COMPLETED' } }"
            >
              <i class="legend-completed" />
              <span>已完成测评</span>
              <strong>{{ data?.completedCount ?? '—' }}</strong>
            </RouterLink>
            <RouterLink
              :to="{
                path: '/admin/talent-employees',
                query: { assessmentStatus: 'NOT_COMPLETED' },
              }"
            >
              <i class="legend-pending" />
              <span>未完成测评</span>
              <strong>{{ data?.notCompletedCount ?? '—' }}</strong>
            </RouterLink>
          </div>
        </div>
        <footer class="participation-footer">
          <RouterLink to="/admin/talent-dashboard">
            <strong>
              {{ data?.assessmentCount ?? '—' }}
              <small>次</small>
            </strong>
            <span>累计测评记录</span>
          </RouterLink>
          <RouterLink to="/mbti">
            <strong>
              16
              <small>型</small>
            </strong>
            <span>了解 MBTI 人格 ↗</span>
          </RouterLink>
        </footer>
      </section>
    </div>
  </main>
</template>
<style scoped lang="scss">
.heading-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.updated-at {
  font-size: 12px;
  color: var(--text-secondary);
}
.error-notice {
  margin-bottom: 12px;
}
.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(200px, 1fr) minmax(340px, 1.55fr);
  grid-template-rows: minmax(256px, auto) minmax(264px, auto);
  gap: 16px;
}
.action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 8px 16px;
  border-radius: 24px;
  text-decoration: none;
  font-size: 12px;
  white-space: nowrap;
}
.mint-action {
  background: #cceae6;
  color: #164f4c;
}
.dark-action {
  background: #242033;
  color: #fff;
}
.white-action {
  background: #fff;
  color: #294440;
}
.personnel-card {
  min-width: 0;
  border-radius: 24px;
  padding: 20px;
  background: linear-gradient(140deg, #94d3cc, #42a69f);
  color: #153c3a;
  box-shadow: inset 0 1px 0 #ffffff80;
}
.personnel-top {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
}
.personnel-top a {
  color: inherit;
  text-decoration: none;
}
.personnel-number {
  display: block;
  font-size: 36px;
  line-height: 44px;
  font-weight: 500;
  letter-spacing: -0.03em;
}
.personnel-number small {
  font-size: 12px;
  margin-left: 8px;
  letter-spacing: 0;
}
.scope-note {
  font-size: 12px;
}
.personnel-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.personnel-actions a {
  flex: 1;
  padding-inline: 8px;
}
.personnel-details {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 16px;
}
.personnel-details span {
  display: block;
  font-size: 12px;
}
.personnel-details strong {
  display: block;
  font-size: 20px;
  line-height: 28px;
  font-weight: 500;
}
.personnel-details small {
  font-size: 12px;
  margin-left: 4px;
}
.personnel-card footer {
  font-size: 12px;
  margin-top: 8px;
  color: #214f4b;
}
.compact-stats {
  display: grid;
  grid-template-rows: 1fr 1fr;
  gap: 12px;
}
.stat-card {
  display: block;
  position: relative;
  padding: 16px 20px;
  border-radius: 20px;
  background: #f4f3f7;
  color: var(--text-primary);
  text-decoration: none;
}
.stat-card > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-secondary);
}
.stat-symbol {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  background: #fff;
  border-radius: 50%;
  color: #555;
}
.stat-card p {
  font-size: 12px;
  margin: 8px 0 0;
  color: var(--text-secondary);
}
.stat-card > strong {
  font-size: 24px;
  font-weight: 500;
  line-height: 32px;
}
.stat-card small {
  font-size: 12px;
  margin-left: 4px;
}
.stat-status {
  justify-self: end;
  margin-top: 0;
  font-size: 12px;
  color: #216b57;
  background: #d7eee1;
  padding: 3px 6px;
  border-radius: 8px;
}
.stat-status.muted {
  background: #e9e6ef;
  color: #696174;
}
.data-panel {
  min-width: 0;
  padding: 20px;
  border: 1px solid #eeedf2;
  border-radius: 24px;
  background: #fff;
}
.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.panel-heading h2 {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  font-weight: 500;
}
.panel-heading > a {
  font-size: 12px;
  color: var(--text-secondary);
  text-decoration: none;
  white-space: nowrap;
}
.review-table-head,
.review-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 64px 48px;
  align-items: center;
  gap: 12px;
}
.review-table-head {
  margin-top: 16px;
  background: #f7f7f9;
  border-radius: 8px;
  padding: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}
.review-row {
  min-height: 36px;
  border-bottom: 1px solid #f0eff3;
  padding: 4px 8px;
  color: inherit;
  text-decoration: none;
  font-size: 12px;
}
.review-row:hover {
  background: #faf9fc;
}
.review-person {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.review-person > span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.review-person small {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
}
.avatar {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #efedf3;
  color: #5c546a;
  font-size: 12px;
}
.review-status {
  font-size: 12px;
  color: #8d691d;
}
.review-status:before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 4px;
  background: #ba963c;
  border-radius: 50%;
  margin-right: 4px;
}
.review-go {
  text-align: right;
  color: #4f495b;
}
.review-total {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 8px;
}
.department-panel {
  grid-column: 1/3;
}
.chart-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 16px 0;
  font-size: 12px;
  color: var(--text-secondary);
}
.chart-legend > span {
  display: flex;
  align-items: center;
  gap: 6px;
}
.chart-unit {
  margin-left: auto;
}
i {
  display: inline-block;
  flex-shrink: 0;
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
.legend-completed {
  background: #63bbb2;
}
.legend-pending {
  background: #ddd9e8;
}
.department-chart {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 124px;
  max-height: 140px;
  overflow-y: auto;
  gap: 12px;
}
.department-bar-row {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr) 52px;
  gap: 12px;
  align-items: center;
  color: inherit;
  text-decoration: none;
  font-size: 12px;
}
.department-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.bar-grid {
  height: 20px;
  background: repeating-linear-gradient(90deg, #eeedf2 0 1px, transparent 1px 25%);
}
.stacked-bar {
  height: 20px;
  display: flex;
  border-radius: 6px;
  overflow: hidden;
}
.bar-completed {
  background: #63bbb2;
}
.bar-pending {
  flex: 1;
  background: #ddd9e8;
}
.bar-value {
  text-align: right;
  font-size: 12px;
}
.bar-value small {
  color: var(--text-secondary);
  font-size: 12px;
}
.chart-axis {
  display: flex;
  justify-content: space-between;
  margin: 0 64px 0 104px;
  color: #8b8694;
  font-size: 12px;
}
.chart-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  border-top: 1px solid #eeedf2;
  margin-top: 12px;
  padding-top: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}
.chart-footer a {
  color: #365e59;
  text-decoration: none;
}
.participation-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  margin: 20px 0;
}
.donut {
  display: grid;
  place-items: center;
  width: 144px;
  height: 144px;
  flex-shrink: 0;
  border-radius: 50%;
}
.donut > div {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  width: 108px;
  height: 108px;
  background: #fff;
  border-radius: 50%;
  gap: 4px;
}
.donut strong {
  font-size: 20px;
  font-weight: 500;
  letter-spacing: -0.02em;
}
.donut span {
  font-size: 12px;
  color: var(--text-secondary);
}
.donut-legend {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 100px;
}
.donut-legend a {
  display: grid;
  grid-template-columns: 8px 1fr;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 12px;
}
.donut-legend strong {
  grid-column: 2;
  font-size: 18px;
  font-weight: 500;
  color: var(--text-primary);
}
.participation-footer {
  display: flex;
  border-top: 1px solid #eeedf2;
  padding-top: 12px;
}
.participation-footer a {
  flex: 1;
  text-decoration: none;
  color: inherit;
  padding-inline: 12px;
}
.participation-footer a:first-child {
  padding-left: 0;
}
.participation-footer a + a {
  border-left: 1px solid #eeedf2;
}
.participation-footer strong {
  display: block;
  font-size: 18px;
  font-weight: 500;
}
.participation-footer small {
  font-size: 12px;
}
.participation-footer span {
  display: block;
  color: var(--text-secondary);
  font-size: 12px;
  margin-top: 4px;
}
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 120px;
  color: var(--text-secondary);
  font-size: 12px;
}
.empty-state button {
  min-height: 32px;
  padding: 4px 12px;
  border: 0;
  border-radius: 16px;
  color: #534d5a;
  background: #f4f2f7;
  box-shadow: inset 0 0 0 1px rgb(61 82 78 / 6%);
  text-decoration: none;
  cursor: pointer;
  transition:
    color 300ms var(--ease-out),
    background-color 300ms var(--ease-out),
    transform 300ms var(--ease-out);
}
.empty-state button:hover {
  color: #247a73;
  background: #eeedf2;
  transform: translate3d(0, -2px, 0);
}
.empty-state button:active {
  transform: translate3d(0, 0, 0) scale(0.98);
}
a:hover {
  text-decoration: none;
}
.action:hover {
  text-decoration: none;
  filter: brightness(0.97);
}
a:focus-visible,
button:focus-visible {
  outline: 2px solid #247a73;
  outline-offset: 3px;
}
strong {
  font-variant-numeric: tabular-nums;
}
@media (max-width: 1200px) {
  .dashboard-grid {
    grid-template-columns: minmax(200px, 1fr) minmax(180px, 0.9fr) minmax(300px, 1.4fr);
  }
  .data-panel,
  .personnel-card {
    padding: 16px;
  }
  .participation-content {
    gap: 12px;
  }
  .donut {
    width: 128px;
    height: 128px;
  }
  .donut > div {
    width: 96px;
    height: 96px;
  }
  .stat-card {
    padding: 16px;
  }
  .stat-status {
    font-size: 12px;
    padding-inline: 4px;
  }
  .updated-at {
    display: none;
  }
}
@media (max-width: 1050px) {
  .dashboard-grid {
    grid-template-columns: 1fr 1fr;
    grid-template-rows: auto;
  }
  .personnel-card {
    min-height: 240px;
  }
  .review-panel {
    grid-column: 1/3;
  }
  .review-row {
    min-height: 44px;
  }
  .department-panel {
    grid-column: 1/3;
  }
  .participation-panel {
    grid-column: 1/3;
  }
  .participation-content {
    margin-block: 24px;
  }
  .heading-actions {
    gap: 8px;
  }
}
@media (max-width: 600px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .dashboard-grid > section {
    grid-column: 1;
  }
  .compact-stats {
    grid-template-columns: 1fr 1fr;
    grid-template-rows: auto;
  }
  .stat-card {
    padding: 12px;
  }
  .stat-status {
    float: none;
    display: inline-block;
    margin-top: 8px;
  }
  .personnel-card {
    min-height: 0;
    padding: 20px;
  }
  .personnel-actions {
    margin-top: 16px;
  }
  .personnel-details {
    margin-top: 20px;
  }
  .personnel-card footer {
    margin-top: 12px;
  }
  .stat-card > div > span:last-child {
    display: none;
  }
  .stat-card p {
    margin-top: 12px;
  }
  .department-bar-row {
    grid-template-columns: 68px minmax(0, 1fr) 48px;
    gap: 8px;
  }
  .chart-axis {
    margin-left: 76px;
    margin-right: 56px;
  }
  .chart-footer > span {
    display: none;
  }
  .participation-content {
    gap: 24px;
  }
  .heading-actions .mint-action {
    padding-inline: 12px;
  }
}

/* Content determines the minimum height. Never clip text to enforce one screen. */
.personnel-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px 24px;
}
.personnel-top {
  font: var(--title-5);
  min-height: 16px;
}
.personnel-number {
  font: var(--heading-3);
}
.personnel-number small,
.personnel-details small {
  font: var(--caption-5);
}
.personnel-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin-top: 4px;
}
.personnel-actions a {
  min-width: 0;
}
.personnel-details {
  margin-top: 4px;
}
.personnel-details span {
  font: var(--caption-5);
}
.personnel-details strong {
  font: var(--title-1);
}
.personnel-card footer {
  margin-top: auto;
  padding-top: 4px;
  font: var(--caption-5);
  overflow-wrap: anywhere;
}
.compact-stats {
  grid-template-rows: repeat(2, minmax(120px, auto));
}
.stat-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-content: center;
  align-items: baseline;
  gap: 4px 8px;
  padding: 12px 16px;
}
.stat-card > div,
.stat-card p {
  grid-column: 1/-1;
  font: var(--caption-5);
}
.stat-card p {
  margin: 0;
}
.stat-card > strong {
  font: var(--heading-5);
}
.stat-status {
  margin: 0;
  padding: 4px 8px;
  font: var(--caption-5);
  overflow-wrap: anywhere;
  text-align: right;
}
.stat-card small {
  font: var(--caption-5);
}
.data-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}
.panel-heading {
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.panel-heading h2 {
  font: var(--title-4);
}
.panel-heading > a,
.updated-at,
.review-total,
.chart-legend,
.chart-footer,
.chart-axis {
  font: var(--caption-5);
}
.review-table-head {
  margin-top: 0;
  padding: 8px;
  font: var(--caption-5);
}
.review-row {
  min-height: 40px;
  line-height: 16px;
}
.review-person small {
  font: var(--caption-5);
}
.review-total {
  margin-top: auto;
}
.review-person > span:last-child {
  min-width: 0;
}
.chart-legend {
  margin: 0;
}
.department-chart {
  justify-content: flex-start;
  min-height: 0;
  max-height: 144px;
  margin-block: auto;
}
.chart-footer {
  margin-top: auto;
  padding-top: 8px;
  flex-wrap: wrap;
  gap: 8px;
}
.participation-content {
  flex-wrap: wrap;
  gap: 16px;
  margin-block: auto;
}
.donut {
  width: 120px;
  height: 120px;
}
.donut > div {
  width: 92px;
  height: 92px;
}
.donut strong {
  font: var(--title-1);
}
.donut span {
  font: var(--caption-5);
}
.donut-legend {
  gap: 8px;
  min-width: 112px;
}
.donut-legend a {
  font: var(--caption-5);
  gap: 4px 8px;
}
.donut-legend strong,
.participation-footer strong {
  font: var(--title-2);
}
.participation-footer {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  padding-top: 8px;
}
.participation-footer a {
  min-width: 0;
  padding: 0;
}
.participation-footer a + a {
  padding-left: 16px;
}
.participation-footer span {
  font: var(--caption-5);
}
.empty-state {
  flex: 1;
  min-height: 80px;
  margin: 0;
  font: var(--caption-5);
}
.action {
  font: var(--button-xsm);
  padding: 8px 12px;
  transition:
    background 200ms var(--ease-out),
    box-shadow 200ms var(--ease-out);
}
.action:hover {
  filter: none;
  box-shadow: 0 2px 8px #24203312;
}
.action:active {
  transform: translateY(1px);
}
.stat-card {
  box-shadow:
    inset 0 0 0 1px transparent,
    0 8px 20px rgb(38 35 54 / 0%);
  transition:
    background-color 300ms cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 300ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 300ms cubic-bezier(0.22, 1, 0.36, 1);
  will-change: transform;
}
.stat-card:hover {
  background: #efedf3;
  box-shadow:
    inset 0 0 0 1px rgb(255 255 255 / 88%),
    0 14px 28px rgb(38 35 54 / 10%);
  transform: translate3d(0, -3px, 0);
}
.stat-card:active {
  transform: translate3d(0, -1px, 0) scale(0.995);
}
.stat-card > div > span:last-child,
.stat-card .stat-status {
  transition:
    color 300ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 300ms cubic-bezier(0.22, 1, 0.36, 1);
}
.stat-card:hover > div > span:last-child,
.stat-card:hover .stat-status {
  color: #247a73;
  transform: translate3d(2px, 0, 0);
}
@media (max-width: 1200px) {
  .personnel-card {
    padding-inline: 16px;
  }
  .stat-card {
    padding-inline: 12px;
  }
  .stat-status {
    padding-inline: 4px;
  }
}
@media (max-width: 1050px) {
  .participation-content {
    margin-block: 16px;
  }
  .personnel-card {
    min-height: 256px;
  }
}
@media (max-width: 600px) {
  .stat-card {
    grid-template-columns: 1fr;
  }
  .stat-status {
    justify-self: start;
    text-align: left;
  }
  .personnel-card {
    padding: 16px;
  }
  .participation-content {
    gap: 16px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .action,
  .stat-card {
    transition: none;
  }
  .action:active {
    transform: none;
  }
}

.review-panel {
  gap: 8px;
}
.review-row {
  min-height: 32px;
}
.review-person > span:last-child {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.review-person small {
  overflow: hidden;
  text-overflow: ellipsis;
}
@media (max-width: 600px) {
  .review-person > span:last-child {
    display: block;
  }
  .review-row {
    min-height: 40px;
  }
}
</style>
