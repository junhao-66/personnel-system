<script setup lang="ts">
import { reactive, ref } from 'vue'
import PublicAuthLayout from '@/components/PublicAuthLayout.vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { registerUser } from '@/api/user'
import type { FormInstance } from 'element-plus'
import { accountRules } from '@/utils/validation'
const formRef = ref<FormInstance>()
const router = useRouter()
const isLoading = ref(false)
const form = reactive({ userName: '', password: '', realName: '', phone: '' })
const handleRegister = async () => {
  if (isLoading.value || !(await formRef.value?.validate().catch(() => false))) return
  isLoading.value = true
  try {
    await registerUser({ ...form, userName: form.userName.trim(), realName: form.realName.trim() })
    form.password = ''
    ElMessage.success('注册成功，请等待管理员审核')
    await router.push('/review-status')
  } catch {
    // 请求层统一显示错误，失败时不跳转。
  } finally {
    isLoading.value = false
  }
}
</script>
<template>
  <PublicAuthLayout register>
    <h1>从这里，认识自己</h1>
    <p class="tip">创建企业账号，开启你的个人成长空间。</p>
    <el-form
      ref="formRef"
      :rules="accountRules"
      :model="form"
      label-position="top"
      @submit.prevent="handleRegister"
    >
      <el-form-item
        label="用户名"
        prop="userName"
      >
        <el-input
          v-model="form.userName"
          autocomplete="username"
          placeholder="设置你的用户名"
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
          autocomplete="new-password"
          placeholder="设置登录密码"
        />
      </el-form-item>
      <el-form-item
        label="姓名"
        prop="realName"
      >
        <el-input
          v-model="form.realName"
          autocomplete="name"
          placeholder="请输入真实姓名"
        />
      </el-form-item>
      <el-form-item
        label="手机号"
        prop="phone"
      >
        <el-input
          v-model="form.phone"
          autocomplete="tel"
          inputmode="tel"
          placeholder="请输入手机号"
        />
      </el-form-item>
      <el-button
        :loading="isLoading"
        type="primary"
        native-type="submit"
        class="submit-button"
      >
        创建账号
      </el-button>
    </el-form>
    <div class="auth-meta">
      <span>提交后需由管理员审核</span>
      <RouterLink to="/review-status">查询审核状态</RouterLink>
    </div>
    <p class="auth-switch">
      已经有账号了？
      <RouterLink to="/login">直接登录</RouterLink>
    </p>
  </PublicAuthLayout>
</template>
<style scoped lang="scss">
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 20px;
}
.auth-card {
  width: min(420px, 100%);
  padding: 18px;
  border-radius: 18px;
  background: rgb(255 255 255 / 52%);
  backdrop-filter: blur(28px) saturate(150%);
  -webkit-backdrop-filter: blur(28px) saturate(150%);
}
h1 {
  margin: 0 0 8px;
  color: var(--text-strong);
}
.tip {
  color: var(--text-strong);
}
.submit-button {
  width: 100%;
  margin-bottom: 12px;
}
</style>
