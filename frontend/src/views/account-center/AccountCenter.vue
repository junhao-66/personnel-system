<script setup lang="ts">
// Self-service account changes; identity and organization assignments remain administrator-maintained.
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { updateOwnContact, changeOwnPassword } from '@/api/account'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const contactForm = reactive({ phone: '' })
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const contactRef = ref<FormInstance>()
const passwordRef = ref<FormInstance>()
const isLoading = ref(false)
const isLoadFailed = ref(false)
const isSaving = ref(false)
const isChanging = ref(false)
const isBusy = computed(() => isLoading.value || isSaving.value || isChanging.value)
const roleLabel = computed(
  () =>
    ({ ADMIN: '管理员', DEPARTMENT_MANAGER: '部门负责人', EMPLOYEE: '普通员工' })[
      userStore.user?.roleCode || ''
    ] || '—',
)
const contactRules: FormRules = {
  phone: [
    {
      validator: (_rule, value: string, done) =>
        done(
          !value || /^[+0-9][0-9() +\-]{2,19}$/.test(value)
            ? undefined
            : new Error('请输入3至20位电话号码，可留空'),
        ),
      trigger: 'blur',
    },
  ],
}
const passwordRules: FormRules = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    {
      validator: (_rule, value: string, done) => {
        const valid =
          !!value.trim() &&
          value.length >= 8 &&
          value.length <= 64 &&
          new TextEncoder().encode(value).length <= 72
        done(
          !valid
            ? new Error('新密码须为8至64个字符，UTF-8长度不超过72字节')
            : value === passwordForm.currentPassword
              ? new Error('新密码不能与当前密码相同')
              : undefined,
        )
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    {
      validator: (_rule, value: string, done) =>
        done(
          value && value === passwordForm.newPassword ? undefined : new Error('两次新密码不一致'),
        ),
      trigger: 'blur',
    },
  ],
}

const getAccount = async () => {
  if (isBusy.value) return
  isLoading.value = true
  isLoadFailed.value = false
  try {
    await userStore.refreshUser()
    contactForm.phone = userStore.user?.phone || ''
  } catch {
    isLoadFailed.value = true
  } finally {
    isLoading.value = false
  }
}
const handleSaveContact = async () => {
  if (
    isBusy.value ||
    isLoadFailed.value ||
    !(await contactRef.value?.validate().catch(() => false))
  )
    return
  isSaving.value = true
  try {
    const response = await updateOwnContact({ phone: contactForm.phone.trim() })
    contactForm.phone = response.data.data.phone || ''
    await userStore.refreshUser()
    ElMessage.success('联系方式已保存')
  } catch {
    /* Shared request layer shows the error; retain the editable value. */
  } finally {
    isSaving.value = false
  }
}
const handleChangePassword = async () => {
  if (
    isBusy.value ||
    isLoadFailed.value ||
    !(await passwordRef.value?.validate().catch(() => false))
  )
    return
  isChanging.value = true
  try {
    await changeOwnPassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
    })
    Object.assign(passwordForm, { currentPassword: '', newPassword: '', confirmPassword: '' })
    userStore.logout()
    ElMessage.success('密码已修改，请使用新密码重新登录；其他设备的旧登录也已失效')
    await router.replace({ path: '/login', query: { redirect: '/account-center' } })
  } catch {
    /* Failed validation must leave the existing login and form available. */
  } finally {
    isChanging.value = false
  }
}
onMounted(getAccount)
</script>

<template>
  <main class="account-page">
    <header class="page-heading">
      <div>
        <RouterLink to="/workbench">返回个人工作台</RouterLink>
        <h1>账号中心</h1>
        <p>查看本人账号信息、维护联系方式和修改登录密码。</p>
      </div>
      <el-button
        :loading="isLoading"
        :disabled="isSaving || isChanging"
        @click="getAccount"
      >
        重新加载账号
      </el-button>
    </header>
    <el-alert
      v-if="isLoadFailed"
      title="账号加载失败，请重新加载后再操作。"
      type="error"
      :closable="false"
    />
    <section
      v-loading="isLoading"
      class="account-grid"
    >
      <el-card class="panel">
        <h2>个人信息</h2>
        <dl>
          <dt>用户名</dt>
          <dd>{{ userStore.user?.userName || '—' }}</dd>
          <dt>姓名</dt>
          <dd>{{ userStore.user?.realName || '—' }}</dd>
          <dt>角色</dt>
          <dd>{{ roleLabel }}</dd>
        </dl>
        <p class="hint">姓名、部门、岗位和在职状态由管理员在员工档案中维护。</p>
        <el-form
          ref="contactRef"
          :model="contactForm"
          :rules="contactRules"
          label-position="top"
          @submit.prevent="handleSaveContact"
        >
          <el-form-item
            label="联系电话"
            prop="phone"
          >
            <el-input
              v-model.trim="contactForm.phone"
              :disabled="isBusy || isLoadFailed"
              maxlength="20"
              autocomplete="tel"
              placeholder="可留空"
            />
          </el-form-item>
          <el-button
            :loading="isSaving"
            :disabled="isBusy || isLoadFailed"
            type="primary"
            native-type="submit"
          >
            保存联系方式
          </el-button>
        </el-form>
      </el-card>
      <el-card class="panel">
        <h2>修改密码</h2>
        <p class="hint">修改成功后所有设备的旧登录将失效，需要重新登录。</p>
        <el-form
          ref="passwordRef"
          :model="passwordForm"
          :rules="passwordRules"
          label-position="top"
          @submit.prevent="handleChangePassword"
        >
          <el-form-item
            label="当前密码"
            prop="currentPassword"
          >
            <el-input
              v-model="passwordForm.currentPassword"
              :disabled="isBusy || isLoadFailed"
              type="password"
              show-password
              autocomplete="current-password"
              maxlength="64"
            />
          </el-form-item>
          <el-form-item
            label="新密码"
            prop="newPassword"
          >
            <el-input
              v-model="passwordForm.newPassword"
              :disabled="isBusy || isLoadFailed"
              type="password"
              show-password
              autocomplete="new-password"
              maxlength="64"
            />
          </el-form-item>
          <el-form-item
            label="确认新密码"
            prop="confirmPassword"
          >
            <el-input
              v-model="passwordForm.confirmPassword"
              :disabled="isBusy || isLoadFailed"
              type="password"
              show-password
              autocomplete="new-password"
              maxlength="64"
            />
          </el-form-item>
          <el-button
            :loading="isChanging"
            :disabled="isBusy || isLoadFailed"
            type="primary"
            native-type="submit"
          >
            修改密码并重新登录
          </el-button>
        </el-form>
      </el-card>
    </section>
  </main>
</template>

<style scoped lang="scss">
.account-page {
  max-width: 1150px;
  margin: auto;
  padding: clamp(16px, 4vw, 40px);
  color: var(--text-primary);
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
p {
  line-height: 1.7;
}
.account-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin-top: 20px;
}
.panel {
  border-radius: 16px;
  min-width: 0;
}
dl {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr);
  gap: 16px;
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
dt,
.hint {
  color: var(--text-secondary);
}
@media (max-width: 680px) {
  .account-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
