<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AttendanceRecords from '@/components/attendance/AttendanceRecords.vue'
import SystemAnnouncement from '@/components/system/SystemAnnouncement.vue'
import { clockIn, clockOut, getTodayAttendance } from '@/api/attendance'
import { useUserStore } from '@/stores/user'
import type { TodayAttendanceVO } from '@/types/attendance'
import { attendanceStatusLabel, formatAttendanceTime, shortTime } from '@/utils/attendance'

const userStore = useUserStore()
const today = ref<TodayAttendanceVO>()
const activeSection = ref('today')
const recordsRef = ref<InstanceType<typeof AttendanceRecords>>()
const isLoading = ref(false)
const isLoadFailed = ref(false)
const isSubmitting = ref(false)

const loadToday = async () => {
  isLoading.value = true
  isLoadFailed.value = false
  try {
    today.value = (await getTodayAttendance()).data.data
  } catch {
    isLoadFailed.value = true
  } finally {
    isLoading.value = false
  }
}
const handleClock = async (type: 'in' | 'out') => {
  if (isSubmitting.value || isLoading.value || isLoadFailed.value || !today.value) return
  isSubmitting.value = true
  try {
    if (type === 'out') {
      try {
        await ElMessageBox.confirm(
          '下班打卡将保存当前服务器时间。系统扣除已批准请假后，早于剩余工作时段结束时间会提示早退。是否继续？',
          '确认下班打卡',
          { confirmButtonText: '确认打卡', cancelButtonText: '取消', type: 'warning' },
        )
      } catch {
        return
      }
    }
    try {
      if (type === 'in') await clockIn()
      else await clockOut()
      ElMessage.success(type === 'in' ? '上班打卡成功' : '下班打卡成功')
    } catch {
      // 请求层提示错误；网络超时可能已经写入，刷新后以服务端结果为准。
    }
    await Promise.all([loadToday(), recordsRef.value?.reload()])
  } finally {
    isSubmitting.value = false
  }
}
onMounted(async () => {
  try {
    await userStore.refreshUser()
    await loadToday()
  } catch {
    isLoadFailed.value = true
  }
})
</script>

