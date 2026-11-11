<script setup lang="ts">
// 管理员维护部门请假审批负责人，不授予负责人额外的人才分析权限。
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { configureLeaveManager, configureSelfApprover } from '@/api/leave'
import { getDepartments } from '@/api/organization'
import { getUsers } from '@/api/user'
import type { DepartmentVO } from '@/types/organization'
import type { AdminUserVO } from '@/types/adminUser'

const departments = ref<DepartmentVO[]>([])
const users = ref<AdminUserVO[]>([])
const isLoading = ref(false)
const isSubmitting = ref(false)
const assignments = reactive<Record<number, number | undefined>>({})
const selfApprovers = reactive<Record<number, number | undefined>>({})
const managers = computed(() =>
  users.value.filter(
    (u) =>
      u.roleCode === 'DEPARTMENT_MANAGER' &&
      u.auditStatus === 'APPROVED' &&
      u.userStatus === 'ENABLED',
  ),
)
const getData = async () => {
  isLoading.value = true
  try {
    const [departmentResult, userResult] = await Promise.all([getDepartments(), getUsers()])
    departments.value = departmentResult.data.data
    users.value = userResult.data.data
    for (const department of departments.value) {
      assignments[department.id] = department.managerUserId
      selfApprovers[department.id] = department.selfApproverUserId
    }
  } catch {
    /* 请求层统一提示。 */
  } finally {
    isLoading.value = false
  }
}
const handleSave = async (row: DepartmentVO) => {
  const managerId = assignments[row.id]
  if (isSubmitting.value || !managerId) return
  isSubmitting.value = true
  try {
    try {
      await ElMessageBox.confirm(
        '保存后，该部门员工的待审批申请将由新负责人处理。负责人本人不能审批自己的申请。',
        '设置审批负责人',
        { confirmButtonText: '确认设置', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    await configureLeaveManager(row.id, managerId)
    ElMessage.success('审批负责人已设置')
    await getData()
  } catch {
    /* 请求层统一提示。 */
  } finally {
    isSubmitting.value = false
  }
}
onMounted(getData)
const handleSaveSelfApprover = async (row: DepartmentVO) => {
  if (isSubmitting.value) return
  if (selfApprovers[row.id] && selfApprovers[row.id] === row.managerUserId) {
    ElMessage.warning('本人请假审批人不能与部门负责人相同')
    return
  }
  isSubmitting.value = true
  try {
    try {
      await ElMessageBox.confirm(
        '此配置仅承接部门负责人的本人请假，普通员工仍由部门负责人审批；清空后负责人将无法提交本人请假。',
        '配置本人请假审批人',
        { confirmButtonText: '保存配置', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    await configureSelfApprover(row.id, selfApprovers[row.id])
    ElMessage.success('本人请假审批人已保存')
    await getData()
  } catch {
    /* 请求层统一提示，保留选择供重试。 */
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <section>
    <p>
      仅可选择已审核且启用的部门负责人。普通员工由对应部门负责人审批；负责人本人请假由单独配置的另一名部门负责人审批。配置变更和调部门后，待审批申请按当前归属流转，任何人不能自批。
    </p>
    <el-button
      :loading="isLoading"
      :disabled="isSubmitting"
      @click="getData"
    >
      刷新负责人配置
    </el-button>
    <el-table
      v-loading="isLoading"
      :data="departments"
      row-key="id"
      empty-text="暂无部门"
    >
      <el-table-column
        prop="departmentName"
        label="部门"
        min-width="140"
      />
      <el-table-column
        label="请假审批负责人"
        min-width="220"
      >
        <template #default="scope">
          <el-select
            v-model="assignments[scope.row.id]"
            :disabled="isSubmitting"
            placeholder="请选择审批负责人"
            filterable
          >
            <el-option
              v-for="manager in managers"
              :key="manager.id"
              :label="manager.realName + '（' + manager.userName + '）'"
              :value="manager.id"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="100"
      >
        <template #default="scope">
          <el-button
            :disabled="isSubmitting || !assignments[scope.row.id]"
            type="primary"
            link
            @click="handleSave(scope.row as DepartmentVO)"
          >
            保存负责人
          </el-button>
        </template>
      </el-table-column>
      <el-table-column
        label="负责人本人请假审批人"
        min-width="240"
      >
        <template #default="scope">
          <el-select
            v-model="selfApprovers[scope.row.id]"
            :disabled="isSubmitting"
            clearable
            filterable
            placeholder="选择另一名部门负责人"
          >
            <el-option
              v-for="manager in managers"
              :key="manager.id"
              :disabled="manager.id === scope.row.managerUserId"
              :label="manager.realName + '（' + manager.userName + '）'"
              :value="manager.id"
            />
          </el-select>
          <el-button
            :disabled="isSubmitting"
            type="primary"
            link
            @click="handleSaveSelfApprover(scope.row as DepartmentVO)"
          >
            保存本人审批配置
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<style scoped lang="scss">
p {
  color: var(--text-secondary);
  line-height: 1.7;
}
.el-table {
  margin-top: 16px;
}
</style>
