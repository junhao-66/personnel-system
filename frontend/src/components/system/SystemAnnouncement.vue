<script setup lang="ts">
// Authenticated, plain-text announcement shared by employee workspaces.
import { onMounted, ref } from 'vue'
import { getSystemSettings } from '@/api/systemSettings'
import type { SystemSettingsVO } from '@/types/systemSettings'

const settings = ref<SystemSettingsVO>()
const isLoading = ref(false)
const isLoadFailed = ref(false)
const getAnnouncement = async () => {
  if (isLoading.value) return
  isLoading.value = true
  isLoadFailed.value = false
  try {
    settings.value = (await getSystemSettings()).data.data
  } catch {
    settings.value = undefined
    isLoadFailed.value = true
  } finally {
    isLoading.value = false
  }
}
onMounted(getAnnouncement)
</script>

<template>
  <aside
    v-if="isLoadFailed || settings?.announcementEnabled"
    class="announcement-panel"
    aria-label="系统公告"
  >
    <template v-if="isLoadFailed">
      <p>公告加载失败。</p>
      <el-button
        :loading="isLoading"
        @click="getAnnouncement"
      >
        重新加载公告
      </el-button>
    </template>
    <template v-else-if="settings?.announcementEnabled">
      <h2>{{ settings.announcementTitle }}</h2>
      <p class="announcement-content">{{ settings.announcementContent }}</p>
      <el-button
        :loading="isLoading"
        text
        @click="getAnnouncement"
      >
        刷新公告
      </el-button>
    </template>
  </aside>
</template>

<style scoped lang="scss">
.announcement-panel {
  border: 1px solid var(--accent-200);
  border-radius: 16px;
  padding: 20px;
  margin-top: 20px;
  background: var(--accent-50);
  color: var(--text-primary);
  min-width: 0;
}
h2 {
  margin: 0 0 12px;
  color: var(--brand-600);
  font-size: 20px;
  overflow-wrap: anywhere;
}
.announcement-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.8;
}
</style>
