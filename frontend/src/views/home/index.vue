<script setup lang="ts">
// 公开介绍与业务入口；内部功能继续由路由守卫和后端权限校验保护。
import { computed, ref } from 'vue'
import PersonalityVisual from '@/components/PersonalityVisual.vue'
import BenefitReveal from '@/components/BenefitReveal.vue'
import { getHealth } from '@/api/system'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const healthStatus = ref('尚未检查服务连接')
const isChecking = ref(false)
const isSignedIn = computed(() => userStore.isLoggedIn && !!userStore.user)
const isAdmin = computed(() => isSignedIn.value && userStore.user?.roleCode === 'ADMIN')
const isManager = computed(
  () => isSignedIn.value && userStore.user?.roleCode === 'DEPARTMENT_MANAGER',
)
const features = [
  {
    title: '人格偏好测评与报告',
    number: '01',
    description: '了解 E/I、S/N、T/F、J/P 四维偏好，查看个人解释、成长建议与独立保存的历次报告。',
    note: '答题进度在保存成功后可继续；不同量表版本不直接比较。',
    links: [
      { label: '进入人格偏好测评', path: '/assessment' },
      { label: '查看历次报告', path: '/assessment/history' },
    ],
  },
  {
    title: '可追溯的知识关系图',
    number: '02',
    description: '从人格类型、偏好倾向到工作方式与行动建议，搜索节点、展开关系，并查看关联依据。',
    note: '图谱展示关联与解释路径；项目演示关系须结合来源和证据等级阅读。',
    links: [{ label: '探索人格知识关系图', path: '/knowledge-graph' }],
  },
  {
    title: '日常考勤与请假',
    number: '03',
    description:
      '使用服务器时间记录上下班打卡，提交请假、查看审批结果，在每日状态中了解已批请假与打卡情况。',
    note: 'Asia/Shanghai · 周末双休；每日缺卡参考状态保留原始打卡时间。',
    links: [
      { label: '前往打卡工作台', path: '/workbench' },
      { label: '进入请假与审批', path: '/leave' },
    ],
  },
]
const steps = [
  {
    title: '注册企业账号',
    description: '填写真实姓名和账号信息，提交后等待管理员审核。',
    link: '/register',
    label: '注册账号',
  },
  {
    title: '查询审核并登录',
    description: '凭注册账号密码查询审核结果；被拒绝时可查看原因，通过后再登录内部功能。',
    link: '/review-status',
    label: '查询注册审核状态',
  },
  {
    title: '阅读说明并作答',
    description: '确认所选量表的来源、数据用途和可见范围后开始。离开前留意“进度已保存”状态。',
    link: '/assessment',
    label: '前往测评说明',
  },
  {
    title: '查看报告与建议',
    description: '提交后保留本次结果，结合图谱依据阅读建议；再次测评会生成独立历史记录。',
    link: '/assessment/history',
    label: '前往历史报告',
  },
]
const roles = [
  {
    title: '普通员工',
    description: '参与本人测评，查看个人报告和成长建议，记录考勤、申请请假并查看结果。',
    scope: '本人数据',
  },
  {
    title: '部门负责人',
    description:
      '具备本人功能，并审批当前分配范围内的请假；负责人本人请假由配置的另一名负责人处理。',
    scope: '本人数据与分配的请假审批',
  },
  {
    title: '管理员',
    description:
      '维护账号审核、人事组织、量表与图谱版本、考勤规则和整体看板，可查看员工完整测评报告。',
    scope: '管理职责范围内的系统数据',
  },
]
const questions = [
  {
    title: '为什么注册后还不能测评、打卡或请假？',
    answer:
      '内部功能需要账号审核通过且处于启用状态。请先查询审核结果；审核通过仍无法使用时，请联系企业管理员核实账号及组织配置。请假还需要有效部门和审批人。',
  },
  {
    title: '答题过程中退出，下次能继续吗？',
    answer:
      '可以。答案保存到服务器后可再次登录继续。请以页面的保存成功状态为准；网络错误时先重试保存，存在未保存内容时不要直接关闭页面。提交后的本次答卷不能修改，可另开一次测评。',
  },
  {
    title: '测评题目来自哪里？',
    answer:
      '项目已接入 OEJTS 1.2 的 OpenJung 中文适配参考题库，题目及中文适配采用 CC BY-NC-SA 4.0，仅用于非商业毕设参考，保留原作者署名；中文版本未经独立信效度验证。系统也保留16题原创演示量表。实际开放内容以测评开始页所选版本为准。',
  },
  {
    title: '报告和图谱结论如何理解？',
    answer:
      '报告由后端按本次量表评分规则计算四维偏好，再结合图谱关系生成解释与建议。结果反映偏好倾向，图谱关系不表示确定因果；演示假设不能作为已验证的科学结论。历史变化也不代表能力提升或退步。',
  },
  {
    title: '谁能看到我的结果，数据会保存多久？',
    answer:
      '本人和管理员可查看个人完整报告。部门负责人不因审批身份获得其他员工的报告权限。保存期限及联系渠道随所选量表在开始页展示；当前毕设参考版本约定答辩结束后由项目维护者清理。正式使用前应核实实际保存期限与联系方式。',
  },
]
const handleCheckHealth = async () => {
  if (isChecking.value) return
  isChecking.value = true
  healthStatus.value = '正在检查服务连接…'
  try {
    const response = await getHealth()
    healthStatus.value =
      response.data.data.status === 'UP' ? '服务连接正常' : '服务状态异常，请稍后重试'
  } catch {
    healthStatus.value = '连接失败，请稍后重试或联系项目维护者'
  } finally {
    isChecking.value = false
  }
}
</script>

