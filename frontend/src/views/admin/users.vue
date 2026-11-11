<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createAdminUser, getUsers, updateUserAccess } from '@/api/user'
import { useUserStore } from '@/stores/user'
import type { UserAccessReqDTO } from '@/types/user'
import { getDepartments, getPositions } from '@/api/organization'
import type { AdminUserVO } from '@/types/adminUser'
import type { DepartmentVO, PositionVO } from '@/types/organization'
import type { FormInstance } from 'element-plus'
import { accountRules } from '@/utils/validation'

const userList = ref<AdminUserVO[]>([])
const departments = ref<DepartmentVO[]>([])
const positions = ref<PositionVO[]>([])
const isLoading = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const isSubmitting = ref(false)
const userStore = useUserStore()
const accessVisible = ref(false)
const isAccessSaving = ref(false)
const accessTarget = ref<AdminUserVO>()
const accessForm = reactive<UserAccessReqDTO>({ roleCode: 'EMPLOYEE', userStatus: 'ENABLED' })
const form = reactive({
  userName: '',
  password: '',
  realName: '',
  phone: '',
  roleCode: 'EMPLOYEE' as 'ADMIN' | 'DEPARTMENT_MANAGER' | 'EMPLOYEE',
  departmentId: undefined as number | undefined,
  positionId: undefined as number | undefined,
})
const roleLabels = { ADMIN: '管理员', DEPARTMENT_MANAGER: '部门负责人', EMPLOYEE: '普通员工' }
const availablePositions = computed(() =>
  positions.value.filter(
    (item) => item.departmentId == null || item.departmentId === form.departmentId,
  ),
)
const loadData = async () => {
  isLoading.value = true
  try {
    const [userResponse, departmentResponse, positionResponse] = await Promise.all([
      getUsers(),
      getDepartments(),
      getPositions(),
    ])
    userList.value = userResponse.data.data
    departments.value = departmentResponse.data.data.filter((item) => item.isEnabled === 1)
    positions.value = positionResponse.data.data.filter((item) => item.isEnabled === 1)
  } catch {
    // 请求层显示错误，不以空数据掩盖失败。
  } finally {
    isLoading.value = false
  }
}
const handleRoleChange = () => {
  if (form.roleCode === 'ADMIN') {
    form.departmentId = undefined
    form.positionId = undefined
  }
}
const handleEditAccess = (row: AdminUserVO) => {
  accessTarget.value = row
  accessForm.roleCode = row.roleCode as UserAccessReqDTO['roleCode']
  accessForm.userStatus = row.userStatus as UserAccessReqDTO['userStatus']
  accessVisible.value = true
}
const handleSaveAccess = async () => {
  if (isAccessSaving.value || !accessTarget.value) return
  isAccessSaving.value = true
  try {
    try {
      await ElMessageBox.confirm(
        '确认保存账号角色及启停状态？如有变更，该账号现有登录将失效，需要重新登录。',
        '调整账号权限',
        { type: 'warning' },
      )
    } catch {
      return
    }
    await updateUserAccess(accessTarget.value.id, { ...accessForm })
    accessVisible.value = false
    ElMessage.success('账号权限已保存')
    await loadData()
  } catch {
    /* Unified request errors; preserve the dialog for correction. */
  } finally {
    isAccessSaving.value = false
  }
}
const handleDepartmentChange = () => {
  form.positionId = undefined
}
const handleReset = () => {
  Object.assign(form, {
    userName: '',
    password: '',
    realName: '',
    phone: '',
    roleCode: 'EMPLOYEE',
    departmentId: undefined,
    positionId: undefined,
  })
  formRef.value?.clearValidate()
}
const handleCreate = async () => {
  if (isSubmitting.value || !(await formRef.value?.validate().catch(() => false))) return
  isSubmitting.value = true
  try {
    await createAdminUser(form)
    dialogVisible.value = false
    Object.assign(form, {
      userName: '',
      password: '',
      realName: '',
      phone: '',
      roleCode: 'EMPLOYEE',
      departmentId: undefined,
      positionId: undefined,
    })
    await loadData()
    ElMessage.success('账号已创建，可直接登录')
  } catch {
    // 创建失败时保留弹窗，业务错误由请求层提示。
  } finally {
    isSubmitting.value = false
  }
}
onMounted(loadData)
</script>

