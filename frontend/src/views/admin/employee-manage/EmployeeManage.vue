<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getEmployees, updateEmployee } from '@/api/employee'
import { getDepartments, getPositions } from '@/api/organization'
import type { EmployeeQueryDTO, EmployeeUpdateDTO, EmployeeVO } from '@/types/employee'
import type { DepartmentVO, PositionVO } from '@/types/organization'
import EmployeeFolderCard from '@/components/EmployeeFolderCard.vue'

const router = useRouter()
const route = useRoute()

const employees = ref<EmployeeVO[]>([])
const departments = ref<DepartmentVO[]>([])
const positions = ref<PositionVO[]>([])
const total = ref(0)
const viewMode = ref<'folders' | 'table'>('folders')
const isLoading = ref(false)
const isLoadFailed = ref(false)
const isOptionsReady = ref(false)
const isSubmitting = ref(false)

// 详情抽屉
const isDrawerVisible = ref(false)
const selectedEmployee = ref<EmployeeVO>()

// 编辑弹窗
const isDialogVisible = ref(false)
const editingEmployee = ref<EmployeeVO>()
const formRef = ref<FormInstance>()

const filters = reactive<Omit<EmployeeQueryDTO, 'pageNum' | 'pageSize'>>({ keyword: '' })
const query = reactive<EmployeeQueryDTO>({ pageNum: 1, pageSize: 8 })

const form = reactive<EmployeeUpdateDTO>({
  realName: '',
  phone: '',
  departmentId: null,
  positionId: null,
  hireDate: null,
  employmentStatus: 'ACTIVE',
})

const rules: FormRules = {
  realName: [
    { required: true, whitespace: true, message: '请输入姓名', trigger: 'blur' },
    { max: 50, message: '姓名最多50个字符', trigger: 'blur' },
  ],
  phone: [{ max: 20, message: '联系方式最多20个字符', trigger: 'blur' }],
  employmentStatus: [{ required: true, message: '请选择在职状态', trigger: 'change' }],
}

const roleLabels: Record<string, string> = {
  ADMIN: '管理员',
  DEPARTMENT_MANAGER: '部门负责人',
  EMPLOYEE: '普通员工',
}

const availableDepartments = computed(() =>
  departments.value.filter(
    (item) => item.isEnabled === 1 || item.id === editingEmployee.value?.departmentId,
  ),
)
const availablePositions = computed(() =>
  positions.value.filter(
    (item) =>
      (item.departmentId == null || item.departmentId === form.departmentId) &&
      (item.isEnabled === 1 || item.id === editingEmployee.value?.positionId),
  ),
)

let requestSequence = 0

const loadEmployees = async () => {
  const sequence = ++requestSequence
  isLoading.value = true
  isLoadFailed.value = false
  try {
    const response = await getEmployees({ ...query })
    if (sequence !== requestSequence) return
    const lastPage = Math.max(1, Math.ceil(response.data.data.total / query.pageSize))
    if (query.pageNum > lastPage) {
      query.pageNum = lastPage
      await loadEmployees()
      return
    }
    employees.value = response.data.data.records
    total.value = response.data.data.total
  } catch {
    if (sequence === requestSequence) {
      isLoadFailed.value = true
      employees.value = []
      total.value = 0
    }
  } finally {
    if (sequence === requestSequence) isLoading.value = false
  }
}

const loadOptions = async () => {
  isOptionsReady.value = false
  try {
    const [departmentResponse, positionResponse] = await Promise.all([
      getDepartments(),
      getPositions(),
    ])
    departments.value = departmentResponse.data.data
    positions.value = positionResponse.data.data
    isOptionsReady.value = true
  } catch {
    // 请求层提示失败，禁止使用不完整字典编辑档案。
  }
}

const handleSearch = () => {
  Object.assign(query, {
    pageNum: 1,
    keyword: filters.keyword?.trim() || undefined,
    departmentId: filters.departmentId || undefined,
    employmentStatus: filters.employmentStatus || undefined,
  })
  void loadEmployees()
}

const handleReset = () => {
  Object.assign(filters, { keyword: '', departmentId: undefined, employmentStatus: undefined })
  handleSearch()
}

const handlePageSizeChange = () => {
  query.pageNum = 1
  void loadEmployees()
}

const openDetail = (employee: EmployeeVO) => {
  selectedEmployee.value = employee
  isDrawerVisible.value = true
}

