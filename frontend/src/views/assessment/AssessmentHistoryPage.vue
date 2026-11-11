<script setup lang="ts">
// Personal history; the optional ownerId is only supplied by an administrator route.
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AssessmentReport from './components/AssessmentReport.vue'
import {
  compareAssessmentResults,
  getAdminAssessmentHistory,
  getAssessmentHistory,
  getAssessmentResult,
} from '@/api/assessment'
import type {
  AssessmentComparisonVO,
  AssessmentHistoryVO,
  AssessmentQueryReqDTO,
  AssessmentResultVO,
} from '@/types/assessment'

const props = defineProps<{ ownerId?: number }>()
const records = ref<AssessmentHistoryVO[]>([])
const selected = ref<AssessmentHistoryVO[]>([])
const total = ref(0)
const query = reactive<AssessmentQueryReqDTO>({ pageNum: 1, pageSize: 10 })
const isLoading = ref(false)
const isDetailLoading = ref(false)
const isCompareLoading = ref(false)
const isLoadFailed = ref(false)
const detail = ref<AssessmentResultVO>()
const comparison = ref<AssessmentComparisonVO>()
const comparisonRows = computed(() =>
  comparison.value?.comparable
    ? comparison.value.first.dimensions.map((value, index) => ({
        dimension: value.dimension,
        first: value.firstPercent,
        second: comparison.value!.second.dimensions[index].firstPercent,
        delta: Number(
          (comparison.value!.second.dimensions[index].firstPercent - value.firstPercent).toFixed(2),
        ),
      }))
    : [],
)
let loadRevision = 0
// Owner can change while this component is reused by the router; clear all previous employee data.
watch(
  () => props.ownerId,
  () => {
    selected.value = []
    detail.value = undefined
    comparison.value = undefined
    query.pageNum = 1
    void getHistory()
  },
)
const getHistory = async () => {
  const revision = ++loadRevision
  isLoading.value = true
  isLoadFailed.value = false
  try {
    const response = await (props.ownerId === undefined
      ? getAssessmentHistory(query)
      : getAdminAssessmentHistory(props.ownerId, query))
    if (revision !== loadRevision) return
    records.value = response.data.data.records
    total.value = response.data.data.total
  } catch {
    if (revision === loadRevision) {
      records.value = []
      total.value = 0
      isLoadFailed.value = true
    }
  } finally {
    if (revision === loadRevision) isLoading.value = false
  }
}
const handleView = async (row: AssessmentHistoryVO) => {
  if (isDetailLoading.value) return
  isDetailLoading.value = true
  detail.value = undefined
  const ownerId = props.ownerId
  try {
    const response = await getAssessmentResult(row.id)
    if (ownerId === props.ownerId) detail.value = response.data.data
  } catch {
    /* Request layer displays errors and no prior employee report is retained. */
  } finally {
    isDetailLoading.value = false
  }
}
const handleSelect = (row: AssessmentHistoryVO) => {
  comparison.value = undefined
  if (selected.value.some((item) => item.id === row.id)) {
    selected.value = selected.value.filter((item) => item.id !== row.id)
    return
  }
  if (selected.value.length >= 2) {
    ElMessage.info('请先移除一条记录，再选择其他记录')
    return
  }
  selected.value.push(row)
}
const handleCompare = async () => {
  if (isCompareLoading.value || selected.value.length !== 2) return
  isCompareLoading.value = true
  comparison.value = undefined
  const [firstId, secondId] = selected.value.map((row) => row.id)
  const ownerId = props.ownerId
  try {
    const response = await compareAssessmentResults(firstId, secondId)
    if (
      ownerId === props.ownerId &&
      selected.value[0]?.id === firstId &&
      selected.value[1]?.id === secondId
    )
      comparison.value = response.data.data
  } catch {
    /* Request layer displays errors. */
  } finally {
    isCompareLoading.value = false
  }
}
const selectionLabel = (row: AssessmentHistoryVO) =>
  selected.value.some((item) => item.id === row.id) ? '移出对比' : '加入对比'
