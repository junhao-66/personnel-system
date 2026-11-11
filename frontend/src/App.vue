<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { RouterView, useRouter } from 'vue-router'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import BaseGradientBackground from '@/components/BaseGradientBackground.vue'
import { useUserStore } from '@/stores/user'
import { SESSION_EXPIRED_EVENT } from '@/utils/session'

const router = useRouter()
const userStore = useUserStore()
const handleSessionExpired = () => {
  userStore.logout()
  void router.replace('/login')
}
onMounted(() => window.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired))
onUnmounted(() => window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired))
</script>

<template>
  <div class="app-shell">
    <el-config-provider :locale="zhCn">
      <BaseGradientBackground />
      <RouterView />
    </el-config-provider>
  </div>
</template>

<style scoped lang="scss">
// Keep the decorative layer above the body and below all page content.
.app-shell {
  min-height: 100vh;
  isolation: isolate;
}
</style>