const handleEdit = (employee: EmployeeVO) => {
  editingEmployee.value = employee
  Object.assign(form, {
    realName: employee.realName,
    phone: employee.phone || '',
    departmentId: employee.departmentId ?? null,
    positionId: employee.positionId ?? null,
    hireDate: employee.hireDate ?? null,
    employmentStatus: employee.employmentStatus,
  })
  isDialogVisible.value = true
}

const handleDepartmentChange = () => {
  form.positionId = null
}

const handleDialogClosed = () => {
  editingEmployee.value = undefined
  Object.assign(form, {
    realName: '',
    phone: '',
    departmentId: null,
    positionId: null,
    hireDate: null,
    employmentStatus: 'ACTIVE',
  })
  formRef.value?.clearValidate()
}

const handleSave = async () => {
  if (
    isSubmitting.value ||
    !editingEmployee.value ||
    !(await formRef.value?.validate().catch(() => false))
  )
    return
  isSubmitting.value = true
  const editingId = editingEmployee.value.id
  const payload: EmployeeUpdateDTO = {
    realName: form.realName.trim(),
    phone: form.phone.trim(),
    departmentId: form.departmentId || null,
    positionId: form.positionId || null,
    hireDate: form.hireDate || null,
    employmentStatus: form.employmentStatus,
  }
  try {
    await updateEmployee(editingId, payload)
    isDialogVisible.value = false
    ElMessage.success('员工档案已更新')
    await loadEmployees()
    if (selectedEmployee.value?.id === editingId) {
      selectedEmployee.value = {
        ...selectedEmployee.value,
        realName: payload.realName,
        phone: payload.phone,
        departmentId: payload.departmentId ?? undefined,
        positionId: payload.positionId ?? undefined,
        hireDate: payload.hireDate ?? undefined,
        employmentStatus: payload.employmentStatus,
      }
    }
  } catch {
    // 保存失败保留输入内容，便于修正后重试。
  } finally {
    isSubmitting.value = false
  }
}

const disableFutureDate = (date: Date) => date.getTime() > Date.now()

watch(
  () => route.fullPath,
  () => {
    query.pageNum = Math.max(1, Number(route.query.page) || 1)
    query.pageSize = [8, 16, 32].includes(Number(route.query.size)) ? Number(route.query.size) : 8
    query.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : undefined
    query.departmentId = Number(route.query.departmentId) || undefined
    query.employmentStatus =
      route.query.employmentStatus === 'ACTIVE' || route.query.employmentStatus === 'RESIGNED'
        ? route.query.employmentStatus
        : undefined
    Object.assign(filters, {
      keyword: query.keyword || '',
      departmentId: query.departmentId,
      employmentStatus: query.employmentStatus,
    })
    void loadEmployees()
  },
  { immediate: true },
)

onMounted(loadOptions)
</script>

