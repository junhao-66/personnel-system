<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import DepartmentManagers from './components/DepartmentManagers.vue'
import {
  addDepartment,
  addPosition,
  updateDepartment,
  updatePosition,
  getDepartments,
  getPositions,
  setDepartmentEnabled,
  setPositionEnabled,
} from '@/api/organization'
import type { DepartmentVO, PositionVO } from '@/types/organization'
import type { FormInstance, FormRules } from 'element-plus'

const departmentFormRef = ref<FormInstance>()
const positionFormRef = ref<FormInstance>()
const departmentRules: FormRules = {
  departmentName: [
    { required: true, whitespace: true, message: '请输入部门名称', trigger: 'blur' },
  ],
  departmentCode: [
    { required: true, whitespace: true, message: '请输入部门编码', trigger: 'blur' },
  ],
}
const positionRules: FormRules = {
  positionName: [{ required: true, whitespace: true, message: '请输入岗位名称', trigger: 'blur' }],
  positionCode: [{ required: true, whitespace: true, message: '请输入岗位编码', trigger: 'blur' }],
}
const activeTab = ref('departments')
const departments = ref<DepartmentVO[]>([])
const positions = ref<PositionVO[]>([])
const isLoading = ref(false)
const isSubmitting = ref(false)
const editingDepartmentId = ref<number>()
const editingPositionId = ref<number>()
const resetDepartment = () => {
  editingDepartmentId.value = undefined
  Object.assign(departmentForm, { departmentName: '', departmentCode: '', sortOrder: 0 })
  departmentFormRef.value?.clearValidate()
}
const resetPosition = () => {
  editingPositionId.value = undefined
  Object.assign(positionForm, {
    positionName: '',
    positionCode: '',
    departmentId: undefined,
    description: '',
  })
  positionFormRef.value?.clearValidate()
}
const handleEditDepartment = (row: DepartmentVO) => {
  editingDepartmentId.value = row.id
  Object.assign(departmentForm, {
    departmentName: row.departmentName,
    departmentCode: row.departmentCode,
    sortOrder: row.sortOrder,
  })
  departmentFormRef.value?.clearValidate()
}
const handleEditPosition = (row: PositionVO) => {
  editingPositionId.value = row.id
  Object.assign(positionForm, {
    positionName: row.positionName,
    positionCode: row.positionCode,
    departmentId: row.departmentId ?? undefined,
    description: row.description ?? '',
  })
  positionFormRef.value?.clearValidate()
}
const positionDepartmentOptions = computed(() =>
  departments.value.filter((item) => item.isEnabled === 1 || item.id === positionForm.departmentId),
)
const departmentForm = reactive({ departmentName: '', departmentCode: '', sortOrder: 0 })
const positionForm = reactive({
  positionName: '',
  positionCode: '',
  departmentId: undefined as number | undefined,
  description: '',
})
const loadDepartments = async () => {
  const response = await getDepartments()
  departments.value = response.data.data
}
const loadPositions = async () => {
  const response = await getPositions()
  positions.value = response.data.data
}
const getData = async () => {
  isLoading.value = true
  try {
    await Promise.all([loadDepartments(), loadPositions()])
  } catch {
    /* 请求层统一提示错误。 */
  } finally {
    isLoading.value = false
  }
}
const handleAddDepartment = async () => {
  if (isSubmitting.value || !(await departmentFormRef.value?.validate().catch(() => false))) return
  isSubmitting.value = true
  try {
    if (editingDepartmentId.value) await updateDepartment(editingDepartmentId.value, departmentForm)
    else await addDepartment(departmentForm)
    resetDepartment()
    await loadDepartments()
    ElMessage.success('部门已保存')
  } catch {
    /* 请求层统一提示错误，保留填写内容供重试。 */
  } finally {
    isSubmitting.value = false
  }
}
const handleAddPosition = async () => {
  if (isSubmitting.value || !(await positionFormRef.value?.validate().catch(() => false))) return
  isSubmitting.value = true
  try {
    if (editingPositionId.value) await updatePosition(editingPositionId.value, positionForm)
    else await addPosition(positionForm)
    resetPosition()
    await loadPositions()
    ElMessage.success('岗位已保存')
  } catch {
    /* 请求层统一提示错误，保留填写内容供重试。 */
  } finally {
    isSubmitting.value = false
  }
}
const handleDepartmentToggle = async (row: DepartmentVO) => {
  if (isSubmitting.value) return
  isSubmitting.value = true
  try {
    await setDepartmentEnabled(row.id, row.isEnabled !== 1)
    await loadDepartments()
  } catch {
    /* 请求层统一提示错误。 */
  } finally {
    isSubmitting.value = false
  }
}
const handlePositionToggle = async (row: PositionVO) => {
  if (isSubmitting.value) return
  isSubmitting.value = true
  try {
    await setPositionEnabled(row.id, row.isEnabled !== 1)
    await loadPositions()
  } catch {
    /* 请求层统一提示错误。 */
  } finally {
    isSubmitting.value = false
  }
}
const getDepartmentName = (id?: number) =>
  departments.value.find((item) => item.id === id)?.departmentName ?? '未关联部门'