<template>
  <main class="workbench-page">
    <div class="workbench-content">
      <header class="welcome">
        <h1>{{ userStore.user?.realName || '员工' }} 的工作台</h1>
        <p>处理日常考勤与请假，查看账号资料，按需探索MBTI人格偏好。</p>
      </header>
      <SystemAnnouncement />
      <section
        class="workspace-shortcuts"
        aria-label="常用功能"
      >
        <RouterLink
          to="/leave"
          class="shortcut-featured"
        >
          <span class="shortcut-index">01 / 日常协作</span>
          <h2>请假与审批</h2>
          <p>提交请假申请，跟进审批进度。</p>
          <span class="shortcut-action">进入请假中心</span>
        </RouterLink>
        <RouterLink to="/assessment/history">
          <span class="shortcut-index">02 / 成长记录</span>
          <h2>历次测评报告</h2>
          <p>回看每一次探索，理解偏好的变化。</p>
          <span class="shortcut-action">查看我的报告</span>
        </RouterLink>
        <RouterLink to="/mbti">
          <span class="shortcut-index">03 / 了解自己</span>
          <h2>MBTI人格偏好</h2>
          <p>先了解各类人格，再自愿参与个人测评。</p>
          <span class="shortcut-action">进入人格导览</span>
        </RouterLink>
      </section>
      <nav
        class="section-navigation"
        aria-label="工作台内容"
      >
        <button
          type="button"
          :aria-pressed="activeSection === 'today'"
          @click="activeSection = 'today'"
        >
          今日打卡
        </button>
        <button
          type="button"
          :aria-pressed="activeSection === 'history'"
          @click="activeSection = 'history'"
        >
          我的考勤记录
        </button>
      </nav>
      <el-card
        v-show="activeSection === 'today'"
        v-loading="isLoading"
        class="today-panel"
      >
        <el-skeleton
          v-if="isLoading && !today"
          :rows="4"
          animated
          aria-label="正在加载今日考勤"
        />
        <div class="panel-heading">
          <div>
            <h2>今日考勤</h2>
            <p>{{ today?.attendanceDate || '正在获取日期' }} · Asia/Shanghai</p>
          </div>
          <el-button
            :loading="isLoading"
            :disabled="isSubmitting"
            @click="loadToday"
          >
            刷新今日状态
          </el-button>
        </div>
        <el-alert
          v-if="isLoadFailed"
          title="今日考勤加载失败，暂不可打卡，请刷新后重试。"
          type="error"
          :closable="false"
        />
        <template v-if="today">
          <div class="schedule-line">
            <span>
              当日作息 {{ shortTime(today.workStartTime) }}—{{ shortTime(today.workEndTime) }}
            </span>
            <el-tag :type="today.restDay ? 'info' : 'success'">
              {{ today.restDay ? '周末休息日' : '工作日' }}
            </el-tag>
            <el-tag>
              {{ attendanceStatusLabel(today.dayStatus?.status ?? today.record?.status) }}
            </el-tag>
          </div>
          <div class="clock-grid">
            <article class="clock-card">
              <h3>上班打卡</h3>
              <p>{{ formatAttendanceTime(today.record?.clockInTime) }}</p>
              <el-button
                type="primary"
                :loading="isSubmitting"
                :disabled="!!today.record?.clockInTime || isLoading || isLoadFailed"
                @click="handleClock('in')"
              >
                {{ today.record?.clockInTime ? '已完成上班打卡' : '上班打卡' }}
              </el-button>
            </article>
            <article class="clock-card">
              <h3>下班打卡</h3>
              <p>{{ formatAttendanceTime(today.record?.clockOutTime) }}</p>
              <el-button
                type="primary"
                :loading="isSubmitting"
                :disabled="
                  !today.record?.clockInTime ||
                  !!today.record?.clockOutTime ||
                  isLoading ||
                  isLoadFailed
                "
                @click="handleClock('out')"
              >
                {{ today.record?.clockOutTime ? '已完成下班打卡' : '下班打卡' }}
              </el-button>
            </article>
          </div>
          <details class="attendance-notes">
            <summary>打卡规则与服务器时间</summary>
            <div>
              <p class="hint">
                最近获取的服务器时间：{{
                  formatAttendanceTime(today.serverTime)
                }}。实际打卡时间由服务器记录。
              </p>
              <p
                v-if="today.dayStatus"
                class="hint"
              >
                今日工作时段内已批请假 {{ today.dayStatus.approvedLeaveMinutes || 0 }} 分钟。{{
                  today.dayStatus.requiredStart
                    ? '扣除请假后应到岗起止：' +
                      formatAttendanceTime(today.dayStatus.requiredStart) +
                      ' 至 ' +
                      formatAttendanceTime(today.dayStatus.requiredEnd)
                    : '今日无需到岗；仍可自愿记录打卡。'
                }}
              </p>
              <p class="hint">
                周末双休，休息日允许记录打卡但不计迟到早退；当天上、下班各只能打卡一次，不支持补卡和跨日班次。
              </p>
            </div>
          </details>
        </template>
      </el-card>
      <el-card
        v-show="activeSection === 'history'"
        class="history-panel"
      >
        <AttendanceRecords ref="recordsRef" />
      </el-card>
    </div>
  </main>
</template>

