<script setup lang="ts">
// Immutable graph bundles, reviewed and published by administrators.
import { onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  archiveGraphVersion,
  getGraphVersions,
  importGraphVersion,
  publishGraphVersion,
} from '@/api/knowledgeGraph'
import type { GraphImportReqDTO, KnowledgeGraphVO } from '@/types/knowledgeGraph'
import GraphVersionEditor from './components/GraphVersionEditor.vue'
const editing = ref<KnowledgeGraphVO>()
const isEditingBusy = ref(false)
const handleSaved = async (value: KnowledgeGraphVO) => {
  editing.value = undefined
  selected.value = value
  await getData()
}
onBeforeRouteLeave(async () => {
  if (isEditingBusy.value) {
    ElMessage.warning('正在保存，请等待完成')
    return false
  }
  if (!editing.value) return true
  try {
    await ElMessageBox.confirm('离开后尚未保存的编辑副本将丢失。', '离开编辑', {
      confirmButtonText: '离开',
      cancelButtonText: '继续编辑',
    })
    return true
  } catch {
    return false
  }
})
const versions = ref<KnowledgeGraphVO[]>([])
const selected = ref<KnowledgeGraphVO>()
const importText = ref('')
const isLoading = ref(false)
const isSubmitting = ref(false)
const isLoadFailed = ref(false)
const statusLabels = { DRAFT: '待发布', PUBLISHED: '已发布', ARCHIVED: '已停用' }
const getData = async () => {
  isLoading.value = true
  isLoadFailed.value = false
  try {
    versions.value = (await getGraphVersions()).data.data
  } catch {
    isLoadFailed.value = true
  } finally {
    isLoading.value = false
  }
}
const handleImport = async () => {
  let value: unknown
  try {
    value = JSON.parse(importText.value)
  } catch {
    ElMessage.warning('JSON格式不正确')
    return
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    ElMessage.warning('请导入图谱JSON对象')
    return
  }
  isSubmitting.value = true
  try {
    await importGraphVersion(value as GraphImportReqDTO)
    importText.value = ''
    ElMessage.success('草稿已导入，请核查来源和规则后发布')
    await getData()
  } catch {
    /* Unified request errors. */
  } finally {
    isSubmitting.value = false
  }
}
const handleStatus = async (row: KnowledgeGraphVO, publish: boolean) => {
  if (isSubmitting.value) return
  try {
    await ElMessageBox.confirm(
      publish
        ? '确认已核查来源、许可、演示标记和多维规则？系统优先采用后导入的已发布版本，旧报告快照保留。'
        : '停用后使用剩余已发布版本中最新的一份，没有可用版本时保留基础报告。',
      publish ? '发布图谱' : '停用图谱',
      { confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  isSubmitting.value = true
  try {
    if (publish) await publishGraphVersion(row.id)
    else await archiveGraphVersion(row.id)
    selected.value = undefined
    await getData()
  } catch {
    /* Unified request errors. */
  } finally {
    isSubmitting.value = false
  }
}
const showImport = ref(false)
onMounted(getData)
</script>
<template>
  <main class="manage-page">
    <h1>知识图谱版本管理</h1>
    <RouterLink to="/knowledge-graph">查看已发布关系图</RouterLink>
    <el-alert
      title="当前成长关系为项目演示假设；题库来源只支持题目及计分结构，不能当作职业或成长结论的证据。"
      type="warning"
      :closable="false"
    />
    <nav
      v-if="!editing && !selected"
      class="section-navigation"
      aria-label="知识库管理"
    >
      <button
        type="button"
        :aria-pressed="!showImport"
        @click="showImport = false"
      >
        版本列表
      </button>
      <button
        type="button"
        :aria-pressed="showImport"
        @click="showImport = true"
      >
        导入版本
      </button>
    </nav>
    <GraphVersionEditor
      v-if="editing"
      :key="editing.id"
      :graph="editing"
      @saved="handleSaved"
      @cancel="editing = undefined"
      @busy="isEditingBusy = $event"
    />
    <el-card
      v-if="showImport && !editing && !selected"
      class="panel"
    >
      <h2>导入新版本</h2>
      <p>
        使用项目根目录graph-example.json作为结构参考。也可从下方选择“编辑为新版本”，通过表单维护节点和关系。内容变更创建新版本；须提供来源、节点、关系和多维条件规则。
      </p>
      <el-input
        v-model="importText"
        type="textarea"
        :rows="5"
        :disabled="isSubmitting || !!editing"
        maxlength="500000"
        placeholder="粘贴图谱JSON：version、title、sources、nodes、edges、rules"
      />
      <el-button
        :loading="isSubmitting"
        :disabled="!importText.trim() || !!editing"
        type="primary"
        @click="handleImport"
      >
        导入图谱草稿
      </el-button>
    </el-card>
    <el-card
      v-if="!showImport && !editing && !selected"
      class="panel"
    >
      <el-button
        :loading="isLoading"
        @click="getData"
      >
        刷新图谱版本
      </el-button>
      <el-alert
        v-if="isLoadFailed"
        title="版本加载失败，请重试"
        type="error"
        :closable="false"
      />
      <el-table
        v-loading="isLoading"
        :data="versions"
      >
        <el-table-column
          prop="version"
          label="版本"
          min-width="150"
        />
        <el-table-column
          prop="title"
          label="标题"
          min-width="200"
        />
        <el-table-column
          label="状态"
          width="100"
        >
          <template #default="scope">
            {{ statusLabels[scope.row.status as keyof typeof statusLabels] }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          min-width="200"
        >
          <template #default="scope">
            <el-button
              :disabled="!!editing || isSubmitting"
              link
              type="primary"
              @click="editing = scope.row as KnowledgeGraphVO"
            >
              编辑为新版本
            </el-button>
            <el-button
              link
              @click="selected = scope.row as KnowledgeGraphVO"
            >
              查看依据与规则
            </el-button>
            <el-button
              v-if="scope.row.status === 'DRAFT'"
              :disabled="isSubmitting || !!editing"
              link
              type="primary"
              @click="handleStatus(scope.row as KnowledgeGraphVO, true)"
            >
              发布
            </el-button>
            <el-button
              v-if="scope.row.status === 'PUBLISHED'"
              :disabled="isSubmitting || !!editing"
              link
              type="danger"
              @click="handleStatus(scope.row as KnowledgeGraphVO, false)"
            >
              停用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-card
      v-if="selected"
      class="panel"
    >
      <el-button @click="selected = undefined">← 返回版本列表</el-button>
      <h2>{{ selected.version }} · {{ selected.title }}</h2>
      <p>摘要：{{ selected.contentHash }}</p>
      <pre>{{ JSON.stringify(selected, null, 2) }}</pre>
    </el-card>
  </main>
</template>
<style scoped lang="scss">
.manage-page {
  max-width: 1280px;
  margin: auto;
  padding: clamp(16px, 4vw, 48px);
  overflow-wrap: anywhere;
}
h1,
h2 {
  color: var(--brand-600);
}
.panel {
  margin: 20px 0;
  border-radius: 14px;
}
p {
  line-height: 1.8;
}
pre {
  max-height: 600px;
  overflow: auto;
  white-space: pre-wrap;
}
.el-button {
  margin-top: 12px;
}
</style>
