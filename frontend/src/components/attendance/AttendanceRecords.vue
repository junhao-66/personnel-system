<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useViewportTableHeight } from '@/utils/viewportTable'
import { getAdminAttendanceRecords, getMyAttendanceRecords } from '@/api/attendance'
import { getDepartments } from '@/api/organization'
import type { AttendanceEmployeeVO, AttendanceQueryDTO } from '@/types/attendance'
import type { DepartmentVO } from '@/types/organization'
import { attendanceStatusLabel, formatAttendanceTime, shortTime } from '@/utils/attendance'

const props = withDefaults(defineProps<{ adminMode?: boolean }>(), { adminMode: false })
const tableAnchor = ref<HTMLElement>()
const tableHeight = useViewportTableHeight(tableAnchor)
const records = ref<AttendanceEmployeeVO[]>([])
const departments = ref<DepartmentVO[]>([])
const total = ref(0)
const isLoading = ref(false)
const isLoadFailed = ref(false)
const isDepartmentFailed = ref(false)
const filters = reactive({
  dateRange: [] as string[],
  keyword: '',
  departmentId: undefined as number | undefined,
  includeMissing: true,
})
const query = reactive<AttendanceQueryDTO>({ pageNum: 1, pageSize: 10, includeMissing: true })
let requestSequence = 0
const getRowKey = (row: AttendanceEmployeeVO) =>
  `${row.attendance.userId}-${row.attendance.attendanceDate}`

const loadRecords = async () => {
  const sequence = ++requestSequence
  isLoading.value = true
  isLoadFailed.value = false
  try {
    const response = props.adminMode
      ? await getAdminAttendanceRecords({ ...query })
      : await getMyAttendanceRecords({ ...query })
    if (sequence !== requestSequence) return
    const lastPage = Math.max(1, Math.ceil(response.data.data.total / query.pageSize))
    if (query.pageNum > lastPage) {
      query.pageNum = lastPage
      await loadRecords()
      return
    }
    records.value = response.data.data.records
    total.value = response.data.data.total
  } catch {
    if (sequence === requestSequence) isLoadFailed.value = true
  } finally {
    if (sequence === requestSequence) isLoading.value = false
  }
}
const loadDepartments = async () => {
  try {
    departments.value = (await getDepartments()).data.data
    isDepartmentFailed.value = false
  } catch {
    isDepartmentFailed.value = true
  }
}
const handleSearch = () => {
  const range = filters.dateRange || []
  Object.assign(query, {
    pageNum: 1,
    startDate: range[0],
    endDate: range[1],
    keyword: props.adminMode ? filters.keyword.trim() || undefined : undefined,
    departmentId: props.adminMode ? filters.departmentId || undefined : undefined,
    includeMissing: filters.includeMissing,
  })
  void loadRecords()
}
const handleReset = () => {
  Object.assign(filters, {
    dateRange: [],
    keyword: '',
    departmentId: undefined,
    includeMissing: true,
  })
  handleSearch()
}
const handleSizeChange = () => {
  query.pageNum = 1
  void loadRecords()
}
defineExpose({ reload: loadRecords })
onMounted(() => {
  void loadRecords()
  if (props.adminMode) void loadDepartments()
})
</script>