<template>
  <main class="employee-page page-container">
    <header class="page-heading">
      <div>
        <h1>员工档案</h1>
        <p>按姓名、部门或在职状态查找员工，点击文件夹或列表行查看完整档案</p>
      </div>
      <nav
        class="section-navigation employee-view-toggle"
        aria-label="员工档案展示方式"
      >
        <button
          type="button"
          :aria-pressed="viewMode === 'folders'"
          @click="viewMode = 'folders'"
        >
          文件夹
        </button>
        <button
          type="button"
          :aria-pressed="viewMode === 'table'"
          @click="viewMode = 'table'"
        >
          列表
        </button>
      </nav>
    </header>

    <el-card
      class="content-panel"
      shadow="never"
    >
      <!-- 搜索栏 -->
      <el-form
        :model="filters"
        label-position="top"
        class="search-form"
        @submit.prevent="handleSearch"
      >
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            placeholder="姓名 / 用户名 / 联系方式"
            maxlength="50"
            clearable
          />
        </el-form-item>
        <el-form-item label="部门">
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
        <el-form-item label="在职状态">
          <el-select
            v-model="filters.employmentStatus"
            placeholder="全部状态"
            clearable
          >
            <el-option
              label="在职"
              value="ACTIVE"
            />
            <el-option
              label="离职"
              value="RESIGNED"
            />
          </el-select>
        </el-form-item>
        <div class="search-actions">
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
        v-if="!isOptionsReady"
        title="组织数据加载中或加载失败，暂不可编辑档案。"
        type="warning"
        :closable="false"
        class="notice-bar"
      >
        <el-button
          text
          @click="loadOptions"
        >
          重新加载组织数据
        </el-button>
      </el-alert>

      <el-alert
        v-if="isLoadFailed"
        title="档案加载失败，请重新查询或重试。"
        type="error"
        :closable="false"
        class="notice-bar"
      />

      <section
        v-if="viewMode === 'folders'"
        v-loading="isLoading"
        class="folder-results"
        aria-label="员工档案文件夹"
      >
        <div
          v-if="employees.length"
          class="employee-folder-grid"
        >
          <EmployeeFolderCard
            v-for="employee in employees"
            :key="employee.id"
            :employee="employee"
            :selected="selectedEmployee?.id === employee.id && isDrawerVisible"
            :disabled="isLoading"
            @open="openDetail"
          />
        </div>
        <el-empty
          v-else-if="!isLoading"
          :description="
            isLoadFailed ? '档案暂不可用，请重新查询' : '暂无符合条件的员工，试试调整筛选条件'
          "
          :image-size="64"
        />
        <div
          v-else
          class="employee-folder-grid"
          aria-label="正在加载员工档案"
        >
          <div
            v-for="index in 8"
            :key="index"
            class="folder-placeholder"
          ></div>
        </div>
      </section>
      <!-- 表格 -->
      <el-table
        v-if="viewMode === 'table'"
        v-loading="isLoading"
        :data="employees"
        row-key="id"
        class="employee-table"
        max-height="360"
        empty-text="暂无符合条件的员工，试试调整筛选条件"
        @row-click="openDetail"
      >
        <el-table-column
          label="姓名"
          min-width="150"
        >
          <template #default="{ row }">
            <div class="cell-person">
              <span class="cell-avatar">{{ row.realName.slice(0, 1) }}</span>
              <span class="cell-name">{{ row.realName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          prop="userName"
          label="用户名"
          min-width="120"
        />
        <el-table-column
          label="部门"
          min-width="130"
        >
          <template #default="{ row }">{{ row.departmentName || '未分配' }}</template>
        </el-table-column>
        <el-table-column
          label="岗位"
          min-width="130"
        >
          <template #default="{ row }">{{ row.positionName || '未分配' }}</template>
        </el-table-column>
        <el-table-column
          label="角色"
          min-width="120"
        >
          <template #default="{ row }">{{ roleLabels[row.roleCode] || row.roleCode }}</template>
        </el-table-column>
        <el-table-column
          label="入职日期"
          min-width="110"
        >
          <template #default="{ row }">{{ row.hireDate || '未填写' }}</template>
        </el-table-column>
        <el-table-column
          label="在职状态"
          width="110"
        >
          <template #default="{ row }">
            <span class="status-pill">
              <i
                class="status-dot"
                :class="row.employmentStatus === 'ACTIVE' ? 'is-active' : 'is-resigned'"
              />
              {{ row.employmentStatus === 'ACTIVE' ? '在职' : '离职' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="170"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              :disabled="!isOptionsReady || isLoading || isLoadFailed"
              link
              type="primary"
              @click.stop="handleEdit(row as EmployeeVO)"
            >
              编辑档案
            </el-button>
            <el-button
              :disabled="isLoading || isLoadFailed"
              link
              type="primary"
              @click.stop="router.push('/admin/assessment-history/' + row.id)"
            >
              测评报告
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[8, 16, 32]"
          :disabled="isLoading"
          layout="total, sizes, prev, pager, next"
          @current-change="loadEmployees"
          @size-change="handlePageSizeChange"
        />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="isDrawerVisible"
      :title="selectedEmployee?.realName || '员工详情'"
      size="min(420px, 92vw)"
      :with-header="false"
      class="employee-drawer"
    >
      <template v-if="selectedEmployee">
        <el-button
          class="drawer-close"
          text
          aria-label="关闭员工档案"
          @click="isDrawerVisible = false"
        >
          关闭档案 ×
        </el-button>
        <header class="drawer-header">
          <span class="drawer-avatar">{{ selectedEmployee.realName.slice(0, 1) }}</span>
          <div>
            <h2>{{ selectedEmployee.realName }}</h2>
            <p>{{ selectedEmployee.positionName || '未分配岗位' }}</p>
          </div>
          <span class="status-pill">
            <i
              class="status-dot"
              :class="selectedEmployee.employmentStatus === 'ACTIVE' ? 'is-active' : 'is-resigned'"
            />
            {{ selectedEmployee.employmentStatus === 'ACTIVE' ? '在职' : '离职' }}
          </span>
        </header>

        <dl class="detail-props">
          <div>
            <dt>员工编号</dt>
            <dd class="text-mono">#{{ String(selectedEmployee.id).padStart(4, '0') }}</dd>
          </div>
          <div>
            <dt>登录账号</dt>
            <dd>{{ selectedEmployee.userName }}</dd>
          </div>
          <div>
            <dt>所属部门</dt>
            <dd>{{ selectedEmployee.departmentName || '未分配' }}</dd>
          </div>
          <div>
            <dt>当前岗位</dt>
            <dd>{{ selectedEmployee.positionName || '未分配' }}</dd>
          </div>
          <div>
            <dt>入职日期</dt>
            <dd>{{ selectedEmployee.hireDate || '未填写' }}</dd>
          </div>
          <div>
            <dt>联系方式</dt>
            <dd>{{ selectedEmployee.phone || '未填写' }}</dd>
          </div>
          <div>
            <dt>账号角色</dt>
            <dd>{{ roleLabels[selectedEmployee.roleCode] || selectedEmployee.roleCode }}</dd>
          </div>
          <div>
            <dt>账号状态</dt>
            <dd>{{ selectedEmployee.userStatus === 'ENABLED' ? '已启用' : '已停用' }}</dd>
          </div>
        </dl>

        <div class="drawer-actions">
          <el-button
            type="primary"
            :disabled="!isOptionsReady"
            @click="handleEdit(selectedEmployee)"
          >
            编辑档案
          </el-button>
          <el-button @click="router.push('/admin/assessment-history/' + selectedEmployee.id)">
            查看测评报告
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="isDialogVisible"
      title="编辑员工档案"
      width="min(520px, 94vw)"
      :close-on-click-modal="false"
      :close-on-press-escape="!isSubmitting"
      :show-close="!isSubmitting"
      @closed="handleDialogClosed"
    >
      <p class="dialog-tip">用户名：{{ editingEmployee?.userName }}</p>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        :disabled="isSubmitting"
        label-position="top"
      >
        <el-form-item
          label="姓名"
          prop="realName"
        >
          <el-input
            v-model="form.realName"
            maxlength="50"
          />
        </el-form-item>
        <el-form-item
          label="联系方式"
          prop="phone"
        >
          <el-input
            v-model="form.phone"
            maxlength="20"
          />
        </el-form-item>
        <el-form-item
          label="部门"
          prop="departmentId"
        >
          <el-select
            v-model="form.departmentId"
            placeholder="未分配"
            clearable
            @change="handleDepartmentChange"
          >
            <el-option
              v-for="item in availableDepartments"
              :key="item.id"
              :label="item.departmentName + (item.isEnabled === 1 ? '' : '（已停用）')"
              :value="item.id"
              :disabled="item.isEnabled !== 1"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="岗位"
          prop="positionId"
        >
          <el-select
            v-model="form.positionId"
            placeholder="未分配"
            clearable
          >
            <el-option
              v-for="item in availablePositions"
              :key="item.id"
              :label="item.positionName + (item.isEnabled === 1 ? '' : '（已停用）')"
              :value="item.id"
              :disabled="item.isEnabled !== 1"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="入职日期"
          prop="hireDate"
        >
          <el-date-picker
            v-model="form.hireDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择入职日期"
            :disabled-date="disableFutureDate"
          />
        </el-form-item>
        <el-form-item
          label="在职状态"
          prop="employmentStatus"
        >
          <el-select v-model="form.employmentStatus">
            <el-option
              label="在职"
              value="ACTIVE"
            />
            <el-option
              label="离职"
              value="RESIGNED"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="dialog-hint">在职状态用于档案记录；本次修改不会自动变更账号登录状态。</p>
      <template #footer>
        <el-button
          :disabled="isSubmitting"
          @click="isDialogVisible = false"
        >
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="isSubmitting"
          @click="handleSave"
        >
          保存档案
        </el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped lang="scss">
.employee-folder-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.folder-results {
  margin-top: 12px;
}
.folder-placeholder {
  height: 152px;
  background: rgb(255 255 255 / 24%);
  border-radius: 16px;
}
.employee-folder-grid :deep(.employee-folder) {
  height: 152px;
}
.employee-folder-grid :deep(.folder-front) {
  inset: 36px 0 0;
  background: #f3f2f7;
  padding: 12px;
}
.employee-folder-grid :deep(.folder-back),
.employee-folder-grid :deep(.folder-back::before) {
  background: #e5e2ed;
}
.employee-folder-grid :deep(.folder-front strong) {
  font-size: 16px;
  line-height: 24px;
  font-weight: 500;
}
.employee-folder-grid :deep(.folder-position),
.employee-folder-grid :deep(.folder-bottom) {
  font-size: 12px;
  line-height: 16px;
}
.employee-folder-grid :deep(.folder-paper) {
  padding: 8px;
  color: #77717f;
}
.employee-folder-grid :deep(.employee-folder:hover:not(:disabled)) {
  transform: translateY(-2px);
}
.employee-view-toggle {
  margin-bottom: 0;
}
@media (max-width: 1100px) {
  .employee-folder-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .employee-folder-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }
}
.employee-page {
  padding-top: var(--space-6);
}
.page-heading {
  margin-bottom: var(--space-5);
}
.page-heading h1 {
  font-size: var(--font-size-3xl);
  line-height: var(--line-height-3xl);
  margin: 8px 0 8px;
}
.page-heading p:not(.eyebrow) {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-base);
}

.content-panel {
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}
.content-panel :deep(.el-card__body) {
  padding: var(--space-5);
}

// 搜索栏
.search-form {
  display: grid;
  grid-template-columns: minmax(200px, 1.4fr) minmax(160px, 1fr) minmax(160px, 1fr) auto;
  align-items: end;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}
.search-form :deep(.el-form-item) {
  margin-bottom: 0;
}
.search-actions {
  display: flex;
  gap: var(--space-2);
  margin-bottom: 18px;
}
.notice-bar {
  margin-bottom: var(--space-4);
}

// 表格
.employee-table :deep(.el-table__row) {
  cursor: pointer;
}
.cell-person {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.cell-avatar {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent-400), var(--accent-600));
  color: #fff;
  font-size: var(--font-size-sm);
  font-weight: 600;
  flex-shrink: 0;
}
.cell-name {
  font-weight: 500;
  color: var(--text-strong);
}

// 在职状态圆点（HR 签名细节，像监控工具的 live dot）
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-base);
  color: var(--text-regular);
  font-weight: 500;
}
.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text-disabled);
  flex-shrink: 0;
}
.status-dot.is-active {
  background: var(--sem-success);
}
.status-dot.is-resigned {
  background: var(--text-disabled);
}

