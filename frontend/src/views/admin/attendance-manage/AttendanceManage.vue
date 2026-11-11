<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import AttendanceRecords from '@/components/attendance/AttendanceRecords.vue'
import { getAttendanceRule, updateAttendanceRule } from '@/api/attendance'

const route = useRoute()
const isRules = computed(() => route.path.endsWith('/rules'))
const formRef = ref<FormInstance>()
const isLoading = ref(false)
const isSubmitting = ref(false)
const isRuleReady = ref(false)
const isLoadFailed = ref(false)
const form = reactive({ workStartTime: '', workEndTime: '' })
const rules: FormRules = {
  workStartTime: [{ required: true, message: '请选择上班时间', trigger: 'change' }],
  workEndTime: [
    { required: true, message: '请选择下班时间', trigger: 'change' },
    {
      validator: (_rule, value: string, callback) =>
        value && form.workStartTime && value <= form.workStartTime
          ? callback(new Error('下班时间必须晚于上班时间'))
          : callback(),
      trigger: 'change',
    },
  ],
}
const loadRule = async () => {
  isLoading.value = true
  isRuleReady.value = false
  isLoadFailed.value = false
  try {
    const response = await getAttendanceRule()
    Object.assign(form, {
      workStartTime: response.data.data.workStartTime,
      workEndTime: response.data.data.workEndTime,
    })
    isRuleReady.value = true
  } catch {
    isLoadFailed.value = true
  } finally {
    isLoading.value = false
  }
}
const handleSave = async () => {
  if (
    isSubmitting.value ||
    !isRuleReady.value ||
    !(await formRef.value?.validate().catch(() => false))
  )
    return
  isSubmitting.value = true
  try {
    try {
      await ElMessageBox.confirm(
        '新作息仅影响之后首次上班打卡的记录，已有记录保留打卡时的规则。确认保存？',
        '修改考勤规则',
        { confirmButtonText: '确认保存', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    try {
      await updateAttendanceRule({ ...form })
      ElMessage.success('考勤规则已保存')
      await loadRule()
    } catch {
      /* 请求层提示错误，保留编辑内容。 */
    }
  } finally {
    isSubmitting.value = false
  }
}
onMounted(loadRule)
</script>

<template>
  <main class="attendance-page">
    <header class="page-heading">
      <h1>{{ isRules ? '作息设置' : '考勤记录' }}</h1>
      <p>
        {{
          isRules
            ? '设置上下班时间，保存后应用于后续新打卡记录。'
            : '查询员工每日出勤、迟到早退与缺卡情况。'
        }}
      </p>
      <RouterLink
        :to="isRules ? '/admin/attendance' : '/admin/attendance/rules'"
        class="back-link"
      >
        {{ isRules ? '返回考勤记录' : '设置上下班时间' }}
      </RouterLink>
    </header>
    <el-card
      v-if="isRules"
      v-loading="isLoading"
      class="rule-panel"
    >
      <h2>固定作息规则</h2>
      <el-alert
        v-if="isLoadFailed"
        title="考勤规则加载失败，暂不能保存，请重试。"
        type="error"
        :closable="false"
      />
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        :disabled="!isRuleReady || isSubmitting"
        label-position="top"
        @submit.prevent="handleSave"
        class="rule-form"
      >
        <el-form-item
          label="上班时间"
          prop="workStartTime"
        >
          <el-time-picker
            v-model="form.workStartTime"
            format="HH:mm"
            value-format="HH:mm:ss"
            :editable="false"
          />
        </el-form-item>
        <el-form-item
          label="下班时间"
          prop="workEndTime"
        >
          <el-time-picker
            v-model="form.workEndTime"
            format="HH:mm"
            value-format="HH:mm:ss"
            :editable="false"
          />
        </el-form-item>
        <div class="rule-actions">
          <el-button
            type="primary"
            native-type="submit"
            :loading="isSubmitting"
          >
            保存规则
          </el-button>
        </div>
      </el-form>
      <el-button
        text
        :disabled="isSubmitting"
        @click="loadRule"
      >
        重新读取规则
      </el-button>
      <p class="hint">
        规则精确到分钟，不支持跨日班次。已上班打卡的当天记录保持原作息，后续调整不追溯修改历史判定。
      </p>
    </el-card>
    <el-card
      v-if="!isRules"
      class="records-panel"
    >
      <AttendanceRecords admin-mode />
    </el-card>
  </main>
</template>

<style scoped lang="scss">
.attendance-page {
  min-height: calc(100vh - 88px);
  padding: clamp(20px, 4vw, 48px);
}
.page-heading,
.rule-panel,
.records-panel {
  max-width: 1320px;
  margin: 0 auto 24px;
}
.eyebrow {
  color: var(--brand-600);
  font-size: 12px;
  letter-spacing: 2px;
  font-weight: 700;
}
h1,
h2 {
  color: var(--text-strong);
}
h1 {
  margin: 8px 0;
}
h2 {
  margin: 0 0 20px;
  font-size: 20px;
}
.page-heading p {
  color: var(--text-strong);
}
.rule-panel,
.records-panel {
  border-radius: 18px;
}
.rule-form {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 20px;
}
.rule-actions {
  margin-bottom: 18px;
}
.hint {
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.7;
}
@media (max-width: 600px) {
  .rule-form {
    display: block;
  }
  :deep(.el-date-editor.el-input) {
    width: 100%;
  }
}
</style>