<template>
  <main
    id="home-top"
    class="home-page"
  >
    <a
      class="skip-link"
      href="#home-features"
    >
      跳至功能介绍
    </a>
    <header class="home-header">
      <a
        href="#home-top"
        class="brand"
      >
        <span
          class="brand-mark"
          aria-hidden="true"
        >
          p.
        </span>
        personnel
        <span>企业人才系统</span>
      </a>
      <nav aria-label="首页导航">
        <a href="#home-features">系统功能</a>
        <a href="#home-process">使用流程</a>
        <a href="#home-assessment">测评说明</a>
        <a href="#home-questions">常见问题</a>
      </nav>
      <RouterLink
        :to="isSignedIn ? '/workbench' : '/login'"
        class="header-entry"
      >
        {{ isSignedIn ? '个人工作台' : '登录系统' }}
      </RouterLink>
    </header>

    <section
      class="hero-card"
      aria-labelledby="hero-title"
    >
      <div class="hero-copy">
        <h1 id="hero-title">
          理解性格的不同，
          <br />
          看见协作的可能
          <span class="title-stop">。</span>
        </h1>
        <p class="description">
          不止于一个人格标签。从四维偏好中认识自己，在知识关系中探索方向，让个人成长与团队协作更有依据。
        </p>
        <p class="assessment-label">非官方 MBTI 测评 · 结果用于自我了解与沟通反思</p>
        <div
          v-if="isSignedIn"
          class="action-links"
        >
          <RouterLink
            to="/workbench"
            class="primary-link"
          >
            进入个人工作台
          </RouterLink>
          <RouterLink
            to="/assessment"
            class="secondary-link"
          >
            开始或继续测评
          </RouterLink>
          <RouterLink
            v-if="isAdmin"
            to="/admin"
            class="secondary-link"
          >
            进入管理后台
          </RouterLink>
          <RouterLink
            v-if="isManager"
            to="/leave"
            class="secondary-link"
          >
            请假与审批
          </RouterLink>
        </div>
        <div
          v-else
          class="action-links"
        >
          <RouterLink
            to="/register"
            class="primary-link"
          >
            注册账号，开启探索
          </RouterLink>
          <RouterLink
            to="/login"
            class="secondary-link"
          >
            已有账号，登录
          </RouterLink>
          <RouterLink
            to="/review-status"
            class="text-link"
          >
            查询注册审核状态
          </RouterLink>
        </div>
        <p
          v-if="isSignedIn"
          class="session-note"
        >
          当前账号 {{ userStore.user?.realName }}，内部功能以服务器校验的账号状态与权限为准。
        </p>
        <p
          v-else
          class="session-note"
        >
          新账号需由管理员审核通过；首页说明可直接阅读。
        </p>
      </div>
      <PersonalityVisual />
      <div class="hero-footnote">
        <span>理解自己，更好地协作</span>
        <span>人格偏好 / 成长探索 / 日常协作</span>
      </div>
    </section>

    <div class="principle-strip">
      <span>为每一种不同，留出空间。</span>
      <span>
        <strong>04</strong>
        个偏好维度
      </span>
      <span>
        <strong>16</strong>
        种人格视角
      </span>
      <span>
        <strong>01</strong>
        个独特的你
      </span>
    </div>

    <section
      id="home-features"
      tabindex="-1"
      class="content-section"
      aria-labelledby="features-title"
    >
      <header class="section-heading">
        <p class="eyebrow">系统功能</p>
        <h2 id="features-title">从个人了解，到日常协作</h2>
        <p>以下内部功能需登录并通过审核后使用。</p>
      </header>
      <div class="feature-grid">
        <article
          v-for="feature in features"
          :key="feature.number"
          class="info-card"
        >
          <span class="card-number">{{ feature.number }}</span>
          <h3>{{ feature.title }}</h3>
          <p>{{ feature.description }}</p>
          <p class="card-note">{{ feature.note }}</p>
          <div class="card-links">
            <RouterLink
              v-for="link in feature.links"
              :key="link.path"
              :to="link.path"
            >
              {{ link.label }}
            </RouterLink>
          </div>
        </article>
      </div>
    </section>

    <section
      id="home-process"
      class="content-section panel"
      aria-labelledby="process-title"
    >
      <header class="section-heading">
        <p class="eyebrow">使用流程</p>
        <h2 id="process-title">第一次使用，从这里开始</h2>
      </header>
      <ol class="process-list">
        <li
          v-for="(step, index) in steps"
          :key="step.title"
        >
          <span
            class="step-number"
            aria-hidden="true"
          >
            {{ index + 1 }}
          </span>
          <div>
            <h3>{{ step.title }}</h3>
            <p>{{ step.description }}</p>
            <RouterLink :to="step.link">
              {{ step.label }}
            </RouterLink>
          </div>
        </li>
      </ol>
    </section>

    <BenefitReveal />

    <section
      class="content-section"
      aria-labelledby="roles-title"
    >
      <header class="section-heading">
        <p class="eyebrow">角色与权限</p>
        <h2 id="roles-title">不同职责，清晰的数据范围</h2>
      </header>
      <div class="role-grid">
        <article
          v-for="role in roles"
          :key="role.title"
          class="info-card"
        >
          <p class="role-scope">{{ role.scope }}</p>
          <h3>{{ role.title }}</h3>
          <p>{{ role.description }}</p>
        </article>
      </div>
      <p class="section-note">
        部门负责人仅处理分配给自己的请假审批，不默认获得其他员工的人格报告或人才分析权限。
      </p>
    </section>

    <section
      id="home-assessment"
      class="content-section panel"
      aria-labelledby="assessment-title"
    >
      <header class="section-heading">
        <p class="eyebrow">测评说明</p>
        <h2 id="assessment-title">了解结果之前，先了解它的边界</h2>
      </header>
      <div class="assessment-grid">
        <article>
          <h3>偏好刻度与历史记录</h3>
          <p>
            展示 E/I、S/N、T/F、J/P
            四维偏好及16型结果。每次提交保存当次量表、评分版本和报告快照；历史对比只用于查看偏好变化，不同量表版本不可直接比较。
          </p>
        </article>
        <article>
          <h3>题库与依据可追溯</h3>
          <p>
            当前毕设参考题库来自 OEJTS 的 OpenJung 中文适配，采用 CC BY-NC-SA
            4.0；中文版本未经独立验证。图谱建议保留关联路径、来源与证据等级，便于结合情境反思。
          </p>
          <a
            href="https://github.com/openjung/core/tree/4d7917df1857e2dc04adc47fab3ecc0c8db10612"
            target="_blank"
            rel="noopener noreferrer"
          >
            查看参考题库来源（新窗口）
          </a>
          <a
            href="https://creativecommons.org/licenses/by-nc-sa/4.0/"
            target="_blank"
            rel="noopener noreferrer"
          >
            查看许可说明（新窗口）
          </a>
        </article>
      </div>
      <div class="usage-note">
        <h3>结果的适用范围</h3>
        <p>
          结果不代表能力、绩效、心理诊断或发展潜力，不用于招聘淘汰、晋升降级、绩效处罚或员工标签化管理。请在测评开始前阅读所选版本的数据用途、保存期限及联系渠道，再决定是否参与。
        </p>
      </div>
      <RouterLink
        to="/assessment"
        class="text-link"
      >
        阅读所选量表说明并参与测评
      </RouterLink>
    </section>

    <section
      id="home-questions"
      class="content-section panel"
      aria-labelledby="questions-title"
    >
      <header class="section-heading">
        <p class="eyebrow">常见问题</p>
        <h2 id="questions-title">使用前，你可能想了解</h2>
      </header>
      <details
        v-for="question in questions"
        :key="question.title"
        class="question-item"
      >
        <summary>{{ question.title }}</summary>
        <p>{{ question.answer }}</p>
      </details>
    </section>

    <footer class="home-footer">
      <div>
        <p>企业员工 MBTI 人格测评与人才分析系统</p>
        <p class="footer-note">非商业毕业设计展示 · 以所选量表和已发布内容版本为准</p>
      </div>
      <div class="service-check">
        <el-button
          :loading="isChecking"
          @click="handleCheckHealth"
        >
          检查系统连接
        </el-button>
        <p
          role="status"
          aria-live="polite"
        >
          {{ healthStatus }}
        </p>
      </div>
    </footer>
  </main>
