<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAiStatus, askAiEmployee, askAiDepartment } from '@/api/ai'
import { getDepartments } from '@/api/organization'
import type { AiAnswerVO, AiStatusVO } from '@/types/ai'
import type { DepartmentVO } from '@/types/organization'
import AiAnswer from '@/components/analysis/AiAnswer.vue'
const route = useRoute(),
  mode = ref('employee'),
  owner = ref<number>(),
  department = ref(0),
  question = ref('请整理最新测评的成长行动建议')
const departments = ref<DepartmentVO[]>([]),
  status = ref<AiStatusVO>(),
  answer = ref<AiAnswerVO>(),
  loading = ref(false),
  busy = ref(false),
  failed = ref(false),
  askFailed = ref(false)
let sequence = 0
async function load() {
  loading.value = true
  failed.value = false
  try {
    const [s, d] = await Promise.all([getAiStatus(), getDepartments()])
    status.value = s.data.data
    departments.value = d.data.data
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
watch([mode, owner, department, question], () => {
  sequence++
  answer.value = undefined
  askFailed.value = false
})
watch(
  () => route.query.userId,
  (value) => {
    const id = Number(value)
    if (Number.isSafeInteger(id) && id > 0) {
      mode.value = 'employee'
      owner.value = id
    }
  },
  { immediate: true },
)
async function ask() {
  if (busy.value || loading.value || failed.value) return
  if (
    !question.value.trim() ||
    question.value.length > 500 ||
    (mode.value === 'employee' &&
      (!owner.value || !Number.isSafeInteger(owner.value) || owner.value < 1))
  ) {
    ElMessage.warning('请填写有效档案ID及1至500字问题')
    return
  }
  const current = ++sequence
  busy.value = true
  askFailed.value = false
  answer.value = undefined
  try {
    const response = await (mode.value === 'employee'
      ? askAiEmployee(owner.value!, question.value)
      : askAiDepartment(department.value, question.value))
    if (current === sequence) answer.value = response.data.data
  } catch {
    if (current === sequence) askFailed.value = true
  } finally {
    busy.value = false
  }
}
onMounted(load)
</script>
<template>
  <main
    class="page"
    v-loading="loading"
  >
    <h1>人才与团队辅助问答</h1>
    <p>
      仅管理员使用。可询问当前档案角色、最新测评偏好和成长行动，或部门人数分布与协作提示。问题在本地识别范围，原文不发送给外部模型；不查询联系方式或判断能力、绩效。
    </p>
    <el-alert
      v-if="failed"
      title="配置加载失败，请重试"
      type="error"
      :closable="false"
    />
    <el-button
      :disabled="loading || busy"
      @click="load"
    >
      刷新配置
    </el-button>
    <p v-if="status">
      配置模式 {{ status.configuredMode }} · 外部调用{{
        status.externalEnabled ? '已开启' : '已关闭'
      }}
      · 真实API{{ status.remoteConfigured ? '已配置' : '未完整配置' }}
    </p>
    <el-button
      v-if="answer"
      @click="answer = undefined"
    >
      ← 返回问题编辑
    </el-button>
    <el-form
      v-show="!answer"
      label-position="top"
      @submit.prevent="ask"
    >
      <el-form-item label="问答范围">
        <el-radio-group
          v-model="mode"
          :disabled="busy"
        >
          <el-radio-button value="employee">人才档案</el-radio-button>
          <el-radio-button value="department">部门团队</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item
        v-if="mode === 'employee'"
        label="员工档案ID"
      >
        <el-input-number
          v-model="owner"
          :min="1"
          :precision="0"
          :disabled="busy"
        />
        <span class="hint">也可从看板人员明细进入，自动填入对应档案。</span>
      </el-form-item>
      <el-form-item
        v-else
        label="部门"
      >
        <el-select
          v-model="department"
          :disabled="busy"
        >
          <el-option
            label="未分配部门"
            :value="0"
          />
          <el-option
            v-for="d in departments"
            :key="d.id"
            :value="d.id"
            :label="d.departmentName"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="问题">
        <el-input
          v-model="question"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          :disabled="busy"
        />
      </el-form-item>
      <el-button
        type="primary"
        native-type="submit"
        :loading="busy"
        :disabled="loading || failed"
      >
        整理回答
      </el-button>
    </el-form>
    <el-alert
      v-if="askFailed"
      title="问答请求失败，已保留问题，可重试"
      type="warning"
      :closable="false"
    />
    <AiAnswer
      v-if="answer"
      :answer="answer"
    />
  </main>
</template>
<style scoped lang="scss">
.page {
  max-width: 1060px;
  margin: auto;
  padding: 28px 4vw;
  overflow-wrap: anywhere;
}
p {
  line-height: 1.8;
}
.el-form {
  margin-top: 24px;
}
.el-select {
  width: 320px;
  max-width: 100%;
}
.hint {
  margin-left: 12px;
  font-size: 12px;
}
.el-alert {
  margin-top: 16px;
}
</style>