const formatTime = (value: string) => value.replace('T', ' ').slice(0, 16)
onMounted(getHistory)
</script>

<template>
  <main class="history-page">
    <header class="page-heading">
      <div>
        <RouterLink to="/assessment">返回人格测评</RouterLink>
        <h1>{{ ownerId === undefined ? '历次报告与结果对比' : '员工测评历史 · ' + ownerId }}</h1>
        <p>每次提交独立保留；变化仅指本次偏好刻度差异，不表示能力提升或退步。</p>
      </div>
      <el-button
        :loading="isLoading"
        @click="getHistory"
      >
        刷新历史
      </el-button>
    </header>
    <el-card class="panel">
      <el-alert
        v-if="isLoadFailed"
        title="历史记录加载失败，请刷新重试"
        type="error"
        :closable="false"
      />
      <el-table
        v-loading="isLoading"
        :data="records"
        row-key="id"
        empty-text="暂无已完成的测评"
      >
        <el-table-column
          label="提交时间"
          min-width="160"
        >
          <template #default="scope">{{ formatTime(scope.row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column
          prop="scaleName"
          label="量表"
          min-width="160"
        />
        <el-table-column
          prop="scaleVersion"
          label="版本"
          min-width="100"
        />
        <el-table-column
          prop="typeCode"
          label="偏好类型"
          width="100"
        />
        <el-table-column
          label="操作"
          min-width="190"
        >
          <template #default="scope">
            <el-button
              :disabled="isDetailLoading"
              link
              type="primary"
              @click="handleView(scope.row as AssessmentHistoryVO)"
            >
              查看报告
            </el-button>
            <el-button
              :disabled="isCompareLoading"
              link
              type="primary"
              @click="handleSelect(scope.row as AssessmentHistoryVO)"
            >
              {{ selectionLabel(scope.row as AssessmentHistoryVO) }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          :disabled="isLoading"
          layout="total, prev, pager, next"
          @current-change="getHistory"
        />
      </div>
      <p>已选 {{ selected.length }} / 2 条记录：</p>
      <div class="selection">
        <el-tag
          v-for="row in selected"
          :key="row.id"
          closable
          @close="handleSelect(row)"
        >
          {{ formatTime(row.submittedAt) }} · {{ row.typeCode }}
        </el-tag>
      </div>
      <el-button
        type="primary"
        :disabled="selected.length !== 2"
        :loading="isCompareLoading"
        @click="handleCompare"
      >
        对比两次结果
      </el-button>
    </el-card>
    <el-card
      v-if="comparison"
      class="panel"
    >
      <h2>结果对比</h2>
      <el-alert
        :title="comparison.explanation"
        :type="comparison.comparable ? 'info' : 'warning'"
        :closable="false"
      />
      <p>
        {{ formatTime(comparison.first.submittedAt) }}：{{ comparison.first.typeCode }} →
        {{ formatTime(comparison.second.submittedAt) }}：{{ comparison.second.typeCode }}
      </p>
      <el-table
        v-if="comparison.comparable"
        :data="comparisonRows"
        row-key="dimension"
      >
        <el-table-column
          prop="dimension"
          label="维度"
          width="90"
        />
        <el-table-column
          prop="first"
          label="第一条左端偏好(%)"
          min-width="170"
        />
        <el-table-column
          prop="second"
          label="第二条左端偏好(%)"
          min-width="170"
        />
        <el-table-column
          prop="delta"
          label="刻度变化(百分点)"
          min-width="170"
        />
      </el-table>
    </el-card>
    <el-card
      v-if="detail"
      v-loading="isDetailLoading"
      class="panel"
    >
      <AssessmentReport :result="detail" />
    </el-card>
  </main>
</template>

<style scoped lang="scss">
.history-page {
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
  border-radius: 20px;
  margin-top: 24px;
  overflow-wrap: anywhere;
}
.pagination-wrap {
  overflow-x: auto;
  margin: 20px 0;
}
.selection {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
p {
  line-height: 1.8;
}
</style>
