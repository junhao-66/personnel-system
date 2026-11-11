<script setup lang="ts">
// Authenticated PDF fetched as a blob; no bearer token appears in a URL or frame address.
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getAssessmentPdf } from '@/api/assessment'

const props = defineProps<{ resultId: number }>()
const isVisible = ref(false)
const isLoading = ref(false)
const isFailed = ref(false)
const pdfUrl = ref('')
let generation = 0
let isDisposed = false
const clearPreview = () => {
  generation++
  if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value)
  pdfUrl.value = ''
  isFailed.value = false
}
const getPreview = async () => {
  if (isLoading.value || isDisposed) return
  clearPreview()
  isLoading.value = true
  const current = generation
  const id = props.resultId
  try {
    const response = await getAssessmentPdf(id)
    if (isDisposed || !isVisible.value || current !== generation || props.resultId !== id) return
    const signature = await response.data.slice(0, 5).text()
    if (signature !== '%PDF-') throw new Error('INVALID_PDF')
    // Blob reads are asynchronous too: closing or switching reports must cancel display.
    if (isDisposed || !isVisible.value || current !== generation || props.resultId !== id) return
    pdfUrl.value = URL.createObjectURL(response.data)
  } catch (error) {
    if (isDisposed || !isVisible.value || current !== generation) return
    isFailed.value = true
    if (error instanceof Error && error.message === 'INVALID_PDF')
      ElMessage.error('服务器未返回有效PDF，请稍后重试')
  } finally {
    isLoading.value = false
  }
}
const handleOpen = () => {
  if (isLoading.value) return
  isVisible.value = true
  void getPreview()
}
const handleDownload = () => {
  if (!pdfUrl.value) return
  const anchor = document.createElement('a')
  anchor.href = pdfUrl.value
  anchor.download = `assessment-report-${props.resultId}.pdf`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
}
watch(isVisible, (visible) => {
  if (!visible) clearPreview()
})
watch(
  () => props.resultId,
  () => {
    isVisible.value = false
    clearPreview()
  },
)
onBeforeUnmount(() => {
  isDisposed = true
  clearPreview()
})
</script>

<template>
  <span class="pdf-preview-action">
    <el-button
      :loading="isLoading"
      @click="handleOpen"
    >
      在线预览PDF
    </el-button>
    <el-dialog
      v-model="isVisible"
      title="测评报告PDF预览"
      width="min(1100px, 94vw)"
      top="3vh"
      append-to-body
      :close-on-click-modal="false"
    >
      <section
        v-loading="isLoading"
        class="preview-body"
        aria-label="PDF预览区域"
      >
        <p>预览与导出使用同一份已保存的报告。若浏览器不能显示PDF，可下载后查看。</p>
        <el-alert
          v-if="isFailed"
          title="PDF预览加载失败，报告保持不变，可重试。"
          type="error"
          :closable="false"
        />
        <el-button
          v-if="isFailed"
          :loading="isLoading"
          @click="getPreview"
        >
          重新加载PDF预览
        </el-button>
        <iframe
          v-if="pdfUrl"
          :src="pdfUrl"
          title="测评报告PDF内容"
          class="pdf-frame"
        />
        <p
          v-else-if="isLoading"
          role="status"
        >
          正在生成PDF预览…
        </p>
      </section>
      <template #footer>
        <el-button
          :disabled="!pdfUrl || isLoading"
          @click="handleDownload"
        >
          下载当前预览PDF
        </el-button>
        <el-button @click="isVisible = false">关闭PDF预览</el-button>
      </template>
    </el-dialog>
  </span>
</template>

<style scoped lang="scss">
.pdf-preview-action {
  display: inline-block;
  margin: 0 0 0 12px;
}
.preview-body {
  min-height: 160px;
  min-width: 0;
  color: var(--text-primary);
}
.preview-body p {
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.pdf-frame {
  display: block;
  width: 100%;
  height: 68vh;
  min-height: 260px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  margin-top: 16px;
}
</style>
