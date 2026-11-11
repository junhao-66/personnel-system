<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TalentFilters from './components/TalentFilters.vue'
import { getTalentEmployee, getTalentEmployees } from '@/api/talent'
import type { TalentEmployeeVO, TalentQueryReqDTO } from '@/types/talent'
import { parseTalentQuery, talentRouteQuery, talentTime } from '@/utils/talent'

const route = useRoute()
const router = useRouter()
const records = ref<TalentEmployeeVO[]>([])
const total = ref(0)
const isLoading = ref(false)
const isLoadFailed = ref(false)
const isDetailLoading = ref(false)
const isDetailFailed = ref(false)
const isDetailVisible = ref(false)
const detail = ref<TalentEmployeeVO>()
const query = computed(() => parseTalentQuery(route.query))
const backQuery = computed(() => talentRouteQuery({ ...query.value, pageNum: 1 }))
let sequence = 0
let detailSequence = 0
let detailId: number | undefined
const getEmployees = async () => {
  const current = ++sequence
  isLoading.value = true
  isLoadFailed.value = false
  records.value = []
  total.value = 0
  try {
    const response = await getTalentEmployees(query.value)
    if (current !== sequence) return
    const page = response.data.data
    const lastPage = Math.max(1, Math.ceil(page.total / query.value.pageSize))
    if (query.value.pageNum > lastPage) {
      await handleSearch({ ...query.value, pageNum: lastPage })
      return
    }
    records.value = page.records
    total.value = page.total
  } catch {
    if (current === sequence) isLoadFailed.value = true
  } finally {
    if (current === sequence) isLoading.value = false
  }
}
const handleSearch = async (value: TalentQueryReqDTO) => {
  const location = { path: '/admin/talent-employees', query: talentRouteQuery(value) }
  if (router.resolve(location).fullPath === route.fullPath) await getEmployees()
  else await router.push(location)
}
const handlePage = (pageNum: number) => handleSearch({ ...query.value, pageNum })
const handlePageSize = (pageSize: number) => handleSearch({ ...query.value, pageNum: 1, pageSize })
const getDetail = async () => {
  if (detailId === undefined) return
  const current = ++detailSequence
  isDetailLoading.value = true
  isDetailFailed.value = false
  detail.value = undefined
  try {
    const response = await getTalentEmployee(detailId)
    if (current === detailSequence) detail.value = response.data.data
  } catch {
    if (current === detailSequence) isDetailFailed.value = true
  } finally {
    if (current === detailSequence) isDetailLoading.value = false
  }
}
const handleDetail = (row: TalentEmployeeVO) => {
  detailId = row.userId
  isDetailVisible.value = true
  void getDetail()
}
const handleCloseDetail = () => {
  ++detailSequence
  detailId = undefined
  detail.value = undefined
  isDetailLoading.value = false
}
// A changed filter or back/forward navigation must never retain another scope's detail.
watch(
  () => route.fullPath,
  () => {
    handleCloseDetail()
    isDetailVisible.value = false
    void getEmployees()
  },
  { immediate: true },
)
</script>

