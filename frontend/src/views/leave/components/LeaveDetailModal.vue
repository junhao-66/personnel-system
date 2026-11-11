<script setup lang="ts">
// Read-only details from the already authorized list; never requests another person's record.
import type { LeaveStatus, LeaveVO } from '@/types/leave'
import { getLeaveMinutes } from '@/utils/leave'

defineProps<{ record?: LeaveVO }>()
const emit = defineEmits<{ close: [] }>()
const labels: Record<LeaveStatus, string> = {
  PENDING: '待审批',
  APPROVED: '已批准',
  REJECTED: '已拒绝',
}
const formatTime = (value?: string) => value?.replace('T', ' ').slice(0, 16) || '—'
const handleClose = () => emit('close')
</script>

<template>
  <el-dialog
    :model-value="!!record"
    title="请假申请详情"
    width="min(680px, 94vw)"
    @close="handleClose"
  >
    <dl
      v-if="record"
      class="leave-detail"
    >
      <dt>申请编号</dt>
      <dd>{{ record.id }}</dd>
      <dt>申请人</dt>
      <dd>{{ record.realName || '本人' }}</dd>
      <dt>请假类型</dt>
      <dd>{{ record.leaveType }}</dd>
      <dt>审批状态</dt>
      <dd>{{ labels[record.status] }}</dd>
      <dt>开始时间</dt>
      <dd>{{ formatTime(record.startTime) }}</dd>
      <dt>结束时间</dt>
      <dd>{{ formatTime(record.endTime) }}</dd>
      <dt>日历时长</dt>
      <dd>{{ getLeaveMinutes(record.startTime, record.endTime) ?? '—' }} 分钟（非考勤扣减时长）</dd>
      <dt>申请原因</dt>
      <dd>{{ record.reason }}</dd>
      <dt>提交时间</dt>
      <dd>{{ formatTime(record.createTime) }}</dd>
      <dt>审批意见</dt>
      <dd>
        {{ record.reviewComment || (record.status === 'PENDING' ? '等待审批' : '无补充意见') }}
      </dd>
      <dt>处理时间</dt>
      <dd>{{ formatTime(record.reviewedAt) }}</dd>
    </dl>
    <p>时间按 Asia/Shanghai 显示。考勤仅合并已批准请假所覆盖的应工作时段，不直接按日历时长扣减。</p>
    <template #footer><el-button @click="handleClose">关闭请假详情</el-button></template>
  </el-dialog>
</template>

<style scoped lang="scss">
.leave-detail {
  display: grid;
  grid-template-columns: minmax(80px, 100px) minmax(0, 1fr);
  gap: 12px 16px;
  line-height: 1.7;
}
dt {
  font-weight: 600;
  color: var(--text-primary);
}
dd {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
p {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.8;
}
@media (max-width: 480px) {
  .leave-detail {
    grid-template-columns: minmax(0, 1fr);
    gap: 6px;
  }
  dd {
    margin-bottom: 10px;
  }
}
</style>
