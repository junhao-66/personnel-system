<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getJobProfiles,
  createJobProfile,
  publishJobProfile,
  archiveJobProfile,
} from '@/api/analysis'
import { getKnowledgeGraph } from '@/api/knowledgeGraph'
import { getPositions } from '@/api/organization'
import type { JobProfileVO, JobProfileCreateReqDTO } from '@/types/analysis'
import type { KnowledgeGraphVO } from '@/types/knowledgeGraph'
import type { PositionVO } from '@/types/organization'
import RuleEvidence from '@/components/analysis/RuleEvidence.vue'
const profiles = ref<JobProfileVO[]>([]),
  positions = ref<PositionVO[]>([]),
  graph = ref<KnowledgeGraphVO | null>(null)
const loading = ref(false),
  failed = ref(false),
  saving = ref(false),
  changing = ref(false),
  open = ref(false)
const form = ref<JobProfileCreateReqDTO>({ version: '', title: '', attribution: '', rules: [] })
const total = computed(() => form.value.rules.reduce((sum, r) => sum + (r.weight || 0), 0))
const profilePage = ref(1)
const visibleProfiles = computed(() =>
  profiles.value.slice((profilePage.value - 1) * 4, profilePage.value * 4),
)
const labels = { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已停用' }
async function load() {
  loading.value = true
  failed.value = false
  try {
    const [p, o, g] = await Promise.all([getJobProfiles(), getPositions(), getKnowledgeGraph()])
    profiles.value = p.data.data
    positions.value = o.data.data
    graph.value = g.data.data
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
function edit(profile?: JobProfileVO) {
  form.value = {
    positionId: profile?.definition.positionId,
    version: '',
    title: profile?.definition.title || '',
    attribution: profile?.definition.attribution || '',
    graphId: graph.value?.id,
    rules: profile
      ? profile.definition.rules.map((r) => ({ ruleId: r.rule.id, weight: r.weight }))
      : [{ ruleId: '', weight: 100 }],
  }
  open.value = true
}
async function save() {
  if (saving.value) return
  if (
    !form.value.positionId ||
    !/^[A-Za-z0-9._-]{1,64}$/.test(form.value.version) ||
    !form.value.title.trim() ||
    !form.value.attribution.trim() ||
    !form.value.graphId ||
    !form.value.rules.length ||
    total.value !== 100 ||
    form.value.rules.some(
      (r) => !r.ruleId || !Number.isInteger(r.weight) || r.weight < 1 || r.weight > 100,
    ) ||
    new Set(form.value.rules.map((r) => r.ruleId)).size !== form.value.rules.length
  ) {
    ElMessage.warning('请选择岗位、填写新版本号与依据，选择不重复的规则，整数权重合计为100')
    return
  }
  saving.value = true
  try {
    await createJobProfile(form.value)
    open.value = false
    ElMessage.success('草稿已保存')
    await load()
  } catch {
    /* 请求层提示错误，保留配置供修改或重试。 */
  } finally {
    saving.value = false
  }
}
async function change(profile: JobProfileVO, publish: boolean) {
  if (changing.value) return
  changing.value = true
  try {
    try {
      await ElMessageBox.confirm(
        publish
          ? '发布后，同岗位最新创建的已发布版本生效；既往报告不会补算。继续？'
          : '停用后，同岗位较早的已发布配置可能重新生效。继续？',
        publish ? '发布岗位配置' : '停用岗位配置',
        { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    if (publish) await publishJobProfile(profile.id)
    else await archiveJobProfile(profile.id)
    ElMessage.success('状态已更新')
    await load()
  } catch {
    /* 请求层提示错误；不猜测失败操作是否生效，可刷新核验。 */
  } finally {
    changing.value = false
  }
}
onMounted(load)
</script>
<template>
  <main
    class="page"
    v-loading="loading"
  >
    <header>
      <div>
        <h1>岗位探索配置</h1>
        <p>为真实岗位组合多个图谱规则与权重。用于自愿探索，不用于招聘、绩效或能力评价。</p>
      </div>
      <el-button
        type="primary"
        :disabled="loading || failed || !graph || changing"
        @click="edit()"
      >
        新建配置
      </el-button>
    </header>
    <el-alert
      v-if="failed"
      title="加载失败，请重试"
      type="error"
      :closable="false"
    />
    <el-button
      :disabled="loading"
      @click="load"
    >
      刷新列表与图谱
    </el-button>
    <el-alert
      v-if="!failed && !loading && !graph"
      title="请先发布知识图谱，再配置岗位规则"
      type="warning"
      :closable="false"
    />
    <el-empty
      v-if="!failed && !loading && !profiles.length"
      description="暂无岗位探索配置"
    />
    <div class="profile-grid">
      <el-card
        v-for="profile in visibleProfiles"
        :key="profile.id"
        class="profile"
      >
        <h2>{{ profile.definition.positionName }} · {{ profile.definition.title }}</h2>
        <p>
          {{ profile.definition.version }} · {{ labels[profile.status] }} · 图谱
          {{ profile.definition.graphVersion }}
        </p>
        <p>{{ profile.definition.attribution }}</p>
        <details>
          <summary>查看配置规则与来源</summary>
          <p class="hash">内容校验：{{ profile.contentHash }}</p>
          <RuleEvidence
            v-for="rule in profile.definition.rules"
            :key="rule.rule.id"
            :evidence="rule"
          />
        </details>
        <el-button
          :disabled="loading || failed || changing || !graph"
          @click="edit(profile)"
        >
          复制为新版本
        </el-button>
        <el-button
          v-if="profile.status === 'DRAFT'"
          :disabled="loading || failed"
          :loading="changing"
          @click="change(profile, true)"
        >
          发布
        </el-button>
        <el-button
          v-if="profile.status === 'PUBLISHED'"
          :disabled="loading || failed"
          :loading="changing"
          @click="change(profile, false)"
        >
          停用
        </el-button>
      </el-card>
    </div>
    <el-pagination
      v-if="profiles.length"
      v-model:current-page="profilePage"
      :page-size="4"
      :total="profiles.length"
      layout="total, prev, pager, next"
    />
    <el-dialog
      v-model="open"
      title="新建岗位探索版本"
      width="min(760px, 94vw)"
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
    >
      <el-form
        label-position="top"
        @submit.prevent="save"
      >
        <el-form-item label="岗位">
          <el-select
            v-model="form.positionId"
            :disabled="saving"
            placeholder="选择启用岗位"
          >
            <el-option
              v-for="position in positions.filter((p) => p.isEnabled === 1)"
              :key="position.id"
              :label="position.positionName"
              :value="position.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="新版本号">
          <el-input
            v-model="form.version"
            :disabled="saving"
            maxlength="64"
            placeholder="英文、数字、点、下划线、连字符"
          />
        </el-form-item>
        <el-form-item label="标题">
          <el-input
            v-model="form.title"
            :disabled="saving"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item label="配置依据与维护者归属">
          <el-input
            v-model="form.attribution"
            type="textarea"
            :disabled="saving"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <p>固定依据图谱：{{ graph?.version }}。全部条件需同时满足，权重为演示配置。</p>
        <div
          v-for="(selection, index) in form.rules"
          :key="index"
          class="rule-row"
        >
          <el-select
            v-model="selection.ruleId"
            :disabled="saving"
            :aria-label="`规则${index + 1}`"
            placeholder="选择多维规则"
          >
            <el-option
              v-for="rule in graph?.rules || []"
              :key="rule.id"
              :label="`${rule.id}：${rule.explanation}`"
              :value="rule.id"
            />
          </el-select>
          <el-input-number
            v-model="selection.weight"
            :disabled="saving"
            :min="1"
            :max="100"
            :precision="0"
            :aria-label="`权重${index + 1}`"
          />
          <el-button
            :disabled="saving || form.rules.length <= 1"
            @click="form.rules.splice(index, 1)"
          >
            移除
          </el-button>
        </div>
        <p>权重合计：{{ total }} / 100</p>
        <el-button
          :disabled="saving || form.rules.length >= Math.min(8, graph?.rules.length || 0)"
          @click="form.rules.push({ ruleId: '', weight: 1 })"
        >
          添加规则
        </el-button>
      </el-form>
      <template #footer>
        <el-button
          :disabled="saving"
          @click="open = false"
        >
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="save"
        >
          保存草稿
        </el-button>
      </template>
    </el-dialog>
  </main>
</template>
<style scoped lang="scss">
.page {
  max-width: 1160px;
  margin: auto;
  padding: 28px 4vw;
}
header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
p {
  line-height: 1.7;
}
.profile {
  margin-top: 20px;
}
.hash {
  overflow-wrap: anywhere;
  font-size: 12px;
}
.rule-row {
  display: flex;
  gap: 8px;
  margin: 12px 0;
}
.rule-row .el-select {
  flex: 1;
  min-width: 0;
}
.el-form-item .el-select {
  width: 100%;
}
@media (max-width: 600px) {
  .rule-row {
    flex-wrap: wrap;
  }
  .rule-row .el-select {
    flex-basis: 100%;
  }
}
</style>
