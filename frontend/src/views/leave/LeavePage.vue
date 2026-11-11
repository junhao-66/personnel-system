<script setup lang="ts">
// 个人请假及负责人审批页；数据范围始终由服务端身份决定。
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import LeaveDetailModal from './components/LeaveDetailModal.vue'
import { addLeave, getLeaveOptions, getMyLeaves, getReviewLeaves, reviewLeave } from '@/api/leave'
import { useUserStore } from '@/stores/user'
import type {
  LeaveCreateReqDTO,
  LeaveOptionsVO,
  LeaveQueryReqDTO,
  LeaveStatus,
  LeaveVO,
} from '@/types/leave'
import { getLeaveMinutes, getLeaveTimeError } from '@/utils/leave'

const userStore = useUserStore()
const options = ref<LeaveOptionsVO>()
const records = ref<LeaveVO[]>([])
const total = ref(0)
const isLoading = ref(false)
const isSubmitting = ref(false)
const isLoadFailed = ref(false)
const isOptionsFailed = ref(false)
const activeTab = ref('mine')
const showRequest = ref(false)
const selectedRecord = ref<LeaveVO>()
const formRef = ref<FormInstance>()
const query = reactive<LeaveQueryReqDTO>({ pageNum: 1, pageSize: 10 })
const form = reactive<LeaveCreateReqDTO>({ leaveType: '', startTime: '', endTime: '', reason: '' })
const labels: Record<LeaveStatus, string> = {
  PENDING: '待审批',
  APPROVED: '已批准',
  REJECTED: '已拒绝',
}
const rules: FormRules = {
  leaveType: [{ required: true, message: '请选择请假类型', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  reason: [{ required: true, whitespace: true, message: '请填写请假原因', trigger: 'blur' }],
}
const isManager = computed(() => userStore.user?.roleCode === 'DEPARTMENT_MANAGER')
const isReview = computed(() => activeTab.value === 'reviews' && isManager.value)
const isUnavailable = computed(
  () => !options.value || !!options.value.unavailableReason || isOptionsFailed.value,
)
const isFormDirty = computed(
  () => !!(form.leaveType || form.startTime || form.endTime || form.reason),
)
const durationMinutes = computed(() => getLeaveMinutes(form.startTime, form.endTime))
const timeError = computed(() =>
  form.startTime && form.endTime && options.value
    ? getLeaveTimeError(
        form.startTime,
        form.endTime,
        options.value.minimumMinutes,
        options.value.crossDayAllowed,
      )
    : '',
)
let listRequestId = 0

const getOptions = async () => {
  isOptionsFailed.value = false
  try {
    options.value = (await getLeaveOptions()).data.data
  } catch {
    isOptionsFailed.value = true
  }
}
const getRecords = async () => {
  selectedRecord.value = undefined
  const requestId = ++listRequestId
  isLoading.value = true
  isLoadFailed.value = false
  try {
    const response = await (isReview.value ? getReviewLeaves(query) : getMyLeaves(query))
    if (requestId !== listRequestId) return
    records.value = response.data.data.records
    total.value = response.data.data.total
  } catch {
    if (requestId === listRequestId) {
      isLoadFailed.value = true
      records.value = []
      total.value = 0
    }
  } finally {
    if (requestId === listRequestId) isLoading.value = false
  }
}
const handleQuery = async () => {
  query.pageNum = 1
  await getRecords()
}
const handleReset = async () => {
  query.status = undefined
  await handleQuery()
}
const handleTab = async () => {
  query.status = undefined
  await handleQuery()
}
const handleRefresh = async () => {
  await Promise.all([getOptions(), getRecords()])
}
const handleSubmit = async () => {
  if (
    isSubmitting.value ||
    isUnavailable.value ||
    !(await formRef.value?.validate().catch(() => false))
  )
    return
  if (timeError.value) {
    ElMessage.warning(timeError.value)
    return
  }
  isSubmitting.value = true
  try {
    await addLeave({ ...form, reason: form.reason.trim() })
    ElMessage.success('请假申请已提交')
    showRequest.value = false
    formRef.value?.resetFields()
    await handleQuery()
  } catch {
    /* 请求层显示服务端重叠、权限或网络错误，保留表单方便重试。 */
  } finally {
    isSubmitting.value = false
  }
}
const handleReview = async (row: LeaveVO, status: 'APPROVED' | 'REJECTED') => {
  if (isSubmitting.value || isLoading.value) return
  isSubmitting.value = true
  try {
    let reviewComment = ''
    try {
      if (status === 'REJECTED') {
        const result = await ElMessageBox.prompt('请填写拒绝原因（最多500字）', '拒绝请假', {
          confirmButtonText: '确认拒绝',
          cancelButtonText: '取消',
          inputType: 'textarea',
          inputValidator: (value: string) =>
            (!!value?.trim() && value.length <= 500) || '请填写1至500字的拒绝原因',
        })
        reviewComment = result.value.trim()
      } else
        await ElMessageBox.confirm(`确认批准${row.realName || '该员工'}的请假申请？`, '批准请假', {
          confirmButtonText: '确认批准',
          cancelButtonText: '取消',
        })
    } catch {
      return
    }
    try {
      await reviewLeave(row.id, { status, reviewComment })
      ElMessage.success('审批结果已保存')
    } catch {
      /* 请求可能已被其他窗口处理，刷新以服务端状态为准。 */
    }
    await getRecords()
  } finally {
    isSubmitting.value = false
  }
}
const formatTime = (value?: string) => value?.replace('T', ' ').slice(0, 16) || '—'
const handleDetail = (row: LeaveVO) => {
  selectedRecord.value = { ...row }
}
const handleClear = async () => {
  if (isSubmitting.value || !isFormDirty.value) return
  try {
    await ElMessageBox.confirm('清空尚未提交的请假申请？', '清空请假表单', {
      confirmButtonText: '确认清空',
      cancelButtonText: '继续填写',
    })
  } catch {
    return
  }
  formRef.value?.resetFields()
}
const handleBeforeUnload = (event: BeforeUnloadEvent) => {
  if (isFormDirty.value || isSubmitting.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
onBeforeRouteLeave(async () => {
  if (!localStorage.getItem('accessToken')) return true
  if (isSubmitting.value) {
    ElMessage.info('正在提交或审批，请等待处理完成')
    return false
  }
  if (!isFormDirty.value) return true
  try {
    await ElMessageBox.confirm('请假申请尚未提交，离开将丢失本页填写内容。', '未提交的请假申请', {
      confirmButtonText: '放弃并离开',
      cancelButtonText: '继续填写',
    })
    return true
  } catch {
    return false
  }
})
onMounted(async () => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  try {
    await userStore.refreshUser()
    await handleRefresh()
  } catch {
    isLoadFailed.value = true
    isOptionsFailed.value = true
  }
})
onBeforeUnmount(() => {
  listRequestId++
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<template>
  <main class="leave-page">
    <header class="page-heading">
      <div>
        <RouterLink to="/workbench">返回个人工作台</RouterLink>
        <h1>请假与审批</h1>
        <p>提交个人请假、查看结果；部门负责人仅审批当前负责员工的申请。</p>
      </div>
      <el-button
        :loading="isLoading"
        :disabled="isSubmitting"
        @click="handleRefresh"
      >
        刷新请假数据
      </el-button>
    </header>
    <nav
      class="section-navigation"
      aria-label="请假办理"
    >
      <button
        type="button"
        :aria-pressed="!showRequest"
        @click="showRequest = false"
      >
        申请记录与审批
      </button>
      <button
        type="button"
        :aria-pressed="showRequest"
        @click="showRequest = true"
      >
        发起请假申请
      </button>
    </nav>
    <el-card
      v-show="showRequest"
      class="leave-panel"
    >
      <h2>提交请假申请</h2>
      <el-alert
        v-if="isOptionsFailed"
        title="请假规则加载失败，请刷新后重试"
        type="error"
        :closable="false"
      />
      <el-alert
        v-else-if="options?.unavailableReason"
        :title="options.unavailableReason"
        type="warning"
        :closable="false"
      />
      <p
        v-if="options"
        class="hint"
      >
        审批负责人：{{ options.approverName || '暂不可用' }}；时间按 {{ options.timeZone }}，时长为
        {{ options.minimumMinutes }} 分钟的整数倍，{{
          options.crossDayAllowed ? '允许' : '不允许'
        }}跨日申请。
      </p>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        :disabled="isSubmitting || isUnavailable"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <div class="form-grid">
          <el-form-item
            label="请假类型"
            prop="leaveType"
          >
            <el-select
              v-model="form.leaveType"
              placeholder="请选择请假类型"
            >
              <el-option
                v-for="item in options?.leaveTypes || []"
                :key="item"
                :label="item"
                :value="item"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            label="开始时间"
            prop="startTime"
          >
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:00"
              placeholder="选择开始时间"
            />
          </el-form-item>
          <el-form-item
            label="结束时间"
            prop="endTime"
          >
            <el-date-picker
              v-model="form.endTime"
              type="datetime"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:00"
              placeholder="选择结束时间"
            />
          </el-form-item>
        </div>
        <p
          v-if="durationMinutes !== undefined && durationMinutes > 0"
          role="status"
          class="hint"
        >
          日历时长：{{ durationMinutes }} 分钟。包含非工作时间；实际考勤覆盖按已批准的工作时段计算。
        </p>
        <el-alert
          v-if="timeError"
          :title="timeError"
          type="warning"
          :closable="false"
        />
        <el-form-item
          label="请假原因"
          prop="reason"
        >
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请填写请假原因"
          />
        </el-form-item>
        <el-button
          type="primary"
          :loading="isSubmitting"
          :disabled="isUnavailable"
          @click="handleSubmit"
        >
          提交请假
        </el-button>
        <el-button
          :disabled="!isFormDirty || isSubmitting"
          @click="handleClear"
        >
          清空申请表单
        </el-button>
      </el-form>
    </el-card>
    <el-card
      v-show="!showRequest"
      class="leave-panel"
    >
      <el-tabs
        v-model="activeTab"
        @tab-change="handleTab"
      >
        <el-tab-pane
          label="我的请假"
          name="mine"
        />
        <el-tab-pane
          v-if="isManager"
          label="员工请假审批"
          name="reviews"
        />
      </el-tabs>
      <el-form
        :model="query"
        inline
        :disabled="isLoading || isSubmitting"
        @submit.prevent="handleQuery"
      >
        <el-form-item label="审批状态">
          <el-select
            v-model="query.status"
            clearable
            placeholder="全部状态"
            class="status-select"
          >
            <el-option
              v-for="(label, status) in labels"
              :key="status"
              :label="label"
              :value="status"
            />
          </el-select>
        </el-form-item>
        <el-button
          type="primary"
          @click="handleQuery"
        >
          查询请假
        </el-button>
        <el-button @click="handleReset">重置筛选</el-button>
      </el-form>
      <el-alert
        v-if="isLoadFailed"
        title="请假记录加载失败，请刷新重试"
        type="error"
        :closable="false"
      />
      <el-table
        v-loading="isLoading"
        :data="records"
        row-key="id"
        empty-text="暂无请假记录"
      >
        <el-table-column
          label="详情"
          width="100"
          fixed="left"
        >
          <template #default="scope">
            <el-button
              link
              type="primary"
              @click="handleDetail(scope.row as LeaveVO)"
            >
              查看详情
            </el-button>
          </template>
        </el-table-column>
        <el-table-column
          v-if="isReview"
          prop="realName"
          label="申请人"
          min-width="100"
        />
        <el-table-column
          prop="leaveType"
          label="类型"
          width="80"
        />
        <el-table-column
          label="开始时间"
          min-width="160"
        >
          <template #default="scope">{{ formatTime(scope.row.startTime) }}</template>
        </el-table-column>
        <el-table-column
          label="结束时间"
          min-width="160"
        >
          <template #default="scope">{{ formatTime(scope.row.endTime) }}</template>
        </el-table-column>
        <el-table-column
          prop="reason"
          label="申请原因"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          label="状态"
          width="100"
        >
          <template #default="scope">{{ labels[scope.row.status as LeaveStatus] }}</template>
        </el-table-column>
        <el-table-column
          prop="reviewComment"
          label="审批意见"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          label="处理时间"
          min-width="160"
        >
          <template #default="scope">{{ formatTime(scope.row.reviewedAt) }}</template>
        </el-table-column>
        <el-table-column
          v-if="isReview"
          label="操作"
          width="140"
          fixed="right"
        >
          <template #default="scope">
            <template v-if="scope.row.status === 'PENDING'">
              <el-button
                :disabled="isSubmitting"
                link
                type="primary"
                @click="handleReview(scope.row as LeaveVO, 'APPROVED')"
              >
                批准
              </el-button>
              <el-button
                :disabled="isSubmitting"
                link
                type="danger"
                @click="handleReview(scope.row as LeaveVO, 'REJECTED')"
              >
                拒绝
              </el-button>
            </template>
            <span v-else>已处理</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          :disabled="isLoading || isSubmitting"
          layout="total, prev, pager, next"
          @current-change="getRecords"
        />
      </div>
    </el-card>
    <LeaveDetailModal
      :record="selectedRecord"
      @close="selectedRecord = undefined"
    />
  </main>
</template>

<style scoped lang="scss">
.leave-page {
  max-width: 1280px;
  margin: auto;
  padding: clamp(16px, 4vw, 48px);
}
.page-heading {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  color: var(--text-strong);
}
h1 {
  color: var(--text-strong);
}
h2 {
  font-size: 20px;
  color: var(--brand-600);
}
.leave-panel {
  border-radius: 20px;
  margin-top: 24px;
}
.hint {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.8;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.status-select {
  width: 150px;
}
.pagination-wrap {
  overflow-x: auto;
  margin-top: 20px;
}
:deep(.el-date-editor.el-input) {
  width: 100%;
}
:deep(.el-table__fixed-right) {
  background: white;
}
@media (max-width: 760px) {
  .form-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }
}
</style>
