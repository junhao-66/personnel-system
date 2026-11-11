<script setup lang="ts">
import { reactive, ref } from 'vue'
import PublicAuthLayout from '@/components/PublicAuthLayout.vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { FormInstance, FormRules } from 'element-plus'
import { useRoute } from 'vue-router'
const router = useRouter()
const userStore = useUserStore()
const isLoading = ref(false)
const route = useRoute()
const formRef = ref<FormInstance>()
const rules: FormRules = {
  userName: [{ required: true, whitespace: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
const form = reactive({ userName: '', password: '' })
const handleLogin = async () => {
  if (isLoading.value || !(await formRef.value?.validate().catch(() => false))) return
  isLoading.value = true
  try {
    await userStore.login({ ...form, userName: form.userName.trim() })
    form.password = ''
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    const allowedRedirect =
      typeof redirect === 'string' &&
      ([
        '/workbench',
        '/mbti',
        '/account-center',
        '/assessment',
        '/assessment/history',
        '/knowledge-graph',
        '/job-preferences',
        '/leave',
      ].includes(redirect) ||
        (redirect.startsWith('/admin/') && userStore.user?.roleCode === 'ADMIN'))
    const target = allowedRedirect
      ? redirect
      : userStore.user?.roleCode === 'ADMIN'
        ? '/admin/dashboard'
        : '/workbench'
    await router.replace(target)
  } catch {
    // 请求层统一提示错误，登录失败时保留表单供用户修正。
  } finally {
    isLoading.value = false
  }
}
</script>
<template>
  <PublicAuthLayout>
    <h1>登录</h1>
    <p class="tip">登录企业账号，进入人事管理工作空间。</p>
    <el-form
      ref="formRef"
      :rules="rules"
      :model="form"
      label-position="top"
      @submit.prevent="handleLogin"
    >
      <el-form-item
        label="用户名"
        prop="userName"
      >
        <el-input
          v-model="form.userName"
          autocomplete="username"
          placeholder="请输入用户名"
        />
      </el-form-item>
      <el-form-item
        label="密码"
        prop="password"
      >
        <el-input
          v-model="form.password"
          type="password"
          show-password
          autocomplete="current-password"
          placeholder="请输入密码"
        />
      </el-form-item>
      <el-button
        :loading="isLoading"
        type="primary"
        native-type="submit"
        class="submit-button"
      >
        登录账号
      </el-button>
    </el-form>
    <div class="auth-meta">
      <span>仅审核通过的企业账号可登录</span>
      <RouterLink to="/review-status">查询审核状态</RouterLink>
    </div>
    <p class="auth-switch">
      第一次来到这里？
      <RouterLink to="/register">创建账号</RouterLink>
    </p>
  </PublicAuthLayout>
</template>
<style scoped lang="scss">
// 登录表单视觉由 PublicAuthLayout 统一控制，此处不重复定义。
</style>