<template>
  <section
    class="attendance-records"
    aria-label="考勤记录"
  >
    <header class="records-heading">
      <h2>{{ adminMode ? '员工考勤记录' : '我的考勤记录' }}</h2>
      <p>默认展示本月至今日的每日状态，可查询最长366天区间；原始打卡时间不可修改。</p>
    </header>
    <el-form
      :model="filters"
      label-position="top"
      @submit.prevent="handleSearch"
      class="record-filters"
    >
      <el-form-item label="日期范围">
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          unlink-panels
        />
      </el-form-item>
      <el-form-item
        v-if="adminMode"
        label="姓名 / 用户名"
      >
        <el-input
          v-model="filters.keyword"
          placeholder="输入员工关键词"
          maxlength="50"
          clearable
        />
      </el-form-item>
      <el-form-item
        v-if="adminMode"
        label="当前部门"
      >
        <el-select
          v-model="filters.departmentId"
          placeholder="全部部门"
          clearable
        >
          <el-option
            v-for="item in departments"
            :key="item.id"
            :label="item.departmentName"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <div class="filter-actions">
        <el-button
          type="primary"
          native-type="submit"
          :loading="isLoading"
        >
          查询记录
        </el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <el-form-item label="未打卡日期">
        <el-switch
          v-model="filters.includeMissing"
          active-text="包含每日状态"
          @change="handleSearch"
        />
      </el-form-item>
    </el-form>
    <el-alert
      v-if="isDepartmentFailed"
      title="部门选项加载失败，仍可按日期查询。"
      type="warning"
      :closable="false"
    >
      <el-button
        text
        @click="loadDepartments"
      >
        重试加载部门
      </el-button>
    </el-alert>
    <el-alert
      v-if="isLoadFailed"
      title="考勤记录加载失败，当前数据可能不是最新，请重新查询。"
      type="error"
      :closable="false"
    />
    <div ref="tableAnchor"></div>
    <el-table
      v-loading="isLoading"
      :data="records"
      :max-height="tableHeight"
      :row-key="getRowKey"
      empty-text="此区间暂无考勤数据"
    >
      <el-table-column
        type="expand"
        width="48"
      >
        <template #default="{ row }">
          <dl class="attendance-row-detail">
            <div>
              <dt>登录账号</dt>
              <dd>{{ row.userName || '—' }}</dd>
            </div>
            <div>
              <dt>当日作息</dt>
              <dd>
                {{ shortTime(row.attendance.workStartTime) }}—{{
                  shortTime(row.attendance.workEndTime)
                }}
              </dd>
            </div>
            <div>
              <dt>已批请假</dt>
              <dd>{{ row.attendance.approvedLeaveMinutes || 0 }} 分钟</dd>
            </div>
            <div>
              <dt>应到岗起止</dt>
              <dd>
                {{
                  row.attendance.requiredStart
                    ? formatAttendanceTime(row.attendance.requiredStart) +
                      ' 至 ' +
                      formatAttendanceTime(row.attendance.requiredEnd)
                    : '无需到岗'
                }}
              </dd>
            </div>
            <div>
              <dt>作息依据</dt>
              <dd>
                {{
                  row.attendance.scheduleBasis === 'CURRENT_RULE'
                    ? '当前规则（参考）'
                    : '打卡时保存的规则'
                }}
              </dd>
            </div>
          </dl>
        </template>
      </el-table-column>
      <el-table-column
        v-if="adminMode"
        prop="realName"
        label="姓名"
        min-width="110"
      />

      <el-table-column
        v-if="adminMode"
        label="当前部门"
        min-width="120"
      >
        <template #default="{ row }">{{ row.departmentName || '未分配' }}</template>
      </el-table-column>
      <el-table-column
        prop="attendance.attendanceDate"
        label="日期"
        width="120"
      />
      <el-table-column
        label="上班打卡"
        min-width="132"
      >
        <template #default="{ row }">
          {{ formatAttendanceTime(row.attendance.clockInTime) }}
        </template>
      </el-table-column>
      <el-table-column
        label="下班打卡"
        min-width="180"
      >
        <template #default="{ row }">
          {{ formatAttendanceTime(row.attendance.clockOutTime) }}
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        min-width="100"
      >
        <template #default="{ row }">{{ attendanceStatusLabel(row.attendance.status) }}</template>
      </el-table-column>
    </el-table>
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :disabled="isLoading"
        layout="total, sizes, prev, pager, next"
        @current-change="loadRecords"
        @size-change="handleSizeChange"
      />
    </div>
    <details class="record-note">
      <summary>考勤计算与数据范围说明</summary>
      <p>
        Asia/Shanghai。仅已批准请假参与计算，按覆盖工作时段合并计时；缺卡在当日最后一段应到岗时间结束后判断。未打卡日期使用当前规则作为参考，仅补齐已审核、启用且在职账号在审核/建档和入职日期之后的日期；不生成原始打卡，不直接认定旷工或用于扣薪。
      </p>
    </details>
  </section>
</template>

<style scoped lang="scss">
.records-heading h2 {
  margin: 0 0 8px;
  color: var(--text-strong);
  font-size: 20px;
}
.records-heading p,
.record-note {
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.7;
}
.record-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 16px;
  margin-top: 20px;
}
.record-filters .el-form-item {
  max-width: 100%;
}
.filter-actions {
  display: flex;
  margin-bottom: 18px;
}
.pagination-wrap {
  margin-top: 20px;
  overflow-x: auto;
  padding-bottom: 8px;
}
:deep(.el-date-editor--daterange) {
  max-width: 100%;
}
@media (max-width: 600px) {
  .record-filters {
    display: block;
  }
  :deep(.el-date-editor--daterange) {
    width: 100%;
    min-width: 0;
  }
  :deep(.el-range-input) {
    min-width: 0;
  }
  .filter-actions {
    margin-top: 12px;
  }
}
</style>