.pagination-wrap {
  margin-top: var(--space-5);
  display: flex;
  justify-content: flex-end;
  overflow-x: auto;
  padding-bottom: var(--space-2);
}

// 详情抽屉
.employee-drawer :deep(.el-drawer__header) {
  display: none;
}
.employee-drawer :deep(.el-drawer__body) {
  padding: var(--space-5);
}
.drawer-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--border-light);
}
.drawer-avatar {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent-400), var(--accent-600));
  color: #fff;
  font-size: var(--font-size-lg);
  font-weight: 600;
  flex-shrink: 0;
}
.drawer-header h2 {
  font-size: var(--font-size-lg);
  line-height: var(--line-height-lg);
  margin-bottom: 2px;
}
.drawer-header p {
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary);
}
.drawer-header .status-pill {
  margin-left: auto;
}
.detail-props {
  display: grid;
  gap: var(--space-3);
  margin: var(--space-5) 0;
}
.detail-props > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-sm);
}
.detail-props dt {
  color: var(--text-secondary);
}
.detail-props dd {
  margin: 0;
  color: var(--text-strong);
  font-weight: 500;
  text-align: right;
  overflow-wrap: anywhere;
}
.drawer-actions {
  display: flex;
  gap: var(--space-2);
  padding-top: var(--space-5);
  border-top: 1px solid var(--border-light);
}

// 弹窗
.dialog-tip {
  margin: 0 0 var(--space-4);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}
.dialog-hint {
  margin: var(--space-3) 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 20px;
}
:deep(.el-date-editor.el-input) {
  width: 100%;
}

@media (max-width: 1000px) {
  .search-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 560px) {
  .search-form {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }
  .search-actions {
    margin-top: var(--space-2);
  }
}
</style>
