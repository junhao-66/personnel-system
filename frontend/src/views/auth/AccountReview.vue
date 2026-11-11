<script setup lang="ts">
// Credentials authorize only this account's review status; no business token is issued.
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { getAccountReview } from '@/api/user'
import type { AccountReviewVO } from '@/types/user'

const form = reactive({ userName: '', password: '' })
const formRef = ref<FormInstance>()
const isLoading = ref(false)
const isLoadFailed = ref(false)
const result = ref<AccountReviewVO>()
const rules: FormRules = {
  userName: [{ required: true, whitespace: true, message: '请输入注册用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入注册密码', trigger: 'blur' }],
}
const statusText = computed(() =>
  result.value?.auditStatus === 'PENDING'
    ? '待审核'
    : result.value?.auditStatus === 'REJECTED'
      ? '审核未通过'
      : '审核已通过',
)
const handleQuery = async () => {
  if (isLoading.value || !(await formRef.value?.validate().catch(() => false))) return
  isLoading.value = true
  isLoadFailed.value = false
  result.value = undefined
  try {
    result.value = (
      await getAccountReview({ userName: form.userName.trim(), password: form.password })
    ).data.data
  } catch {
    isLoadFailed.value = true
  } finally {
    form.password = ''
    isLoading.value = false
  }
}
// Changing accounts must not leave another account's rejection reason visible.
watch(
  () => form.userName,
  () => {
    result.value = undefined
    isLoadFailed.value = false
  },
)
</script>
<template>
  <main class="review-page">
    <el-card class="review-card">
      <h1>查询注册审核状态</h1>
      <p>使用注册时的用户名和密码查询本人审核结果。此操作不会登录内部业务。</p>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        :disabled="isLoading"
        label-position="top"
        @submit.prevent="handleQuery"
      >
        <el-form-item
          label="注册用户名"
          prop="userName"
        >
          <el-input
            v-model="form.userName"
            autocomplete="username"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item
          label="注册密码"
          prop="password"
        >
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            maxlength="64"
          />
        </el-form-item>
        <el-button
          type="primary"
          native-type="submit"
          :loading="isLoading"
        >
          查询审核结果
        </el-button>
      </el-form>
      <el-alert
        v-if="isLoadFailed"
        title="查询失败，请核对账号密码后重试。"
        type="error"
        :closable="false"
      />
      <section
        v-if="result"
        class="review-result"
        aria-live="polite"
      >
        <h2>{{ statusText }}</h2>
        <p>账号：{{ result.userName }}</p>
        <p v-if="result.auditStatus === 'PENDING'">管理员尚未完成审核，请稍后查询或联系管理员。</p>
        <p v-else-if="result.auditStatus === 'REJECTED'">
          拒绝原因：{{ result.auditReason || '历史审核未记录原因，请联系管理员。' }}
        </p>
        <p v-else-if="result.userStatus === 'DISABLED'">
          审核已通过，但账号当前已停用，请联系管理员。
        </p>
        <p v-else>账号已审核通过，可以返回登录页登录。</p>
        <p v-if="result.reviewedAt">
          审核时间：{{ result.reviewedAt.replace('T', ' ').slice(0, 19) }}
        </p>
      </section>
      <div class="links">
        <RouterLink to="/login">返回登录</RouterLink>
        <RouterLink to="/register">注册账号</RouterLink>
        <RouterLink to="/">返回首页</RouterLink>
      </div>
    </el-card>
  </main>
</template>
<style scoped lang="scss">
.review-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 20px;
  color: var(--text-strong);
}
.review-card {
  width: min(520px, 100%);
  border-radius: 18px;
  background: rgb(255 255 255 / 65%);
}
h1,
h2 {
  color: var(--brand-600);
}
p {
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.review-result {
  margin-top: 20px;
  border-top: 1px solid var(--border-default);
}
.links {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 24px;
}
:deep(.el-alert) {
  margin-top: 18px;
}
</style>