<template>
  <main class="talent-employees-page">
    <header class="page-heading">
      <div>
        <RouterLink :to="{ path: '/admin/talent-dashboard', query: backQuery }">
          返回当前范围看板
        </RouterLink>
        <h1>看板人员明细</h1>
        <p>筛选条件来自统计下钻，可继续筛选并查看员工详情、历次报告。</p>
      </div>
      <el-button
        :loading="isLoading"
        @click="getEmployees"
      >
        刷新人员
      </el-button>
    </header>
    <el-card class="panel">
      <TalentFilters
        :query="query"
        :is-loading="isLoading"
        @search="handleSearch"
      />
      <el-alert
        v-if="isLoadFailed"
        title="人员列表加载失败，请刷新重试。"
        type="error"
        :closable="false"
      />
      <el-table
        v-loading="isLoading"
        :data="records"
        row-key="userId"
        empty-text="暂无符合条件的员工"
      >
        <el-table-column
          prop="realName"
          label="姓名"
          min-width="110"
        />
        <el-table-column
          prop="userName"
          label="用户名"
          min-width="120"
        />
        <el-table-column
          prop="departmentName"
          label="部门"
          min-width="130"
        />
        <el-table-column
          prop="positionName"
          label="岗位"
          min-width="130"
        />
        <el-table-column
          label="在职状态"
          width="100"
        >
          <template #default="{ row }">
            {{ row.employmentStatus === 'ACTIVE' ? '在职' : '离职' }}
          </template>
        </el-table-column>
        <el-table-column
          label="测评状态"
          width="110"
        >
          <template #default="{ row }">{{ row.latestResultId ? '已完成' : '未完成' }}</template>
        </el-table-column>
        <el-table-column
          label="最新偏好"
          width="100"
        >
          <template #default="{ row }">{{ row.typeCode || '—' }}</template>
        </el-table-column>
        <el-table-column
          prop="assessmentCount"
          label="提交次数"
          width="100"
        />
        <el-table-column
          label="操作"
          width="120"
        >
          <template #default="{ row }">
            <el-button
              :disabled="isLoading || isLoadFailed"
              link
              type="primary"
              @click="handleDetail(row as TalentEmployeeVO)"
            >
              员工详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          :disabled="isLoading"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePage"
          @size-change="handlePageSize"
        />
      </div>
      <p class="hint">
        统计与列表均取实时数据；若期间发生新提交或档案调整，请返回看板刷新。类型不是能力评价，不用于录用、晋升或绩效决定。
      </p>
    </el-card>
    <el-dialog
      v-model="isDetailVisible"
      title="员工测评详情"
      width="min(720px, 94vw)"
      @closed="handleCloseDetail"
    >
      <p
        v-if="isDetailLoading"
        role="status"
      >
        正在读取员工详情…
      </p>
      <el-alert
        v-if="isDetailFailed"
        title="详情加载失败，请重试"
        type="error"
        :closable="false"
      >
        <el-button @click="getDetail">重试详情</el-button>
      </el-alert>
      <template v-if="detail">
        <el-descriptions
          :column="1"
          border
        >
          <el-descriptions-item label="姓名">
            {{ detail.realName }}（{{ detail.userName }}）
          </el-descriptions-item>
          <el-descriptions-item label="部门 / 岗位">
            {{ detail.departmentName }} / {{ detail.positionName }}
          </el-descriptions-item>
          <el-descriptions-item label="在职 / 账号状态">
            {{ detail.employmentStatus === 'ACTIVE' ? '在职' : '离职' }} /
            {{ detail.userStatus === 'ENABLED' ? '启用' : '停用' }}
          </el-descriptions-item>
          <el-descriptions-item label="入职日期">
            {{ detail.hireDate || '未填写' }}
          </el-descriptions-item>
          <el-descriptions-item label="有效提交次数">
            {{ detail.assessmentCount }}
          </el-descriptions-item>
          <el-descriptions-item label="最新偏好">
            {{ detail.typeCode || '尚未完成测评' }}
          </el-descriptions-item>
          <el-descriptions-item label="最新提交时间">
            {{ talentTime(detail.submittedAt) }}
          </el-descriptions-item>
          <el-descriptions-item
            v-if="detail.latestResultId"
            label="量表 / 版本"
          >
            {{ detail.scaleName }} / {{ detail.scaleVersion }} / {{ detail.scoringVersion }}
          </el-descriptions-item>
        </el-descriptions>
        <p class="hint">
          完整报告含测评说明、四维偏好、成长建议和当次知识图谱依据；管理员可查看，部门负责人不默认获得此权限。
        </p>
        <el-button
          type="primary"
          @click="router.push('/admin/assessment-history/' + detail.userId)"
        >
          查看历次报告与对比
        </el-button>
        <el-button @click="router.push('/admin/job-preferences/users/' + detail.userId)">
          查看岗位偏好探索
        </el-button>
        <el-button
          @click="router.push({ path: '/admin/ai-assistant', query: { userId: detail.userId } })"
        >
          人才档案问答
        </el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped lang="scss">
.talent-employees-page {
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
h1 {
  color: var(--brand-600);
}
.panel {
  margin-top: 24px;
  border-radius: 18px;
}
.pagination-wrap {
  overflow-x: auto;
  margin-top: 24px;
  padding-bottom: 8px;
}
.hint {
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
:deep(.el-descriptions__content) {
  overflow-wrap: anywhere;
}
</style>