<style scoped lang="scss">
.workbench-page {
  height: calc(100dvh - 152px);
  min-height: 560px;
  overflow: hidden;
}
.workbench-content {
  display: flex;
  flex-direction: column;
  max-width: 1200px;
  height: 100%;
  margin: auto;
  padding: 12px 24px 16px;
  overflow: hidden;
}
.welcome {
  padding: 0 0 12px;
}
h1 {
  color: var(--text-strong);
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
  margin: 0 0 8px;
  letter-spacing: 0;
}
.welcome > p:last-child {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 20px;
}
.workspace-shortcuts {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr;
  gap: 12px;
  margin: 4px 0 12px;
}
.workspace-shortcuts > a {
  display: flex;
  flex-direction: column;
  min-height: 124px;
  padding: 14px 16px;
  border: 1px solid var(--border-default);
  border-radius: 12px;
  background: var(--bg-card);
  color: var(--text-regular);
  text-decoration: none;
  transition:
    border-color 300ms var(--ease-out),
    box-shadow 300ms var(--ease-out),
    transform 300ms var(--ease-out);
}
.workspace-shortcuts > a:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
  transform: translate3d(0, -2px, 0);
}
.workspace-shortcuts > a.shortcut-featured {
  border-color: var(--brand-300);
  background: var(--brand-50);
}
.shortcut-index {
  font-size: 12px;
  line-height: 16px;
  color: var(--text-disabled);
  letter-spacing: 0;
}
.workspace-shortcuts h2 {
  margin: 8px 0 4px;
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
  color: var(--text-strong);
}
.workspace-shortcuts p {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-secondary);
}
.shortcut-action {
  display: flex;
  justify-content: space-between;
  margin-top: auto;
  color: var(--brand-600);
  font-size: 13px;
  line-height: 20px;
}
.section-navigation {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin: 0 0 8px;
  padding: 4px;
  background: var(--bg-subtle);
  border-radius: 8px;
  width: fit-content;
  max-width: 100%;
}
.section-navigation button {
  font: inherit;
  font-size: 13px;
  line-height: 20px;
  padding: 6px 12px;
  border: 0;
  border-radius: 6px;
  color: var(--text-secondary);
  background: transparent;
  cursor: pointer;
}
.section-navigation button[aria-pressed='true'] {
  background: var(--bg-card);
  color: var(--text-strong);
  box-shadow: var(--shadow-xs);
}
.section-navigation button:hover {
  color: var(--text-strong);
}
.today-panel,
.history-panel {
  flex: 1;
  min-height: 0;
  border-radius: 12px;
  margin-top: 0;
  overflow: auto;
}
.today-panel :deep(.el-card__body) {
  padding: 16px 20px;
}
.panel-heading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  justify-content: space-between;
}
.panel-heading h2 {
  margin: 0 0 4px;
  color: var(--text-strong);
  font-size: 16px;
  line-height: 24px;
}
.panel-heading p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
}
.schedule-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
  color: var(--text-regular);
  font-size: 13px;
}
.clock-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.clock-card {
  padding: 14px 16px;
  background: var(--bg-subtle);
  border: 1px solid var(--border-light);
  border-radius: 12px;
}
.clock-card h3 {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 16px;
  font-weight: 500;
}
.clock-card p {
  min-height: 24px;
  color: var(--text-strong);
  font-size: 24px;
  line-height: 32px;
  margin: 4px 0 8px;
}
.hint {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.7;
  margin: 8px 0 0;
}
.attendance-notes {
  margin-top: 10px;
  color: var(--text-secondary);
  font-size: 12px;
}
.attendance-notes summary {
  width: fit-content;
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--bg-subtle);
  color: var(--text-regular);
  cursor: pointer;
  transition:
    background-color 300ms var(--ease-out),
    color 300ms var(--ease-out);
}
.attendance-notes summary:hover {
  background: #eeedf2;
  color: var(--text-strong);
}
.attendance-notes > div {
  padding-top: 4px;
}
:deep(.el-tag--success) {
  --el-tag-text-color: var(--sem-success);
  --el-tag-bg-color: var(--sem-success-bg);
}
@media (max-width: 768px) {
  .workbench-page {
    height: auto;
    min-height: 0;
    overflow: visible;
  }
  .workbench-content {
    display: block;
    height: auto;
    padding: 20px 16px;
    overflow: visible;
  }
  .workspace-shortcuts {
    grid-template-columns: 1fr;
  }
  .clock-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .clock-card {
    padding: 16px;
  }
  .today-panel,
  .history-panel {
    overflow: visible;
  }
}
</style>
