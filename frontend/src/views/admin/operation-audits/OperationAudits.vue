<script setup lang="ts">
// Read-only history of successful business mutations. No request body or sensitive field values are stored.
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getOperationAudits } from '@/api/operationAudit'
import type { OperationAuditQueryReqDTO, OperationAuditVO } from '@/types/operationAudit'
import {
  auditFieldLabels,
  auditOperationLabels,
  auditRoleLabels,
  auditTargetLabels,
  auditTime,
} from '@/utils/operationAudit'

const filters = reactive({
  actorUserId: '',
  targetId: '',
  targetType: '',
  operation: '',
  dates: [] as string[],
})
const query = reactive<OperationAuditQueryReqDTO>({ pageNum: 1, pageSize: 10 })
const records = ref<OperationAuditVO[]>([])
const total = ref(0)
const isLoading = ref(false)
const isLoadFailed = ref(false)
let sequence = 0

const getAuditLogs = async () => {
  const current = ++sequence
  isLoading.value = true
  isLoadFailed.value = false
  records.value = []
  try {
    const response = await getOperationAudits({ ...query })
    if (current !== sequence) return
    records.value = response.data.data.records
    total.value = response.data.data.total
  } catch {
    if (current === sequence) {
      isLoadFailed.value = true
      total.value = 0
    }
  } finally {
    if (current === sequence) isLoading.value = false
  }
}
const handleSearch = () => {
  for (const value of [filters.actorUserId, filters.targetId]) {
    if (value && (!/^[1-9]\d*$/.test(value) || !Number.isSafeInteger(Number(value)))) {
      ElMessage.warning('编号须为有效的正整数')
      return
    }
  }
  Object.assign(query, {
    pageNum: 1,
    actorUserId: filters.actorUserId ? Number(filters.actorUserId) : undefined,
    targetId: filters.targetId ? Number(filters.targetId) : undefined,
    targetType: filters.targetType || undefined,
    operation: filters.operation || undefined,
    startDate: filters.dates?.[0],
    endDate: filters.dates?.[1],
  })
  void getAuditLogs()
}
const handleReset = () => {
  Object.assign(filters, {
    actorUserId: '',
    targetId: '',
    targetType: '',
    operation: '',
    dates: [],
  })
  handleSearch()
}
const handlePage = (page: number) => {
  query.pageNum = page
  void getAuditLogs()
}
const handlePageSize = (size: number) => {
  query.pageSize = size
  query.pageNum = 1
  void getAuditLogs()
}
onMounted(getAuditLogs)
onBeforeUnmount(() => {
  ++sequence
})
</script>

<template>
  <main class="audit-page">
    <header class="page-heading">
      <div>
        <h1>操作审计</h1>
        <p>查看人事、请假、内容发布及考勤规则的成功变更记录，时间按 Asia/Shanghai 显示。</p>
      </div>
      <el-button
        :loading="isLoading"
        @click="getAuditLogs"
      >
        刷新记录
      </el-button>
    </header>
    <details>
      <summary>记录范围与隐私说明</summary>
      <el-alert
        title="各类操作从对应审计功能启用后开始记录，不追补历史。记录操作者编号、对象编号和变更字段名，不保存请假原因、审批意见或版本内容原文；不包含登录失败、打卡、测评答案或报告阅读记录。"
        type="info"
        :closable="false"
      />
    </details>
    <el-card class="panel">
      <el-form
        class="audit-filters"
        label-position="top"
        @submit.prevent="handleSearch"
      >
        <el-form-item label="操作者编号">
          <el-input
            v-model="filters.actorUserId"
            placeholder="输入操作者编号"
            clearable
          />
        </el-form-item>
        <el-form-item label="对象编号">
          <el-input
            v-model="filters.targetId"
            placeholder="输入对象编号"
            clearable
          />
        </el-form-item>
        <el-form-item label="对象类型">
          <el-select
            v-model="filters.targetType"
            placeholder="全部对象"
            clearable
          >
            <el-option
              v-for="(label, value) in auditTargetLabels"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select
            v-model="filters.operation"
            placeholder="全部操作"
            clearable
          >
            <el-option
              v-for="(label, value) in auditOperationLabels"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="操作日期"
          class="date-filter"
        >
          <el-date-picker
            v-model="filters.dates"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <div class="filter-actions">
          <el-button
            type="primary"
            native-type="submit"
            :loading="isLoading"
          >
            查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </el-form>
      <el-alert
        v-if="isLoadFailed"
        title="审计记录加载失败，请刷新重试。"
        type="error"
        :closable="false"
      />
      <el-table
        v-loading="isLoading"
        :data="records"
        row-key="id"
        empty-text="暂无符合条件的审计记录"
      >
        <el-table-column
          prop="id"
          label="记录编号"
          width="100"
        />
        <el-table-column
          label="操作时间"
          min-width="180"
        >
          <template #default="{ row }">{{ auditTime(row.occurredAt) }}</template>
        </el-table-column>
        <el-table-column
          label="操作者"
          min-width="150"
        >
          <template #default="{ row }">
            {{ row.actorUserId ? '#' + row.actorUserId : '—' }} ·
            {{ auditRoleLabels[row.actorRole] || row.actorRole }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          min-width="150"
        >
          <template #default="{ row }">
            {{ auditOperationLabels[row.operation] || row.operation }}
          </template>
        </el-table-column>
        <el-table-column
          label="对象"
          min-width="140"
        >
          <template #default="{ row }">
            {{ auditTargetLabels[row.targetType] || row.targetType }} #{{ row.targetId }}
          </template>
        </el-table-column>
        <el-table-column
          label="变更字段（不含原值）"
          min-width="230"
        >
          <template #default="{ row }">
            <span
              v-for="field in row.changedFields"
              :key="field"
              class="field-tag"
            >
              {{ auditFieldLabels[field] || field }}
            </span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-if="!isLoadFailed"
          :disabled="isLoading"
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePage"
          @size-change="handlePageSize"
        />
      </div>
    </el-card>
  </main>
</template>

<style scoped lang="scss">
.audit-page {
  padding: clamp(16px, 3vw, 36px);
  max-width: 1500px;
  margin: auto;
  color: var(--text-primary);
  min-width: 0;
}
h1 {
  color: var(--brand-600);
}
p {
  line-height: 1.8;
}
.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.panel {
  margin-top: 20px;
  border-radius: 14px;
}
.audit-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: end;
}
.audit-filters :deep(.el-form-item) {
  flex: 1 1 160px;
  min-width: 0;
}
.audit-filters :deep(.date-filter) {
  flex: 2 1 280px;
}
.audit-filters :deep(.el-date-editor) {
  width: 100%;
  min-width: 0;
}
.filter-actions {
  display: flex;
  margin-bottom: 18px;
}
.field-tag {
  display: inline-block;
  background: var(--bg-subtle);
  border-radius: 6px;
  margin: 3px;
  padding: 2px 6px;
}
.pagination-wrap {
  overflow-x: auto;
  margin-top: 16px;
}
@media (max-width: 520px) {
  .audit-filters :deep(.el-form-item) {
    flex-basis: 100%;
  }
}
</style>
