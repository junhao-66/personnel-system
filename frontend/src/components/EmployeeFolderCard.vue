<script setup lang="ts">
import type { EmployeeVO } from '@/types/employee'
defineProps<{ employee: EmployeeVO; selected: boolean; disabled?: boolean }>()
defineEmits<{ open: [employee: EmployeeVO] }>()
</script>

<template>
  <button
    class="employee-folder"
    :class="{ selected }"
    :aria-pressed="selected"
    :aria-label="'查看' + employee.realName + '的员工档案'"
    :disabled="disabled"
    @click="$emit('open', employee)"
  >
    <span
      class="folder-back"
      aria-hidden="true"
    ></span>
    <span
      class="folder-paper"
      aria-hidden="true"
    >
      <span>员工档案</span>
      <span></span>
      <span></span>
      <span></span>
    </span>
    <span class="folder-front">
      <span
        class="folder-monogram"
        aria-hidden="true"
      >
        {{ employee.realName.slice(0, 1) }}
      </span>
      <strong>{{ employee.realName }}</strong>
      <span class="folder-position">{{ employee.positionName || '未分配岗位' }}</span>
      <span class="folder-bottom">
        <span>{{ employee.departmentName || '未分配部门' }}</span>
        <span
          class="folder-state"
          :class="{ resigned: employee.employmentStatus === 'RESIGNED' }"
        >
          {{ employee.employmentStatus === 'ACTIVE' ? '在职' : '离职' }}
        </span>
      </span>
    </span>
  </button>
</template>

<style scoped lang="scss">
.employee-folder {
  position: relative;
  isolation: isolate;
  display: block;
  min-width: 0;
  width: 100%;
  height: 208px;
  padding: 0;
  border: 0;
  border-radius: 16px;
  background: none;
  text-align: left;
  cursor: pointer;
  color: #514b5d;
  font: inherit;
  transition: transform 500ms cubic-bezier(0.22, 1, 0.36, 1);
}
.employee-folder:hover:not(:disabled) {
  transform: translateY(-4px);
}
.employee-folder:active:not(:disabled) {
  transform: scale(0.98);
}
.employee-folder:disabled {
  cursor: default;
  opacity: 0.55;
}
.folder-back {
  position: absolute;
  inset: 16px 0 0;
  border: 1px solid var(--border-default);
  background: var(--bg-subtle);
  border-radius: 16px;
  box-shadow: inset 0 2px 6px #8a98aa0d;
}
.folder-back::before {
  content: '';
  position: absolute;
  top: -12px;
  left: 0;
  width: 48%;
  height: 32px;
  border: 1px solid var(--border-default);
  border-bottom: 0;
  background: var(--bg-subtle);
  border-radius: 12px 12px 0 0;
  z-index: -1;
}
.folder-paper {
  position: absolute;
  inset: 24px 12px 32px;
  padding: 16px;
  background: #fff;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  transform: rotate(-3deg);
  transition: transform 500ms cubic-bezier(0.22, 1, 0.36, 1);
  color: var(--text-disabled);
  font-size: 12px;
  line-height: 16px;
}
.folder-paper > span:not(:first-child) {
  display: block;
  height: 4px;
  background: var(--bg-subtle);
  margin-top: 12px;
  border-radius: 4px;
}
.folder-paper > span:last-child {
  width: 65%;
}
.folder-front {
  position: absolute;
  inset: 64px 0 0;
  display: flex;
  flex-direction: column;
  padding: 16px;
  border: 1px solid var(--border-light);
  background: var(--bg-subtle);
  border-radius: 12px 12px 16px 16px;
  box-shadow:
    inset 0 2px 6px #fff9,
    0 8px 16px #8291a10a;
  backdrop-filter: blur(8px);
}
.folder-monogram {
  position: absolute;
  right: 16px;
  top: 16px;
  width: 24px;
  height: 24px;
  border: 1px solid var(--border-default);
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: var(--text-disabled);
  font-size: 12px;
  line-height: 16px;
}
.folder-front strong {
  margin: 12px 32px 4px 0;
  font-weight: 500;
  font-size: 16px;
  line-height: 24px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.folder-position {
  font-size: 12px;
  line-height: 16px;
  color: var(--text-disabled);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.folder-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
  padding-top: 16px;
  font-size: 12px;
  line-height: 16px;
  color: var(--text-disabled);
}
.folder-bottom > span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.folder-state {
  font-size: 12px;
  line-height: 16px;
  color: var(--accent-600);
  white-space: nowrap;
}
.folder-state.resigned {
  color: #9a9da6;
}
.selected .folder-back,
.selected .folder-back::before {
  background: #72bcb3;
  border-color: #72bcb3;
}
.selected .folder-paper {
  transform: translateY(-8px) rotate(3deg);
}
.selected .folder-front {
  background: #3c958beb;
  border-color: #5ba69d;
  box-shadow:
    inset 0 2px 4px #d4efebaa,
    0 12px 24px #255a5322;
  color: #fff;
}
.selected :is(.folder-position, .folder-bottom, .folder-state) {
  color: #f1faf7;
}
.selected .folder-monogram {
  color: #f1faf7;
  border-color: #bce2db;
}
@media (prefers-reduced-motion: reduce) {
  .employee-folder,
  .folder-paper {
    transition: none;
  }
}
</style>