onMounted(getData)
</script>

<template>
  <main class="admin-page">
    <header class="page-header">
      <div>
        <h1>部门与岗位</h1>
        <p>维护企业组织结构，停用记录会保留以保护历史关联。</p>
      </div>
      <el-button
        :loading="isLoading"
        @click="getData"
      >
        刷新
      </el-button>
    </header>
    <el-card class="content-panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane
          label="请假审批负责人"
          name="managers"
          lazy
        >
          <DepartmentManagers />
        </el-tab-pane>
        <el-tab-pane
          label="部门管理"
          name="departments"
        >
          <el-form
            ref="departmentFormRef"
            :rules="departmentRules"
            :model="departmentForm"
            :disabled="isSubmitting"
            inline
            class="create-form"
            @submit.prevent="handleAddDepartment"
          >
            <el-form-item
              label="部门名称"
              prop="departmentName"
            >
              <el-input
                v-model="departmentForm.departmentName"
                maxlength="100"
              />
            </el-form-item>
            <el-form-item
              label="部门编码"
              prop="departmentCode"
            >
              <el-input
                v-model="departmentForm.departmentCode"
                maxlength="50"
              />
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number
                v-model="departmentForm.sortOrder"
                :min="0"
              />
            </el-form-item>
            <el-button
              type="primary"
              :loading="isSubmitting"
              @click="handleAddDepartment"
            >
              {{ editingDepartmentId ? '保存部门' : '新增部门' }}
            </el-button>
            <el-button
              v-if="editingDepartmentId"
              :disabled="isSubmitting"
              @click="resetDepartment"
            >
              取消编辑
            </el-button>
          </el-form>
          <el-table
            v-loading="isLoading"
            :data="departments"
            row-key="id"
            empty-text="暂无部门"
          >
            <el-table-column
              prop="departmentName"
              label="部门名称"
              min-width="160"
            />
            <el-table-column
              prop="departmentCode"
              label="部门编码"
              min-width="150"
            />
            <el-table-column
              prop="sortOrder"
              label="排序"
              width="90"
            />
            <el-table-column
              label="状态"
              width="100"
            >
              <template #default="scope">
                <el-tag :type="scope.row.isEnabled === 1 ? 'success' : 'info'">
                  {{ scope.row.isEnabled === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              label="操作"
              width="160"
            >
              <template #default="scope">
                <el-button
                  :disabled="isSubmitting"
                  link
                  type="primary"
                  @click="handleEditDepartment(scope.row as DepartmentVO)"
                >
                  编辑
                </el-button>
                <el-button
                  :disabled="isSubmitting"
                  link
                  type="primary"
                  @click="handleDepartmentToggle(scope.row as DepartmentVO)"
                >
                  {{ scope.row.isEnabled === 1 ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane
          label="岗位管理"
          name="positions"
        >
          <el-form
            ref="positionFormRef"
            :rules="positionRules"
            :model="positionForm"
            :disabled="isSubmitting"
            inline
            class="create-form"
            @submit.prevent="handleAddPosition"
          >
            <el-form-item
              label="岗位名称"
              prop="positionName"
            >
              <el-input
                v-model="positionForm.positionName"
                maxlength="100"
              />
            </el-form-item>
            <el-form-item
              label="岗位编码"
              prop="positionCode"
            >
              <el-input
                v-model="positionForm.positionCode"
                maxlength="50"
              />
            </el-form-item>
            <el-form-item label="所属部门">
              <el-select
                v-model="positionForm.departmentId"
                :disabled="!!editingPositionId"
                clearable
                placeholder="可选"
              >
                <el-option
                  v-for="department in positionDepartmentOptions"
                  :key="department.id"
                  :label="department.departmentName"
                  :value="department.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="岗位说明">
              <el-input
                v-model="positionForm.description"
                type="textarea"
                maxlength="255"
                show-word-limit
              />
            </el-form-item>
            <el-button
              type="primary"
              :loading="isSubmitting"
              @click="handleAddPosition"
            >
              {{ editingPositionId ? '保存岗位' : '新增岗位' }}
            </el-button>
            <el-button
              v-if="editingPositionId"
              :disabled="isSubmitting"
              @click="resetPosition"
            >
              取消编辑
            </el-button>
            <p v-if="editingPositionId">岗位所属部门保持不变；调岗请新增岗位后在员工档案中调整。</p>
          </el-form>
          <el-table
            v-loading="isLoading"
            :data="positions"
            row-key="id"
            empty-text="暂无岗位"
          >
            <el-table-column
              prop="positionName"
              label="岗位名称"
              min-width="160"
            />
            <el-table-column
              prop="positionCode"
              label="岗位编码"
              min-width="150"
            />
            <el-table-column
              label="所属部门"
              min-width="140"
            >
              <template #default="scope">{{ getDepartmentName(scope.row.departmentId) }}</template>
            </el-table-column>
            <el-table-column
              label="状态"
              width="100"
            >
              <template #default="scope">
                <el-tag :type="scope.row.isEnabled === 1 ? 'success' : 'info'">
                  {{ scope.row.isEnabled === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              prop="description"
              label="岗位说明"
              min-width="180"
              show-overflow-tooltip
            />
            <el-table-column
              label="操作"
              width="160"
            >
              <template #default="scope">
                <el-button
                  :disabled="isSubmitting"
                  link
                  type="primary"
                  @click="handleEditPosition(scope.row as PositionVO)"
                >
                  编辑
                </el-button>
                <el-button
                  :disabled="isSubmitting"
                  link
                  type="primary"
                  @click="handlePositionToggle(scope.row as PositionVO)"
                >
                  {{ scope.row.isEnabled === 1 ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </main>
</template>

<style scoped lang="scss">
.admin-page {
  min-height: 100vh;
  padding: clamp(20px, 5vw, 56px);
}
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  max-width: 1160px;
  margin: 0 auto 24px;
  color: var(--text-strong);
}
.eyebrow {
  margin: 0 0 8px;
  color: var(--brand-600);
  font-size: 12px;
  font-weight: 700;
}
h1 {
  margin: 0 0 8px;
  color: var(--text-strong);
  font-size: 30px;
}
.content-panel {
  max-width: 1160px;
  margin: auto;
  border-radius: 14px;
}
.create-form {
  margin-bottom: 12px;
}
@media (max-width: 760px) {
  .create-form {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }
  .page-header {
    flex-direction: column;
  }
}
</style>
