<script setup lang="ts">
// Administrator-owned announcement and new-assessment availability; revision protects concurrent edits.
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminSystemSettings, updateSystemSettings } from '@/api/systemSettings'
import type { SystemSettingsUpdateReqDTO, SystemSettingsVO } from '@/types/systemSettings'

const form = reactive<SystemSettingsUpdateReqDTO>({
  assessmentEnabled: true,
  assessmentNotice: '',
  announcementEnabled: false,
  announcementTitle: '',
  announcementContent: '',
  revision: 0,
})
const activeSection = ref('announcement')
const saved = ref('')
const updatedAt = ref('')
const isLoading = ref(false)
const isSaving = ref(false)
const isLoadFailed = ref(false)
const isDirty = computed(() => !!saved.value && JSON.stringify(form) !== saved.value)
const applySettings = (value: SystemSettingsVO) => {
  Object.assign(form, {
    assessmentEnabled: value.assessmentEnabled,
    assessmentNotice: value.assessmentNotice,
    announcementEnabled: value.announcementEnabled,
    announcementTitle: value.announcementTitle,
    announcementContent: value.announcementContent,
    revision: value.revision,
  })
  saved.value = JSON.stringify(form)
  updatedAt.value = value.updatedAt?.replace('T', ' ').slice(0, 19) || '—'
}
const confirmDiscard = async () => {
  if (!isDirty.value) return true
  try {
    await ElMessageBox.confirm('有尚未保存的配置，是否放弃本页修改？', '确认离开', {
      confirmButtonText: '放弃修改',
      cancelButtonText: '继续编辑',
    })
    return true
  } catch {
    return false
  }
}
const getSettings = async () => {
  if (isLoading.value || isSaving.value || !(await confirmDiscard())) return
  isLoading.value = true
  isLoadFailed.value = false
  try {
    applySettings((await getAdminSystemSettings()).data.data)
  } catch {
    isLoadFailed.value = true
  } finally {
    isLoading.value = false
  }
}
const handleSave = async () => {
  if (isLoading.value || isSaving.value || isLoadFailed.value || !saved.value) return
  if (!form.assessmentEnabled && !form.assessmentNotice.trim()) {
    ElMessage.warning('暂停新测评时请填写说明')
    return
  }
  if (
    form.announcementEnabled &&
    (!form.announcementTitle.trim() || !form.announcementContent.trim())
  ) {
    ElMessage.warning('发布公告时请填写标题和内容')
    return
  }
  try {
    await ElMessageBox.confirm(
      '保存后公告与新测评开放状态立即生效。已有草稿仍可续答和提交，历史报告保持可用。是否保存？',
      '确认保存配置',
      { confirmButtonText: '保存配置', cancelButtonText: '继续编辑' },
    )
  } catch {
    return
  }
  if (isSaving.value) return
  isSaving.value = true
  try {
    applySettings((await updateSystemSettings({ ...form })).data.data)
    ElMessage.success('系统配置已保存')
  } catch {
    /* Retain edits and revision so a concurrent-update error cannot overwrite another administrator. */
  } finally {
    isSaving.value = false
  }
}
onBeforeRouteLeave(
  () => !localStorage.getItem('accessToken') || (!isSaving.value && confirmDiscard()),
)
onMounted(getSettings)
</script>

<template>
  <main class="settings-page">
    <header class="page-heading">
      <div>
        <h1>系统公告与测评开关</h1>
        <p>维护工作台公告，并控制是否允许创建新测评。</p>
      </div>
      <el-button
        :loading="isLoading"
        :disabled="isSaving"
        @click="getSettings"
      >
        重新加载配置
      </el-button>
    </header>
    <nav
      class="section-navigation"
      aria-label="系统配置分类"
    >
      <button
        type="button"
        :aria-pressed="activeSection === 'announcement'"
        @click="activeSection = 'announcement'"
      >
        工作台公告
      </button>
      <button
        type="button"
        :aria-pressed="activeSection === 'assessment'"
        @click="activeSection = 'assessment'"
      >
        测评开放设置
      </button>
    </nav>
    <el-alert
      v-if="activeSection === 'assessment'"
      title="暂停只影响新建测评。已有草稿仍可保存、续答和提交，历史报告、打卡、请假不受影响。"
      type="info"
      :closable="false"
    />
    <el-alert
      v-if="isLoadFailed"
      title="系统配置加载失败，暂不可保存，请重新加载。"
      type="error"
      :closable="false"
    />
    <el-form
      v-loading="isLoading"
      :model="form"
      label-position="top"
      :disabled="isLoading || isSaving || isLoadFailed || !saved"
      @submit.prevent="handleSave"
    >
      <el-card
        v-show="activeSection === 'assessment'"
        class="panel"
      >
        <h2>测评开放状态</h2>
        <el-form-item label="允许开始新测评">
          <el-switch
            v-model="form.assessmentEnabled"
            aria-label="允许开始新测评"
          />
        </el-form-item>
        <el-form-item label="测评说明（暂停时必填）">
          <el-input
            v-model="form.assessmentNotice"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="例如：正在核校题库，暂不接受新测评；已有草稿可继续完成。"
          />
        </el-form-item>
      </el-card>
      <el-card
        v-show="activeSection === 'announcement'"
        class="panel"
      >
        <h2>工作台公告</h2>
        <el-form-item label="显示公告">
          <el-switch
            v-model="form.announcementEnabled"
            aria-label="显示公告"
          />
        </el-form-item>
        <el-form-item label="公告标题">
          <el-input
            v-model="form.announcementTitle"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="公告内容">
          <el-input
            v-model="form.announcementContent"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <p>关闭公告时保留文字，重新开启并保存后发布。公告以纯文本显示。</p>
        <details v-if="form.announcementEnabled">
          <summary>查看公告预览</summary>
          <aside
            class="preview"
            aria-label="公告预览"
          >
            <h3>{{ form.announcementTitle || '公告标题预览' }}</h3>
            <p>{{ form.announcementContent || '公告内容预览' }}</p>
          </aside>
        </details>
      </el-card>
      <footer class="save-actions">
        <span>
          配置版本 {{ form.revision }} · 最近更新 {{ updatedAt }} ·
          {{ isDirty ? '有未保存修改' : '与服务器一致' }}
        </span>
        <el-button
          :loading="isSaving"
          type="primary"
          native-type="submit"
        >
          保存系统配置
        </el-button>
      </footer>
    </el-form>
  </main>
</template>

<style scoped lang="scss">
.settings-page {
  max-width: 1100px;
  margin: auto;
  padding: clamp(16px, 3vw, 36px);
  color: var(--text-primary);
  min-width: 0;
}
.page-heading,
.save-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}
h1,
h2 {
  color: var(--brand-600);
}
p {
  line-height: 1.8;
}
.panel {
  margin-top: 20px;
  border-radius: 16px;
}
.preview {
  border: 1px solid var(--accent-200);
  background: var(--accent-50);
  padding: 20px;
  border-radius: 12px;
  overflow-wrap: anywhere;
}
.preview p {
  white-space: pre-wrap;
}
.save-actions {
  margin-top: 24px;
}
.save-actions span {
  color: var(--text-secondary);
  font-size: 14px;
}
</style>
