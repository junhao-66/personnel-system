<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getDepartments } from '@/api/organization'
import { getTeamAnalysis } from '@/api/analysis'
import type { DepartmentVO } from '@/types/organization'
import type { TeamAnalysisVO } from '@/types/analysis'
const departments = ref<DepartmentVO[]>([]),
  selected = ref(0),
  data = ref<TeamAnalysisVO>(),
  loading = ref(false),
  failed = ref(false)
const activeSection = ref('types')
const dimensionNames: Record<string, string> = {
  EI: '外向 E / 内向 I',
  SN: '实感 S / 直觉 N',
  TF: '思考 T / 情感 F',
  JP: '判断 J / 感知 P',
}
let sequence = 0
async function load() {
  const current = ++sequence
  loading.value = true
  failed.value = false
  data.value = undefined
  try {
    const [d, a] = await Promise.all([getDepartments(), getTeamAnalysis(selected.value)])
    if (current === sequence) {
      departments.value = d.data.data
      data.value = a.data.data
    }
  } catch {
    if (current === sequence) failed.value = true
  } finally {
    if (current === sequence) loading.value = false
  }
}
onMounted(load)
</script>
<template>
  <main
    class="page"
    v-loading="loading"
  >
    <h1>部门协作分析</h1>
    <div class="filters">
      <el-select
        v-model="selected"
        aria-label="分析部门"
        :disabled="loading"
        @change="load"
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
      <el-button
        :disabled="loading"
        @click="load"
      >
        刷新分析
      </el-button>
    </div>
    <el-alert
      v-if="failed"
      title="分析加载失败，请重试"
      type="error"
      :closable="false"
    />
    <template v-if="data">
      <h2>{{ data.departmentName }}</h2>
      <el-alert
        :title="data.notice"
        type="info"
        :closable="false"
      />
      <p>
        在职人数 {{ data.distribution.employeeCount }} · 已完成人数
        {{ data.distribution.completedCount }} · 未完成人数
        {{ data.distribution.notCompletedCount }} · 完成率
        {{
          data.distribution.completionRate === null ? '—' : `${data.distribution.completionRate}%`
        }}
      </p>
      <el-empty
        v-if="!data.distribution.completedCount"
        description="本部门暂无已完成测评"
      />
      <nav
        class="section-navigation"
        aria-label="部门分析内容"
      >
        <button
          type="button"
          :aria-pressed="activeSection === 'types'"
          @click="activeSection = 'types'"
        >
          人格分布
        </button>
        <button
          type="button"
          :aria-pressed="activeSection === 'dimensions'"
          @click="activeSection = 'dimensions'"
        >
          维度人数
        </button>
        <button
          type="button"
          :aria-pressed="activeSection === 'tips'"
          @click="activeSection = 'tips'"
        >
          协作建议
        </button>
      </nav>
      <section v-if="activeSection === 'types'">
        <h3>最新类型编码分布</h3>
        <div class="types">
          <el-card
            v-for="type in data.distribution.typeDistribution"
            :key="type.typeCode"
          >
            <strong>{{ type.typeCode }}</strong>
            <p>
              {{ type.employeeCount }} 人 · {{ type.percent === null ? '—' : `${type.percent}%` }}
            </p>
          </el-card>
        </div>
      </section>
      <section v-if="activeSection === 'dimensions'">
        <h3>四维端点人数</h3>
        <p>仅由类型编码拆分，不表示偏好强度，不跨版本平均原始分数。</p>
        <el-table :data="data.dimensions">
          <el-table-column label="维度">
            <template #default="scope">{{ dimensionNames[scope.row.dimension] }}</template>
          </el-table-column>
          <el-table-column
            prop="firstCount"
            label="前端点人数"
          />
          <el-table-column
            prop="secondCount"
            label="后端点人数"
          />
        </el-table>
      </section>
      <section v-if="activeSection === 'tips'">
        <h3>可自愿尝试的协作方式</h3>
        <p
          v-for="tip in data.collaborationTips"
          :key="tip"
        >
          {{ tip }}
        </p>
      </section>
      <small>规则版本 {{ data.ruleVersion }} · 生成时间 {{ data.distribution.generatedAt }}</small>
    </template>
  </main>
</template>
<style scoped lang="scss">
.page {
  max-width: 1160px;
  margin: auto;
  padding: 28px 4vw;
  overflow-wrap: anywhere;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 20px 0;
}
.el-select {
  width: 240px;
  max-width: 100%;
}
p {
  line-height: 1.7;
}
.types {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
@media (max-width: 600px) {
  .types {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
