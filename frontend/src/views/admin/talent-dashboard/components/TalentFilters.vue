<script setup lang="ts">
// Shared filter form for dashboard and its drill-down list; parent owns the applied query.
import { onMounted, reactive, ref, watch } from 'vue'
import { getDepartments, getPositions } from '@/api/organization'
import type { DepartmentVO, PositionVO } from '@/types/organization'
import type { TalentQueryReqDTO } from '@/types/talent'
import { defaultTalentQuery, PERSONALITY_TYPES } from '@/utils/talent'

const props = defineProps<{ query: TalentQueryReqDTO; isLoading: boolean }>()
const emit = defineEmits<{ search: [query: TalentQueryReqDTO] }>()
const form = reactive<TalentQueryReqDTO>({ ...props.query })
const departments = ref<DepartmentVO[]>([])
const positions = ref<PositionVO[]>([])
const isOptionsReady = ref(false)
const isOptionsLoading = ref(false)
// Browser back/forward restores the applied query and the visible form together.
watch(
  () => props.query,
  (value) =>
    Object.assign(
      form,
      defaultTalentQuery(),
      { departmentId: undefined, positionId: undefined, typeCode: undefined },
      value,
    ),
  { deep: true },
)
const getOptions = async () => {
  if (isOptionsLoading.value) return
  isOptionsLoading.value = true
  try {
    const [d, p] = await Promise.all([getDepartments(), getPositions()])
    departments.value = d.data.data
    positions.value = p.data.data
    isOptionsReady.value = true
  } catch {
    isOptionsReady.value = false
  } finally {
    isOptionsLoading.value = false
  }
}
const handleSearch = () =>
  emit('search', {
    ...form,
    pageNum: 1,
    departmentId: form.departmentId ?? undefined,
    positionId: form.positionId ?? undefined,
    typeCode: form.typeCode || undefined,
  })
const handleReset = () => {
  Object.assign(form, defaultTalentQuery(), {
    departmentId: undefined,
    positionId: undefined,
    typeCode: undefined,
  })
  handleSearch()
}
onMounted(getOptions)
</script>

<template>
  <section>
    <el-alert
      v-if="!isOptionsReady"
      title="组织选项加载中或加载失败，可重试；当前统计不会使用残缺选项替换筛选。"
      type="warning"
      :closable="false"
    >
      <el-button
        :loading="isOptionsLoading"
        text
        @click="getOptions"
      >
        重新加载选项
      </el-button>
    </el-alert>
    <el-form
      :model="form"
      label-position="top"
      @submit.prevent="handleSearch"
      class="filter-form"
    >
      <el-form-item label="部门">
        <el-select
          v-model="form.departmentId"
          :disabled="!isOptionsReady"
          placeholder="全部部门"
          clearable
        >
          <el-option
            label="未分配部门"
            :value="0"
          />
          <el-option
            v-for="item in departments"
            :key="item.id"
            :label="item.departmentName + (item.isEnabled ? '' : '（已停用）')"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="岗位">
        <el-select
          v-model="form.positionId"
          :disabled="!isOptionsReady"
          placeholder="全部岗位"
          clearable
        >
          <el-option
            label="未分配岗位"
            :value="0"
          />
          <el-option
            v-for="item in positions"
            :key="item.id"
            :label="item.positionName + (item.isEnabled ? '' : '（已停用）')"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="在职状态">
        <el-select v-model="form.employmentStatus">
          <el-option
            label="在职"
            value="ACTIVE"
          />
          <el-option
            label="离职"
            value="RESIGNED"
          />
          <el-option
            label="全部在职状态"
            value="ALL"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="测评状态">
        <el-select v-model="form.assessmentStatus">
          <el-option
            label="全部测评状态"
            value="ALL"
          />
          <el-option
            label="已完成"
            value="COMPLETED"
          />
          <el-option
            label="未完成"
            value="NOT_COMPLETED"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="最新偏好类型">
        <el-select
          v-model="form.typeCode"
          placeholder="全部类型"
          clearable
        >
          <el-option
            v-for="type in PERSONALITY_TYPES"
            :key="type"
            :label="type"
            :value="type"
          />
        </el-select>
      </el-form-item>
      <div class="filter-actions">
        <el-button
          :loading="isLoading"
          type="primary"
          native-type="submit"
        >
          查询
        </el-button>
        <el-button
          :disabled="isLoading"
          @click="handleReset"
        >
          重置
        </el-button>
      </div>
    </el-form>
    <details class="scope-hint">
      <summary>筛选范围说明</summary>
      <p>
        固定范围：审核通过人员；默认在职。部门、岗位和最新类型均按当前归属筛选，停用账号仍计入人员档案。选择“已完成”或某个类型后，完成率按该筛选范围计算。
      </p>
    </details>
  </section>
</template>

<style scoped lang="scss">
.filter-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 16px;
}
.filter-actions {
  display: flex;
  align-items: end;
  margin-bottom: 0;
  align-self: end;
}
.scope-hint {
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.8;
}
@media (min-width: 1200px) {
  .filter-form {
    grid-template-columns: repeat(5, minmax(0, 1fr)) auto;
    gap: 12px;
    align-items: end;
  }
  .filter-form :deep(.el-form-item) {
    margin-bottom: 0;
  }
  .scope-hint {
    margin-top: 8px;
  }
}
@media (max-width: 800px) {
  .filter-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 480px) {
  .filter-form {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
