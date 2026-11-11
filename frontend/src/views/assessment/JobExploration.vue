<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { getJobExploration } from '@/api/analysis'
import type { JobExplorationVO } from '@/types/analysis'
import RuleEvidence from '@/components/analysis/RuleEvidence.vue'
const props = defineProps<{ ownerId?: number }>()
const data = ref<JobExplorationVO>(),
  loading = ref(false),
  failed = ref(false)
let sequence = 0
async function load() {
  const current = ++sequence
  loading.value = true
  failed.value = false
  data.value = undefined
  try {
    const response = await getJobExploration(props.ownerId)
    if (current === sequence) data.value = response.data.data
  } catch {
    if (current === sequence) failed.value = true
  } finally {
    if (current === sequence) loading.value = false
  }
}
watch(() => props.ownerId, load)
onMounted(load)
</script>
<template>
  <main
    class="page"
    v-loading="loading"
  >
    <RouterLink :to="ownerId ? '/admin/talent-employees' : '/workbench'">
      返回{{ ownerId ? '人员明细' : '工作台' }}
    </RouterLink>
    <header>
      <h1>岗位偏好探索</h1>
      <el-button
        :disabled="loading"
        @click="load"
      >
        刷新探索
      </el-button>
    </header>
    <el-alert
      v-if="failed"
      title="加载失败，请重试"
      type="error"
      :closable="false"
    />
    <template v-if="data">
      <el-alert
        :title="data.notice"
        type="info"
        :closable="false"
      />
      <el-empty
        v-if="!data.resultId"
        description="请先完成一次测评"
      >
        <RouterLink
          v-if="!ownerId"
          to="/assessment"
        >
          开始测评
        </RouterLink>
      </el-empty>
      <template v-else>
        <p>
          采用最新已提交报告 #{{ data.resultId }} · 量表 {{ data.scaleVersion }} · 图谱
          {{ data.graphVersion || '旧报告未记录' }}
        </p>
        <el-empty
          v-if="!data.positions.length"
          description="暂无已发布且岗位启用的探索配置"
        />
        <el-card
          v-for="item in data.positions"
          :key="item.profile.id"
          class="position"
        >
          <h2>{{ item.profile.definition.positionName }} · {{ item.profile.definition.title }}</h2>
          <p>
            {{ item.profile.definition.version }} · 图谱 {{ item.profile.definition.graphVersion }}
          </p>
          <h3>
            演示规则覆盖率：{{
              item.comparable ? `${item.ruleCoveragePercent}%` : '不计算（依据版本不同）'
            }}
          </h3>
          <p>{{ item.explanation }}</p>
          <p>配置依据：{{ item.profile.definition.attribution }}</p>
          <section
            v-for="rule in item.profile.definition.rules"
            :key="rule.rule.id"
          >
            <el-tag
              :type="
                item.comparable && item.matchedRuleIds.includes(rule.rule.id) ? 'success' : 'info'
              "
            >
              {{
                !item.comparable
                  ? '不比较'
                  : item.matchedRuleIds.includes(rule.rule.id)
                    ? '当次快照命中'
                    : '当次快照未命中'
              }}
            </el-tag>
            <RuleEvidence :evidence="rule" />
          </section>
        </el-card>
      </template>
    </template>
  </main>
</template>
<style scoped lang="scss">
.page {
  max-width: 1080px;
  margin: auto;
  padding: 28px 4vw;
  overflow-wrap: anywhere;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
p {
  line-height: 1.7;
}
.position {
  margin-top: 24px;
}
a {
  color: var(--brand-600);
}
</style>