</template>

<style scoped lang="scss">
// 石墨灰 + 焦橙（统一到 design-tokens），克制圆角与中性阴影。
.home-page {
  position: relative;
  max-width: none;
  min-height: 100dvh;
  padding: 0 max(5vw, calc((100vw - 1260px) / 2)) 24px;
  background: var(--bg-page);
  color: var(--text-regular);
}
.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 20px;
  min-height: 100px;
  padding: 25px 0;
  border-bottom: 1px solid var(--border-light);
}
a {
  color: var(--brand-600);
  text-underline-offset: 3px;
  transition:
    color 0.2s,
    background 0.2s,
    transform 0.2s;
}
a:focus-visible,
summary:focus-visible {
  outline: 2px solid var(--brand-500);
  outline-offset: 3px;
  border-radius: 4px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -1px;
  color: var(--text-strong);
  text-decoration: none;
}
.brand .brand-mark {
  display: grid;
  place-items: center;
  width: 35px;
  height: 35px;
  background: var(--brand-500);
  color: #fff;
  font-size: 29px;
  line-height: 1;
  border-radius: 8px;
  margin: 0;
  padding-bottom: 5px;
}
.brand span:last-child {
  margin: 0 0 0 6px;
  border-left: 1px solid var(--border-default);
  padding-left: 15px;
  font-size: 10px;
  letter-spacing: 1px;
  font-weight: 400;
  color: var(--text-secondary);
}
nav {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
}
nav a {
  font-size: 12px;
  color: var(--text-secondary);
  text-decoration: none;
}
nav a:hover {
  color: var(--text-strong);
}
.header-entry {
  border: 1px solid var(--border-default);
  border-radius: 8px;
  padding: 10px 18px;
  text-decoration: none;
  font-size: 12px;
  color: var(--text-strong);
}
.header-entry:hover {
  border-color: var(--border-strong);
  background: var(--bg-hover);
}
.skip-link {
  position: absolute;
  top: 0;
  left: 16px;
  padding: 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: 8px;
  transform: translateY(-150%);
  z-index: 10;
}
.skip-link:focus {
  transform: translateY(0);
}
.hero-card {
  display: grid;
  grid-template-columns: 1.08fr 1fr;
  align-items: center;
  gap: 35px;
  padding: 68px 0 0;
}
.hero-copy {
  padding-bottom: 15px;
}
.eyebrow {
  color: var(--text-disabled);
  font-size: 12px;
  letter-spacing: 0;
  font-weight: 500;
  margin: 0 0 12px;
}
h1,
h2,
h3 {
  color: var(--text-strong);
}
h1 {
  font-size: clamp(33px, 3.65vw, 51px);
  font-weight: 500;
  line-height: 1.4;
  letter-spacing: -1px;
  margin: 24px 0;
}
.title-stop {
  color: var(--brand-500);
}
.description {
  max-width: 440px;
  font-size: 14px;
  line-height: 2;
  color: var(--text-secondary);
  margin: 0;
}
.assessment-label {
  font-size: 12px;
  color: var(--text-disabled);
  margin-top: 18px;
}
.action-links {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 26px;
}
.primary-link,
.secondary-link {
  display: inline-flex;
  justify-content: center;
  font-size: 12px;
  border-radius: 8px;
  padding: 13px 20px;
  font-weight: 500;
  text-decoration: none;
}
.primary-link {
  background: var(--text-strong);
  color: #fff;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 14%);
}
.primary-link:hover {
  background: #2e2e2e;
  color: #fff;
}
.secondary-link {
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  color: var(--text-strong);
}
.secondary-link:hover {
  border-color: var(--border-strong);
  background: var(--bg-hover);
}
.action-links .text-link {
  flex-basis: 100%;
  font-size: 12px;
  padding: 0;
  color: var(--brand-600);
}
.session-note {
  color: var(--text-disabled);
  font-size: 12px;
  margin-top: 13px;
}
.hero-footnote {
  grid-column: 1 / -1;
  display: flex;
  justify-content: space-between;
  gap: 15px;
  padding: 20px 0;
  border-bottom: 1px solid var(--border-light);
  font-size: 12px;
  color: var(--text-disabled);
}
.principle-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 25px 0;
  border-bottom: 1px solid var(--border-light);
  font-size: 12px;
  color: var(--text-disabled);
}
.principle-strip > span:first-child {
  font-size: 13px;
  color: var(--text-secondary);
}
.principle-strip strong {
  font-size: 25px;
  font-weight: 500;
  color: var(--brand-600);
  margin-right: 10px;
  font-variant-numeric: tabular-nums;
}
.content-section {
  margin-top: 80px;
  scroll-margin-top: 30px;
}
.section-heading {
  margin-bottom: 30px;
}
.section-heading .eyebrow {
  margin-bottom: 12px;
}
h2 {
  font-size: clamp(24px, 2.6vw, 33px);
  font-weight: 500;
  letter-spacing: -1px;
  margin: 0 0 12px;
}
.section-heading > p:last-child,
.section-note {
  font-size: 12px;
  color: var(--text-secondary);
  margin: 0;
}
h3 {
  font-size: 18px;
  font-weight: 500;
  margin: 12px 0;
}
.feature-grid {
  display: grid;
  grid-template-columns: 1.25fr 1fr;
  gap: 18px;
}
.feature-grid .info-card:first-child {
  grid-row: span 2;
  padding: 36px;
  background: var(--bg-subtle);
  justify-content: center;
}
.feature-grid .info-card:first-child h3 {
  font-size: 26px;
  margin-top: 35px;
}
.feature-grid .info-card:first-child .card-links {
  margin-top: 32px;
}
.info-card {
  border: 1px solid var(--border-default);
  border-radius: 12px;
  background: var(--bg-card);
  box-shadow: none;
  padding: 26px 30px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.info-card > p {
  font-size: 12px;
  color: var(--text-secondary);
}
.card-number {
  color: var(--brand-600);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
}
.card-note {
  color: var(--text-disabled);
  font-size: 11px !important;
}
.card-links {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: auto;
  padding-top: 12px;
  font-size: 12px;
}
.card-links a {
  text-decoration: none;
  color: var(--brand-600);
}
.card-links a:hover {
  color: var(--brand-500);
}
.panel {
  border: 0;
  border-top: 1px solid var(--border-default);
  border-radius: 0;
  background: none;
  box-shadow: none;
  padding: 45px 0 0;
}
.process-list {
  list-style: none;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 32px 50px;
  margin: 0;
  padding: 0;
}
.process-list li {
  display: flex;
  gap: 16px;
}
.process-list h3 {
  margin-top: 0;
}
.process-list p,
.process-list a {
  font-size: 12px;
}
.process-list a {
  color: var(--brand-600);
}
.step-number {
  flex: 0 0 32px;
  height: 32px;
  display: grid;
  place-items: center;
  background: var(--brand-100);
  color: var(--brand-600);
  border-radius: 6px;
  font-weight: 500;
  font-size: 13px;
}
.role-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
}
.role-grid .info-card {
  display: grid;
  grid-template-columns: 200px 150px 1fr;
  gap: 15px;
  align-items: baseline;
  border: 0;
  border-bottom: 1px solid var(--border-light);
  border-radius: 0;
  background: none;
  padding: 22px 0;
}
.role-grid .info-card p,
.role-grid .info-card h3 {
  margin: 0;
}
.role-scope {
  color: var(--brand-600);
  font-size: 12px;
}
.assessment-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 32px;
  color: var(--text-secondary);
  font-size: 12px;
}
.assessment-grid a {
  display: block;
  margin-bottom: 12px;
  color: var(--brand-600);
}
.usage-note {
  margin-top: 24px;
  padding: 20px;
  background: var(--bg-subtle);
  border-left: 2px solid var(--brand-500);
  border-radius: 0 8px 8px 0;
  font-size: 12px;
}
.usage-note h3 {
  margin-top: 0;
}
.usage-note p {
  margin-bottom: 0;
  color: var(--text-secondary);
}
.text-link {
  display: inline-block;
  padding: 10px 0;
  font-size: 14px;
  color: var(--brand-600);
}
.question-item {
  padding: 23px 0;
  border-bottom: 1px solid var(--border-light);
}
.question-item:last-child {
  border-bottom: 0;
}
.question-item summary {
  cursor: pointer;
  color: var(--text-strong);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.7;
}
.question-item p {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.7;
  max-width: 85ch;
  margin: 16px 0 0;
}
.home-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 24px;
  border-top: 1px solid var(--border-light);
  margin-top: 65px;
  padding-top: 30px;
  font-size: 11px;
  color: var(--text-secondary);
}
.footer-note,
.service-check p {
  color: var(--text-disabled);
  margin: 0;
}
.service-check :deep(.el-button) {
  background: var(--bg-card);
  border-color: var(--border-default);
  color: var(--text-strong);
  font-size: 11px;
}
@media (prefers-reduced-motion: no-preference) {
  .hero-copy,
  .hero-card > :deep(.personality-visual) {
    animation: page-enter 0.75s cubic-bezier(0.16, 1, 0.3, 1) both;
  }
  .hero-card > :deep(.personality-visual) {
    animation-delay: 0.12s;
  }
  @keyframes page-enter {
    from {
      opacity: 0;
      transform: translateY(18px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }
}
@media (max-width: 1000px) {
  .brand span:last-child {
    display: none;
  }
  nav {
    gap: 18px;
  }
  .hero-card {
    gap: 25px;
  }
  h1 {
    font-size: 36px;
  }
  .role-grid .info-card {
    grid-template-columns: 170px 120px 1fr;
  }
}
@media (max-width: 760px) {
  .home-page {
    padding-left: 22px;
    padding-right: 22px;
  }
  .home-header {
    flex-wrap: wrap;
    gap: 15px;
  }
  .home-header nav {
    order: 3;
    flex-basis: 100%;
    justify-content: space-between;
    gap: 10px;
  }
  .hero-card {
    grid-template-columns: 1fr;
    padding-top: 38px;
    gap: 25px;
  }
  .hero-copy {
    max-width: 550px;
  }
  h1 {
    font-size: 38px;
  }
  .hero-footnote {
    flex-direction: column;
    gap: 8px;
  }
  .principle-strip {
    flex-wrap: wrap;
    gap: 15px;
  }
  .principle-strip > span:first-child {
    flex-basis: 100%;
  }
  .principle-strip strong {
    font-size: 23px;
    margin-right: 6px;
  }
  .feature-grid {
    grid-template-columns: 1fr;
  }
  .feature-grid .info-card:first-child {
    grid-row: auto;
    padding: 28px;
  }
  .feature-grid .info-card:first-child h3 {
    margin-top: 15px;
    font-size: 23px;
  }
  .content-section {
    margin-top: 50px;
  }
  .role-grid .info-card {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .process-list,
  .assessment-grid {
    grid-template-columns: 1fr;
  }
  .action-links {
    flex-direction: row;
    align-items: center;
  }
  .action-links .text-link {
    text-align: left;
  }
  .home-footer {
    align-items: flex-start;
  }
}
</style>