<template>
  <main class="admin-page">
    <header class="page-header">
      <div>
        <h1>用户与角色</h1>
        <p>管理员创建的账号会直接通过审核并启用。</p>
      </div>
      <el-button
        type="primary"
        @click="dialogVisible = true"
      >
        创建用户
      </el-button>
    </header>
    <el-card class="content-panel">
      <el-table
        v-loading="isLoading"
        :data="userList"
        row-key="id"
        empty-text="暂无用户"
      >
        <el-table-column
          prop="userName"
          label="用户名"
          min-width="130"
        />
        <el-table-column
          prop="realName"
          label="姓名"
          min-width="120"
        />
        <el-table-column
          prop="phone"
          label="手机号"
          min-width="140"
        >
          <template #default="scope">{{ scope.row.phone || '未填写' }}</template>
        </el-table-column>
        <el-table-column
          label="角色"
          min-width="130"
        >
          <template #default="scope">
            {{ roleLabels[scope.row.roleCode as keyof typeof roleLabels] || scope.row.roleCode }}
          </template>
        </el-table-column>
        <el-table-column
          label="审核状态"
          width="120"
        >
          <template #default="scope">
            <el-tag :type="scope.row.auditStatus === 'APPROVED' ? 'success' : 'warning'">
              {{
                scope.row.auditStatus === 'APPROVED'
                  ? '已通过'
                  : scope.row.auditStatus === 'REJECTED'
                    ? '已拒绝'
                    : '待审核'
              }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="账号状态"
          width="110"
        >
          <template #default="scope">
            <el-tag :type="scope.row.userStatus === 'ENABLED' ? 'success' : 'info'">
              {{ scope.row.userStatus === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="auditReason"
          label="审核拒绝原因"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          label="账号操作"
          width="150"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-if="row.auditStatus === 'APPROVED'"
              :disabled="row.id === userStore.user?.id"
              size="small"
              @click="handleEditAccess(row as AdminUserVO)"
            >
              {{ row.id === userStore.user?.id ? '当前登录账号' : '调整账号权限' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-dialog
      v-model="dialogVisible"
      title="创建用户"
      width="min(560px, 94vw)"
      :close-on-click-modal="false"
      :close-on-press-escape="!isSubmitting"
      :show-close="!isSubmitting"
      @closed="handleReset"
    >
      <el-form
        ref="formRef"
        :rules="accountRules"
        :model="form"
        :disabled="isSubmitting"
        label-position="top"
      >
        <el-form-item
          label="用户名"
          prop="userName"
        >
          <el-input
            v-model="form.userName"
            minlength="4"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item
          label="初始密码"
          prop="password"
        >
          <el-input
            v-model="form.password"
            type="password"
            show-password
            minlength="6"
            maxlength="64"
          />
        </el-form-item>
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
          label="手机号"
          prop="phone"
        >
          <el-input
            v-model="form.phone"
            maxlength="20"
          />
        </el-form-item>
        <el-form-item
          label="角色"
          prop="roleCode"
        >
          <el-select
            v-model="form.roleCode"
            @change="handleRoleChange"
          >
            <el-option
              label="管理员"
              value="ADMIN"
            />
            <el-option
              label="部门负责人"
              value="DEPARTMENT_MANAGER"
            />
            <el-option
              label="普通员工"
              value="EMPLOYEE"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="form.roleCode !== 'ADMIN'"
          label="部门"
        >
          <el-select
            v-model="form.departmentId"
            clearable
            @change="handleDepartmentChange"
          >
            <el-option
              v-for="department in departments"
              :key="department.id"
              :label="department.departmentName"
              :value="department.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="form.roleCode !== 'ADMIN'"
          label="岗位"
        >
          <el-select
            v-model="form.positionId"
            clearable
          >
            <el-option
              v-for="position in availablePositions"
              :key="position.id"
              :label="position.positionName"
              :value="position.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button
          :disabled="isSubmitting"
          @click="dialogVisible = false"
        >
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="isSubmitting"
          @click="handleCreate"
        >
          创建并启用
        </el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="accessVisible"
      title="调整账号权限"
      width="min(500px, 94vw)"
      :close-on-click-modal="false"
      :close-on-press-escape="!isAccessSaving"
      :show-close="!isAccessSaving"
    >
      <p>目标账号：{{ accessTarget?.userName }}。负责人停用或变更角色前，请先移交所负责部门。</p>
      <el-form
        :model="accessForm"
        :disabled="isAccessSaving"
        label-position="top"
      >
        <el-form-item label="账号角色">
          <el-select v-model="accessForm.roleCode">
            <el-option
              v-for="(label, value) in roleLabels"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="账号状态">
          <el-select v-model="accessForm.userStatus">
            <el-option
              label="启用"
              value="ENABLED"
            />
            <el-option
              label="停用"
              value="DISABLED"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button
          :disabled="isAccessSaving"
          @click="accessVisible = false"
        >
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="isAccessSaving"
          @click="handleSaveAccess"
        >
          保存账号权限
        </el-button>
      </template>
    </el-dialog>
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
@media (max-width: 560px) {
  .page-header {
    flex-direction: column;
  }
}
</style>
