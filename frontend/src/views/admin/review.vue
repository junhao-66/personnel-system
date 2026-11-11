<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditUser, getPendingUsers } from '@/api/user'
import type { UserVO } from '@/types/user'

const userList = ref<UserVO[]>([])
const isLoading = ref(false)
const activeId = ref<number | null>(null)
const getList = async () => {
  isLoading.value = true
  try {
    const response = await getPendingUsers()
    userList.value = response.data.data
  } catch {
    // 请求层统一提示查询失败。
  } finally {
    isLoading.value = false
  }
}
const handleAudit = async (user: UserVO, auditStatus: 'APPROVED' | 'REJECTED') => {
  if (activeId.value !== null) return
  let auditReason: string | undefined
  if (auditStatus === 'REJECTED') {
    try {
      const answer = await ElMessageBox.prompt(
        `请说明拒绝 ${user.realName} 的原因，申请人可查询此内容。`,
        '拒绝申请',
        {
          inputType: 'textarea',
          inputPlaceholder: '请输入500字以内的拒绝原因',
          inputValidator: (value) =>
            (Boolean(value?.trim()) && value.trim().length <= 500) || '请填写500字以内的拒绝原因',
        },
      )
      auditReason = answer.value.trim()
    } catch {
      return
    }
  }
  activeId.value = user.id
  try {
    await auditUser(user.id, auditStatus, auditReason)
    ElMessage.success(auditStatus === 'APPROVED' ? '已通过审核' : '已拒绝申请')
    await getList()
  } catch {
    // 请求层统一显示审批失败。
  } finally {
    activeId.value = null
  }
}
onMounted(getList)
</script>

<template>
  <main class="admin-page">
    <header class="page-header">
      <div>
        <h1>员工注册审核</h1>
        <p>审核通过后，员工即可使用企业内部功能。</p>
      </div>
      <el-button
        :loading="isLoading"
        @click="getList"
      >
        刷新
      </el-button>
    </header>
    <el-card class="table-panel">
      <el-table
        v-loading="isLoading"
        :data="userList"
        row-key="id"
        empty-text="暂无待审核申请"
      >
        <el-table-column
          prop="userName"
          label="用户名"
          min-width="140"
        />
        <el-table-column
          prop="realName"
          label="姓名"
          min-width="120"
        />
        <el-table-column
          prop="phone"
          label="手机号"
          min-width="150"
        >
          <template #default="scope">{{ scope.row.phone || '未填写' }}</template>
        </el-table-column>
        <el-table-column
          label="审核状态"
          width="120"
        >
          <template #default><el-tag type="warning">待审核</el-tag></template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="190"
          fixed="right"
        >
          <template #default="scope">
            <el-button
              size="small"
              type="success"
              :loading="activeId === scope.row.id"
              @click="handleAudit(scope.row as UserVO, 'APPROVED')"
            >
              通过
            </el-button>
            <el-button
              size="small"
              type="danger"
              :loading="activeId === scope.row.id"
              @click="handleAudit(scope.row as UserVO, 'REJECTED')"
            >
              拒绝
            </el-button>
          </template>
        </el-table-column>
      </el-table>
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
.table-panel {
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
