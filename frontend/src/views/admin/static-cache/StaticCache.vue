<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getStaticCache, refreshStaticCache } from '@/api/staticCache'
import type { StaticCacheStatusVO } from '@/types/staticCache'
const data = ref<StaticCacheStatusVO>(),
  busy = ref(false),
  failed = ref(false)
const states = {
  DISABLED: '未开启，直接读取数据库',
  MYSQL_FALLBACK: '缓存故障冷却中，已回源数据库',
  READY: '允许尝试缓存',
}
const catalogs: Record<string, string> = {
  'catalog-scales': '已发布题库',
  'catalog-graph': '静态图谱',
  'catalog-growth': '建议模板',
  'catalog-jobs': '岗位规则与权重',
  'catalog-prompts': '提示词模板',
  'catalog-dictionary': '系统字典',
}
async function load(refresh = false) {
  if (busy.value) return
  busy.value = true
  failed.value = false
  try {
    data.value = (await (refresh ? refreshStaticCache() : getStaticCache())).data.data
    if (refresh) {
      if (!data.value.enabled) ElMessage.info('缓存未开启，业务继续读取数据库')
      else if (data.value.state === 'MYSQL_FALLBACK')
        ElMessage.warning('缓存暂不可用，业务已回源数据库')
      else ElMessage.success('已清理缓存并重新加载静态目录')
    }
  } catch {
    failed.value = true
  } finally {
    busy.value = false
  }
}
onMounted(() => load())
</script>
<template>
  <main
    class="page"
    v-loading="busy"
  >
    <h1>静态缓存维护</h1>
    <p>
      缓存不会保存员工答卷、个人测评报告或团队动态档案。发布/停用内容后自动失效，再次访问加载最新目录。
    </p>
    <el-button
      :disabled="busy"
      @click="load()"
    >
      刷新状态
    </el-button>
    <el-button
      type="primary"
      :disabled="busy || !data?.enabled || failed"
      @click="load(true)"
    >
      清理并重新加载静态缓存
    </el-button>
    <el-alert
      v-if="failed"
      title="状态读取失败，请重试"
      type="error"
      :closable="false"
    />
    <el-card v-if="data">
      <h2>{{ states[data.state] }}</h2>
      <p>有效期 {{ data.ttlSeconds }} 秒 · 本进程缓存故障计数 {{ data.failureCount }}</p>
      <p>{{ data.notice }}</p>
      <p
        v-for="catalog in data.catalogs"
        :key="catalog"
      >
        {{ catalogs[catalog] || catalog }}
      </p>
    </el-card>
  </main>
</template>
<style scoped lang="scss">
.page {
  max-width: 1000px;
  margin: auto;
  padding: 28px 4vw;
}
p {
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.el-card,
.el-alert {
  margin-top: 20px;
}
.el-button {
  max-width: 100%;
  margin-bottom: 10px;
}
@media (max-width: 600px) {
  .el-button + .el-button {
    margin-left: 0;
  }
}
</style>
