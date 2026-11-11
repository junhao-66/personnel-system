<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getPendingUsers } from '@/api/user'
import { getReviewLeaves } from '@/api/leave'
import type { UserVO } from '@/types/user'
import type { LeaveVO } from '@/types/leave'
import AmbientScene from '@/components/AmbientScene.vue'

interface NavItem {
  label: string
  to: string
  icon: string
}
interface NavGroup {
  title: string
  items: NavItem[]
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const menuOpen = ref(false)
const searchKeyword = ref('')
const expandedGroup = ref(0)
const topNavigation = ref<HTMLElement>()
const capsuleStyle = ref<Record<string, string>>({ opacity: '0' })
const capsuleMotion = ref('')
const navigationDirection = ref('')
let navigationTimer = 0
let capsuleSettleTimer = 0
const topNav = computed(() =>
  isAdmin.value
    ? [
        adminNav.value[0].items[0],
        adminNav.value[0].items[1],
        adminNav.value[1].items[0],
        adminNav.value[1].items[2],
        adminNav.value[1].items[3],
        adminNav.value[2].items[0],
      ]
    : employeeNav.value.flatMap((group) => group.items).slice(0, 5),
)
const openGroup = (index: number) => {
  expandedGroup.value = index
  menuOpen.value = true
}

const isAdmin = computed(() => userStore.user?.roleCode === 'ADMIN')
const isManager = computed(() => userStore.user?.roleCode === 'DEPARTMENT_MANAGER')
const showTodo = computed(() => isAdmin.value || isManager.value)
const todoOpen = ref(false)
const pendingUsers = ref<UserVO[]>([])
const pendingLeaves = ref<LeaveVO[]>([])
const todoCount = computed(() => pendingUsers.value.length + pendingLeaves.value.length)
const roleTitle = computed(() =>
  isAdmin.value
    ? '系统管理员'
    : userStore.user?.roleCode === 'DEPARTMENT_MANAGER'
      ? '部门负责人'
      : '企业员工',
)

// 图标集：统一 24x24，stroke-width 1.5，线条端点圆角。
const icons: Record<string, string> = {
  dashboard: 'M3 3h7v9H3zM14 3h7v5h-7zM14 12h7v9h-7zM3 16h7v5H3z',
  people:
    'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  organization: 'M9 3h6v6H9zM3 15h6v6H3zM15 15h6v6h-6zM12 9v3m-3 3h6v-3',
  'user-role': 'M12 3l7 3v6c0 5-3 8-7 9-4-1-7-4-7-9V6zM9 12l2 2 4-4',
  review: 'M9 5H5v16h14V5h-4m-6 0V3h6v2M9 5h6M9 12l2 2 4-4',
  attendance: 'M5 4h14a1 1 0 0 1 1 1v15H4V5a1 1 0 0 1 1-1zM8 2v4m8-4v4M4 9h16',
  leave: 'M22 2L11 13M22 2l-7 20-4-9-9-4z',
  chart: 'M4 20V10m6 10V4m6 16v-6m4 6H4',
  scale: 'M7 3h8l4 4v14H7zM14 3v5h5M9 13h6m-6 4h4',
  job: 'M4 7h16v13H4zM9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2',
  template: 'M12 2l9 5-9 5-9-5zM3 12l9 5 9-5M3 17l9 5 9-5',
  network: 'M6 6V4m12 2V4M6 20v-2m12 2v-2M6 8h12v8H6zM9 12h6',
  ai: 'M12 3l1.8 4.2L18 9l-4.2 1.8L12 15l-1.8-4.2L6 9l4.2-1.8zM19 15l.9 2.1L22 18l-2.1.9L19 21l-.9-2.1L16 18l2.1-.9z',
  graph:
    'M6 3v12m0 6a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM18 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm0 0a9 9 0 0 1-9 9',
  settings:
    'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1z',
  audit: 'M3 12a9 9 0 1 0 3-6.7L3 8m0-5v5h5M12 7v5l4 2',
  cache:
    'M12 3c4 0 7 1.3 7 3s-3 3-7 3-7-1.3-7-3 3-3 7-3zm7 3v6c0 1.7-3 3-7 3s-7-1.3-7-3V6m14 6c0 1.7-3 3-7 3s-7-1.3-7-3',
  home: 'M3 10.5L12 3l9 7.5M5 9.5V21h14V9.5M10 21v-6h4v6',
  account: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8',
  assessment: 'M9 5H5v16h14V5h-4m-6 0V3h6v2M12 8a3 3 0 1 0 0 6 3 3 0 0 0 0-6z',
  history: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2',
  mbti: 'M12 2l8.7 5v10L12 22l-8.7-5V7z',
  search: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM21 21l-4.35-4.35',
  logout: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9',
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0',
}

const adminNav = computed<NavGroup[]>(() => [
  {
    title: '工作空间',
    items: [
      { label: '数据总览', to: '/admin/dashboard', icon: 'dashboard' },
      { label: '员工档案', to: '/admin/employees', icon: 'people' },
      { label: '部门岗位', to: '/admin/organization', icon: 'organization' },
      { label: '用户角色', to: '/admin/users', icon: 'user-role' },
      { label: '注册审核', to: '/admin/review', icon: 'review' },
    ],
  },
  {
    title: '日常业务',
    items: [
      { label: '考勤记录', to: '/admin/attendance', icon: 'attendance' },
      { label: '请假审批', to: '/leave', icon: 'leave' },
      { label: '测评统计', to: '/admin/talent-dashboard', icon: 'chart' },
      { label: 'MBTI偏好', to: '/mbti', icon: 'mbti' },
    ],
  },
  {
    title: '管理工具',
    items: [{ label: '管理配置', to: '/admin/management', icon: 'settings' }],
  },
])

const configurationPaths = [
  '/admin/assessment-scales',
  '/admin/job-preferences',
  '/admin/growth-templates',
  '/admin/team-analysis',
  '/admin/ai-assistant',
  '/admin/knowledge-graph',
  '/admin/system-settings',
  '/admin/operation-audits',
  '/admin/static-cache',
]
const isConfiguration = computed(() =>
  configurationPaths.some((path) => route.path.startsWith(path)),
)

const employeeNav = computed<NavGroup[]>(() => [
  {
    title: '工作台',
    items: [
      { label: '个人工作台', to: '/workbench', icon: 'home' },
      { label: '请假与审批', to: '/leave', icon: 'leave' },
      { label: '账号中心', to: '/account-center', icon: 'account' },
    ],
  },
  {
    title: '个人发展',
    items: [
      { label: '人格测评', to: '/assessment', icon: 'assessment' },
      { label: '历次报告', to: '/assessment/history', icon: 'history' },
      { label: 'MBTI人格偏好', to: '/mbti', icon: 'mbti' },
      { label: '岗位偏好探索', to: '/job-preferences', icon: 'job' },
      { label: '人格知识图谱', to: '/knowledge-graph', icon: 'graph' },
    ],
  },
])

const navGroups = computed(() => (isAdmin.value ? adminNav.value : employeeNav.value))

const isActive = (item: NavItem) => {
  if (item.to === '/admin/management' && isConfiguration.value) return true
  if (item.to === '/admin/employees' && route.path.startsWith('/admin/employees/')) return true
  if (item.to === '/admin/attendance' && route.path.endsWith('/attendance/rules')) return true
  if (item.to === '/assessment' && route.path === '/assessment') return true
  return route.path === item.to || route.path.startsWith(item.to + '/')
}

const activeTopNavIndex = () => topNav.value.findIndex((item) => isActive(item))

const syncTopCapsule = (index = activeTopNavIndex()) => {
  const navigation = topNavigation.value
  const links = navigation?.querySelectorAll<HTMLElement>('a[data-top-nav]')
  const link = index >= 0 ? links?.[index] : undefined
  if (!navigation || !link) {
    capsuleStyle.value = { opacity: '0' }
    return
  }
  const navigationRect = navigation.getBoundingClientRect()
  const linkRect = link.getBoundingClientRect()
  capsuleStyle.value = {
    opacity: '1',
    transform: `translate3d(${linkRect.left - navigationRect.left}px, 0, 0)`,
  }
}

const startTopNavigation = (targetIndex: number) => {
  const currentIndex = activeTopNavIndex()
  if (targetIndex === currentIndex) return
  const direction = targetIndex > currentIndex ? 'forward' : 'backward'
  capsuleMotion.value = `is-moving is-${direction}`
  navigationDirection.value = `nav-${direction}`
  syncTopCapsule(targetIndex)
  window.clearTimeout(navigationTimer)
  window.clearTimeout(capsuleSettleTimer)
  navigationTimer = window.setTimeout(() => {
    capsuleMotion.value = 'is-settling'
    navigationDirection.value = 'nav-idle'
    capsuleSettleTimer = window.setTimeout(() => (capsuleMotion.value = ''), 140)
  }, 400)
}

const handleSearch = () => {
  const keyword = searchKeyword.value.trim()
  if (!keyword) return
  searchKeyword.value = ''
  menuOpen.value = false
  void router.push({ path: '/admin/employees', query: { keyword } })
}

const handleLogout = async () => {
  userStore.logout()
  await router.replace('/login')
}

const loadTodos = async () => {
  if (isAdmin.value) {
    try {
      pendingUsers.value = (await getPendingUsers()).data.data
    } catch {
      pendingUsers.value = []
    }
  }
  if (isManager.value) {
    try {
      const res = await getReviewLeaves({ pageNum: 1, pageSize: 5, status: 'PENDING' })
      pendingLeaves.value = res.data.data.records
    } catch {
      pendingLeaves.value = []
    }
  }
}

const fmtDateTime = (s?: string) => (s ? s.replace('T', ' ').slice(0, 16) : '')

const handleNavigationResize = () => syncTopCapsule()

onMounted(() => {
  void loadTodos()
  void nextTick(() => syncTopCapsule())
  window.addEventListener('resize', handleNavigationResize, { passive: true })
})

onBeforeUnmount(() => {
  window.clearTimeout(navigationTimer)
  window.clearTimeout(capsuleSettleTimer)
  window.removeEventListener('resize', handleNavigationResize)
})

watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false
    void nextTick(() => syncTopCapsule())
  },
)

watch(
  () => topNav.value.length,
  () => void nextTick(() => syncTopCapsule()),
  { immediate: true },
)
</script>

<template>
  <div
    class="hr-shell"
    :class="[{ 'menu-open': menuOpen }, navigationDirection]"
    @keydown.esc="menuOpen = false"
  >
    <AmbientScene />
    <a
      class="skip-link"
      href="#admin-content"
    >
      跳至主要内容
    </a>

    <!-- 画中画主卡片 -->
    <div class="app-frame">
      <aside
        class="icon-rail"
        aria-label="快捷导航"
      >
        <button
          class="rail-toggle"
          aria-label="打开导航菜单"
          :aria-expanded="menuOpen"
          aria-controls="admin-navigation"
          @click="menuOpen = !menuOpen"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <path d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>
        <RouterLink
          :to="isAdmin ? '/admin/dashboard' : '/workbench'"
          title="工作总览"
          :class="{ active: route.path === '/admin/dashboard' || route.path === '/workbench' }"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <path :d="icons.dashboard" />
          </svg>
          <span>总览</span>
        </RouterLink>
        <button
          v-for="(group, index) in navGroups"
          :key="group.title"
          :title="group.title"
          :class="{ active: menuOpen && expandedGroup === index }"
          @click="openGroup(index)"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <path :d="icons[group.items[0].icon]" />
          </svg>
          <span>{{ isAdmin ? ['人员', '业务', '配置'][index] : group.title.slice(0, 2) }}</span>
        </button>
        <RouterLink
          to="/mbti"
          title="MBTI人格偏好"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <path :d="icons.mbti" />
          </svg>
          <span>MBTI</span>
        </RouterLink>
        <div class="rail-bottom">
          <RouterLink
            to="/account-center"
            title="账号中心"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
            >
              <path :d="icons.account" />
            </svg>
          </RouterLink>
          <button
            title="退出登录"
            aria-label="退出登录"
            @click="handleLogout"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
            >
              <path :d="icons.logout" />
            </svg>
          </button>
        </div>
      </aside>
      <Transition name="menu-reveal">
        <aside
          v-if="menuOpen"
          id="admin-navigation"
          class="expanded-menu"
        >
          <header>
            <strong>
              人事系统
              <span>· 功能导航</span>
            </strong>
            <button
              aria-label="关闭导航菜单"
              @click="menuOpen = false"
            >
              ×
            </button>
          </header>
          <nav
            :aria-label="isAdmin ? '后台导航' : '工作空间导航'"
            class="menu-columns"
          >
            <div class="menu-tree">
              <section
                v-for="(group, index) in navGroups"
                :key="group.title"
              >
                <button
                  class="group-button"
                  :class="{ 'is-selected': expandedGroup === index }"
                  @click="expandedGroup = index"
                >
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.5"
                  >
                    <path :d="icons[group.items[0].icon]" />
                  </svg>
                  {{ group.title }}
                  <span>{{ expandedGroup === index ? '−' : '+' }}</span>
                </button>
                <Transition name="submenu-reveal">
                  <div
                    v-if="expandedGroup === index"
                    class="tree-children"
                  >
                    <RouterLink
                      v-for="item in group.items"
                      :key="item.to"
                      :to="item.to"
                      class="nav-item"
                      :class="{ 'is-active': isActive(item) }"
                    >
                      <svg
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="1.5"
                      >
                        <path :d="icons[item.icon]" />
                      </svg>
                      {{ item.label }}
                    </RouterLink>
                  </div>
                </Transition>
              </section>
            </div>
            <div class="menu-related">
              <h2>{{ isAdmin ? '管理入口' : '个人空间' }}</h2>
              <RouterLink
                v-for="item in isAdmin
                  ? [
                      { label: '部门与岗位', to: '/admin/organization', icon: 'organization' },
                      { label: '用户与角色', to: '/admin/users', icon: 'user-role' },
                      { label: '注册审核', to: '/admin/review', icon: 'review' },
                      { label: '系统配置', to: '/admin/management', icon: 'settings' },
                    ]
                  : employeeNav[1].items"
                :key="item.to"
                :to="item.to"
                class="nav-item"
                :class="{ 'is-active': isActive(item) }"
              >
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.5"
                >
                  <path :d="icons[item.icon]" />
                </svg>
                {{ item.label }}
              </RouterLink>
              <div class="menu-account">
                <span class="account-avatar">
                  {{ (userStore.user?.realName || '用').slice(0, 1) }}
                </span>
                <div>
                  {{ userStore.user?.realName || '企业用户' }}
                  <small>{{ roleTitle }}</small>
                </div>
              </div>
            </div>
          </nav>
        </aside>
      </Transition>

      <!-- 右侧主列 -->
      <div class="app-main">
        <!-- 顶部栏 -->
        <header class="topbar">
          <button
            class="topbar-menu"
            :aria-label="menuOpen ? '关闭导航菜单' : '打开导航菜单'"
            aria-controls="admin-navigation"
            :aria-expanded="menuOpen"
            @click="menuOpen = !menuOpen"
          >
            <svg
              viewBox="0 0 24 24"
              aria-hidden="true"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path
                v-if="!menuOpen"
                d="M3 6h18M3 12h18M3 18h18"
              />
              <path
                v-else
                d="M6 6l12 12M18 6L6 18"
              />
            </svg>
          </button>

          <RouterLink
            class="topbar-brand"
            :to="isAdmin ? '/admin/dashboard' : '/workbench'"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
            >
              <path :d="icons.people" />
            </svg>
            <span>人事系统</span>
          </RouterLink>
          <nav
            ref="topNavigation"
            class="top-navigation"
            aria-label="主导航"
          >
            <span
              class="top-nav-capsule"
              :class="capsuleMotion"
              :style="capsuleStyle"
              aria-hidden="true"
            />
            <RouterLink
              v-for="(item, index) in topNav"
              :key="item.to"
              :to="item.to"
              :class="{ 'is-active': isActive(item) }"
              data-top-nav
              @click="startTopNavigation(index)"
            >
              <span>{{ item.label }}</span>
            </RouterLink>
          </nav>

          <div class="topbar-actions">
            <form
              v-if="isAdmin"
              class="topbar-search"
              role="search"
              @submit.prevent="handleSearch"
            >
              <svg
                viewBox="0 0 24 24"
                aria-hidden="true"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path :d="icons.search" />
              </svg>
              <input
                v-model="searchKeyword"
                type="search"
                placeholder="搜索员工"
                aria-label="搜索员工"
              />
            </form>

            <!-- 待办中心 -->
            <el-popover
              v-if="showTodo"
              v-model:visible="todoOpen"
              placement="bottom-end"
              :width="360"
              :show-arrow="false"
              :offset="8"
              trigger="click"
              popper-class="todo-popover"
              @show="loadTodos"
            >
              <template #reference>
                <button
                  class="todo-trigger"
                  type="button"
                  aria-label="待办事项"
                  title="待办事项"
                >
                  <svg
                    viewBox="0 0 24 24"
                    aria-hidden="true"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path :d="icons.bell" />
                  </svg>
                  <span
                    v-if="todoCount"
                    class="todo-badge"
                  >
                    {{ todoCount > 99 ? '99+' : todoCount }}
                  </span>
                </button>
              </template>

              <div class="todo-panel">
                <header class="todo-head">
                  <strong>待办事项</strong>
                  <span
                    v-if="todoCount"
                    class="todo-head-count"
                  >
                    {{ todoCount }} 项待处理
                  </span>
                </header>

                <!-- 待审核注册 -->
                <section
                  v-if="isAdmin"
                  class="todo-group"
                >
                  <div class="todo-group-title">
                    <span>注册审核</span>
                    <el-tag
                      v-if="pendingUsers.length"
                      size="small"
                      type="warning"
                      effect="light"
                      round
                    >
                      {{ pendingUsers.length }}
                    </el-tag>
                  </div>
                  <ul
                    v-if="pendingUsers.length"
                    class="todo-list"
                  >
                    <li
                      v-for="u in pendingUsers"
                      :key="u.id"
                    >
                      <RouterLink
                        to="/admin/review"
                        class="todo-item"
                        @click="todoOpen = false"
                      >
                        <span class="todo-dot" />
                        <span class="todo-body">
                          <span class="todo-title">{{ u.realName || u.userName }}</span>
                          <span class="todo-meta">申请注册 · {{ u.userName }}</span>
                        </span>
                        <span class="todo-go">审核</span>
                      </RouterLink>
                    </li>
                  </ul>
                  <p
                    v-else
                    class="todo-empty"
                  >
                    暂无待审核注册
                  </p>
                  <RouterLink
                    v-if="pendingUsers.length"
                    to="/admin/review"
                    class="todo-more"
                    @click="todoOpen = false"
                  >
                    全部审核
                  </RouterLink>
                </section>

                <!-- 待审批请假 -->
                <section
                  v-if="isManager"
                  class="todo-group"
                >
                  <div class="todo-group-title">
                    <span>请假审批</span>
                    <el-tag
                      v-if="pendingLeaves.length"
                      size="small"
                      type="danger"
                      effect="light"
                      round
                    >
                      {{ pendingLeaves.length }}
                    </el-tag>
                  </div>
                  <ul
                    v-if="pendingLeaves.length"
                    class="todo-list"
                  >
                    <li
                      v-for="l in pendingLeaves"
                      :key="l.id"
                    >
                      <RouterLink
                        to="/leave"
                        class="todo-item"
                        @click="todoOpen = false"
                      >
                        <span class="todo-dot todo-dot--leave" />
                        <span class="todo-body">
                          <span class="todo-title">
                            {{ l.realName || '员工' }} · {{ l.leaveType }}
                          </span>
                          <span class="todo-meta">{{ fmtDateTime(l.startTime) }} 起</span>
                        </span>
                        <span class="todo-go">审批</span>
                      </RouterLink>
                    </li>
                  </ul>
                  <p
                    v-else
                    class="todo-empty"
                  >
                    暂无待审批请假
                  </p>
                  <RouterLink
                    v-if="pendingLeaves.length"
                    to="/leave"
                    class="todo-more"
                    @click="todoOpen = false"
                  >
                    全部审批
                  </RouterLink>
                </section>

                <el-empty
                  v-if="todoCount === 0"
                  description="暂无待办事项"
                  :image-size="56"
                />
              </div>
            </el-popover>

            <RouterLink
              class="topbar-link"
              to="/workbench"
            >
              我的工作台
            </RouterLink>
            <RouterLink
              class="topbar-link"
              to="/account-center"
            >
              账号中心
            </RouterLink>
          </div>
        </header>

        <button
          v-if="menuOpen"
          class="menu-backdrop"
          aria-label="关闭功能菜单"
          @click="menuOpen = false"
        ></button>

        <!-- 内容区 -->
        <div
          id="admin-content"
          class="main-content"
          tabindex="-1"
        >
          <RouterView />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
/* Reference adaptation; Unslop by Damir Sokolovsky, CC BY 4.0.
   https://github.com/heydamir/unslop */
.hr-shell {
  min-height: 100dvh;
  padding: 20px;
  background: var(--bg-page);
  color: var(--text-primary);
}
.app-frame {
  position: relative;
  z-index: 1;
  max-width: 1600px;
  min-height: calc(100dvh - 40px);
  margin: auto;
  border: 1px solid #fff;
  border-radius: 28px;
  background: #faf9fc;
  box-shadow: 0 12px 48px #26233608;
  overflow: clip;
}
.app-main {
  min-width: 0;
}
.topbar {
  display: flex;
  align-items: center;
  gap: 20px;
  height: 72px;
  padding: 0 24px;
}
.topbar-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  color: var(--text-strong);
  text-decoration: none;
  font-weight: 600;
  font-size: 14px;
}
.topbar-brand svg {
  width: 24px;
  height: 24px;
}
.top-navigation {
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
}
.top-navigation a {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 84px;
  padding: 10px 4px;
  border-radius: 24px;
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 12px;
  white-space: nowrap;
  transition: color 260ms cubic-bezier(0.22, 1, 0.36, 1);
}
.top-navigation a:hover {
  color: var(--text-strong);
}
.top-navigation a.is-active {
  color: #fff;
}
.top-navigation a > span {
  display: block;
  transition: transform 400ms cubic-bezier(0.22, 1.35, 0.36, 1);
}
.top-navigation a.is-active > span {
  animation: top-nav-label 400ms cubic-bezier(0.22, 1.35, 0.36, 1);
}
.top-nav-capsule {
  position: absolute;
  inset: 0 auto 0 0;
  z-index: 0;
  width: 84px;
  border-radius: 24px;
  background: #242033;
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 16%),
    0 6px 14px rgb(36 32 51 / 14%);
  transform-origin: center;
  transition:
    transform 400ms cubic-bezier(0.22, 1.18, 0.36, 1),
    border-radius 220ms cubic-bezier(0.22, 1, 0.36, 1);
  will-change: transform;
}
.top-nav-capsule::after {
  position: absolute;
  inset: 4px;
  content: '';
  border-radius: inherit;
  background: rgb(255 255 255 / 5%);
  opacity: 0;
  transition:
    transform 400ms cubic-bezier(0.22, 1.25, 0.36, 1),
    opacity 160ms;
}
.top-nav-capsule.is-moving {
  border-radius: 20px;
  animation-duration: 400ms;
  animation-timing-function: cubic-bezier(0.22, 1, 0.36, 1);
}
.top-nav-capsule.is-moving::after {
  opacity: 1;
  transform: scaleX(1.18) scaleY(0.82);
}
.top-nav-capsule.is-forward {
  transform-origin: right center;
  animation-name: capsule-travel-forward;
}
.top-nav-capsule.is-backward {
  transform-origin: left center;
  animation-name: capsule-travel-backward;
}
.top-nav-capsule.is-settling {
  animation: capsule-settle 140ms cubic-bezier(0.34, 1.56, 0.64, 1);
}
@keyframes top-nav-label {
  0% {
    transform: scale(1);
  }
  38% {
    transform: scale(0.88);
  }
  100% {
    transform: scale(1);
  }
}
@keyframes capsule-settle {
  0% {
    scale: 1 0.92;
  }
  65% {
    scale: 1.025 1.03;
  }
  100% {
    scale: 1;
  }
}
@keyframes capsule-travel-forward {
  0% {
    scale: 1;
  }
  42% {
    scale: 1.14 0.88;
  }
  78% {
    scale: 1.04 0.96;
  }
  100% {
    scale: 1 0.94;
  }
}
@keyframes capsule-travel-backward {
  0% {
    scale: 1;
  }
  42% {
    scale: 1.14 0.88;
  }
  78% {
    scale: 1.04 0.96;
  }
  100% {
    scale: 1 0.94;
  }
}
@media (prefers-reduced-motion: reduce) {
  .top-nav-capsule,
  .top-nav-capsule::after,
  .top-navigation a,
  .top-navigation a > span {
    transition: none;
    animation: none;
  }
  .top-nav-capsule.is-moving,
  .top-nav-capsule.is-settling {
    scale: 1;
    animation: none;
  }
}
.topbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}
.topbar-search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 140px;
  height: 36px;
  padding: 0 12px;
  border: 1px solid #eeedf2;
  background: white;
  border-radius: 24px;
}
.topbar-search svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}
.topbar-search input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  font: inherit;
  font-size: 12px;
  color: var(--text-primary);
}
.topbar-link {
  font-size: 12px;
  color: var(--text-secondary);
  text-decoration: none;
  white-space: nowrap;
}
.topbar-link:first-of-type {
  display: none;
}
.topbar-menu {
  display: none;
  width: 36px;
  height: 36px;
  padding: 8px;
  border: 0;
  border-radius: 50%;
  background: #efedf3;
  cursor: pointer;
}
.topbar-menu svg {
  width: 20px;
  height: 20px;
}
.main-content {
  min-width: 0;
  margin-left: 88px;
  padding: 20px 24px 20px;
  background: white;
  border-radius: 24px 0 0;
}
.icon-rail {
  position: absolute;
  top: 92px;
  left: 16px;
  width: 56px;
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 8px;
  padding: 12px 4px;
  border: 1px solid white;
  border-radius: 32px;
  background: #fff;
  box-shadow: 0 4px 16px #29243605;
  z-index: 20;
}
.icon-rail a,
.icon-rail button {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 4px;
  width: 44px;
  min-height: 44px;
  padding: 8px 0;
  border: 0;
  border-radius: 24px;
  background: transparent;
  color: #615e6b;
  text-decoration: none;
  font: inherit;
  cursor: pointer;
}
.icon-rail svg {
  width: 20px;
  height: 20px;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.icon-rail span {
  font-size: 12px;
  line-height: 16px;
}
.icon-rail a:hover,
.icon-rail button:hover {
  background: #f2f0f5;
  color: #242033;
}
.icon-rail .active,
.icon-rail .rail-toggle {
  background: #242033;
  color: white;
}
.rail-toggle {
  margin-bottom: 8px;
}
.rail-bottom {
  border-top: 1px solid #eeedf2;
  padding-top: 8px;
  margin-top: 8px;
}
.menu-backdrop {
  position: fixed;
  inset: 0;
  border: 0;
  background: #28243314;
  z-index: 21;
}
.expanded-menu {
  position: absolute;
  top: 88px;
  left: 88px;
  z-index: 22;
  width: 552px;
  max-width: calc(100% - 112px);
  padding: 24px;
  border: 1px solid #fff;
  border-radius: 32px;
  background: #eeedf2;
  box-shadow: 0 16px 48px #2520341a;
}
.expanded-menu > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.expanded-menu > header strong {
  font-size: 18px;
  font-weight: 500;
}
.expanded-menu > header span {
  font-size: 12px;
  color: var(--text-secondary);
}
.expanded-menu > header button {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  background: #fff;
  color: #333;
  font-size: 24px;
  cursor: pointer;
}
.menu-columns {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 20px;
}
.menu-tree section + section {
  margin-top: 8px;
}
.group-button {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 16px;
  border: 0;
  border-radius: 24px;
  background: transparent;
  text-align: left;
  color: #29262e;
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}
.group-button span {
  margin-left: auto;
}
.group-button.is-selected {
  background: #17181b;
  color: #fff;
}
.group-button svg,
.nav-item svg {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.tree-children {
  position: relative;
  margin: 8px 0 16px 24px;
  padding-left: 12px;
  border-left: 1px solid #b7b4bf;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 8px 12px;
  margin: 4px 0;
  border-radius: 24px;
  color: #39363e;
  text-decoration: none;
  font-size: 12px;
  white-space: nowrap;
}
.nav-item:hover,
.nav-item.is-active {
  background: #fff;
}
.tree-children .nav-item.is-active:before {
  content: '';
  position: absolute;
  left: -2px;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #242033;
}
.menu-related {
  border-left: 1px solid #dedce4;
  padding-left: 16px;
}
.menu-related h2 {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-secondary);
  margin: 8px 12px 16px;
}
.menu-account {
  display: flex;
  align-items: center;
  gap: 8px;
  border-top: 1px solid #dedce4;
  margin-top: 24px;
  padding-top: 16px;
  font-size: 12px;
}
.menu-account small {
  display: block;
  margin-top: 4px;
  color: var(--text-secondary);
}
.account-avatar {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #fff;
  color: #45404d;
}
.todo-trigger {
  position: relative;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid rgb(255 255 255 / 24%);
  border-radius: 20px;
  background: rgb(255 255 255 / 16%);
  color: #28364d;
  cursor: pointer;
  transition:
    background 200ms var(--ease-out),
    color 200ms var(--ease-out);
}
.todo-trigger:hover {
  background: rgb(255 255 255 / 32%);
  color: #111a2b;
}
.todo-trigger svg {
  width: 16px;
  height: 16px;
}
.todo-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border: 1.5px solid #fff;
  border-radius: 8px;
  background: #e5484d;
  color: #fff;
  font-size: 10px;
  line-height: 15px;
  font-weight: 600;
  text-align: center;
  box-sizing: border-box;
}

.todo-trigger {
  background: white;
  border-color: #eeedf2;
  color: #514b5d;
}
.todo-trigger:hover {
  background: #efedf3;
  color: #242033;
}
a:focus-visible,
button:focus-visible,
.topbar-search:focus-within {
  outline: 2px solid #21837e;
  outline-offset: 3px;
}
@media (max-width: 1200px) {
  .topbar {
    gap: 12px;
    padding-inline: 20px;
  }
  .top-navigation a {
    padding-inline: 10px;
  }
  .topbar-link {
    display: none;
  }
  .topbar-search {
    width: 120px;
  }
}
@media (max-width: 1000px) {
  .top-navigation {
    display: none;
  }
  .topbar-menu {
    display: block;
  }
  .topbar-brand {
    margin-right: auto;
  }
  .topbar-actions {
    margin-left: 0;
  }
  .icon-rail {
    display: none;
  }
  .main-content {
    margin-left: 0;
    border-radius: 24px 24px 0 0;
  }
  .expanded-menu {
    left: 20px;
    max-width: calc(100% - 40px);
  }
}
@media (max-width: 600px) {
  .hr-shell {
    padding: 8px;
  }
  .app-frame {
    min-height: calc(100dvh - 16px);
    border-radius: 20px;
  }
  .topbar {
    height: 64px;
    padding-inline: 12px;
    gap: 8px;
  }
  .topbar-brand span {
    display: none;
  }
  .topbar-actions {
    gap: 8px;
  }
  .topbar-search {
    width: 140px;
  }
  .main-content {
    padding: 16px 12px;
  }
  .expanded-menu {
    position: fixed;
    top: 80px;
    left: 16px;
    width: calc(100% - 32px);
    max-width: none;
    max-height: calc(100dvh - 96px);
    overflow: auto;
    padding: 20px;
    border-radius: 24px;
  }
  .menu-columns {
    gap: 12px;
    grid-template-columns: 1fr;
  }
  .menu-related {
    border: 0;
    border-top: 1px solid #dedce4;
    padding: 12px 0 0;
  }
  .menu-account {
    margin-top: 12px;
  }
  .menu-related .nav-item {
    display: inline-flex;
  }
  .menu-related h2 {
    margin-bottom: 8px;
  }
}

/* Smooth menu presence without animating layout dimensions. */
.menu-reveal-enter-active,
.menu-reveal-leave-active {
  transition:
    opacity 300ms var(--ease-out),
    transform 300ms var(--ease-out);
  transform-origin: 32px 0;
}
.menu-reveal-enter-from,
.menu-reveal-leave-to {
  opacity: 0;
  transform: translate3d(0, -12px, 0) scale(0.985);
}
.submenu-reveal-enter-active,
.submenu-reveal-leave-active {
  transition:
    opacity 220ms var(--ease-out),
    transform 220ms var(--ease-out);
}
.submenu-reveal-enter-from,
.submenu-reveal-leave-to {
  opacity: 0;
  transform: translate3d(0, -8px, 0);
}
.expanded-menu {
  background: rgb(242 241 246 / 84%);
  border-color: rgb(255 255 255 / 72%);
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 72%),
    0 20px 56px rgb(40 35 52 / 16%);
  backdrop-filter: blur(10px) saturate(110%);
  -webkit-backdrop-filter: blur(10px) saturate(110%);
}
.group-button,
.nav-item,
.expanded-menu > header button {
  transition:
    color 220ms var(--ease-out),
    background 220ms var(--ease-out),
    border-color 220ms var(--ease-out),
    transform 220ms var(--ease-out);
}
.group-button:active,
.nav-item:active,
.expanded-menu > header button:active {
  transform: translateY(1px) scale(0.985);
}
@media (prefers-reduced-motion: reduce) {
  .menu-reveal-enter-active,
  .menu-reveal-leave-active,
  .submenu-reveal-enter-active,
  .submenu-reveal-leave-active,
  .group-button,
  .nav-item,
  .expanded-menu > header button {
    transition: none;
  }
}
</style>

<style lang="scss">
/* 待办中心下拉面板（el-popover teleport 到 body，需全局样式） */
.todo-popover.el-popover {
  padding: 0;
  border: 1px solid var(--border-default, #e8eaee);
  border-radius: 16px;
  box-shadow: var(--shadow-lg, 0 16px 48px rgb(20 26 24 / 12%));
  overflow: hidden;
}
.todo-popover .todo-panel {
  max-height: 420px;
  overflow-y: auto;
}
.todo-popover .todo-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-light, #eff1f4);
}
.todo-popover .todo-head strong {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-strong, #1b1d21);
}
.todo-popover .todo-head-count {
  font-size: 12px;
  color: var(--text-secondary, #8b909a);
}
.todo-popover .todo-group {
  padding: 12px 8px;
}
.todo-popover .todo-group + .todo-group {
  border-top: 1px solid var(--border-light, #eff1f4);
}
.todo-popover .todo-group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary, #8b909a);
}
.todo-popover .todo-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.todo-popover .todo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
  border-radius: 10px;
  text-decoration: none;
  color: inherit;
  transition: background 150ms cubic-bezier(0.22, 1, 0.36, 1);
}
.todo-popover .todo-item:hover {
  background: var(--bg-hover, #f3f5f7);
}
.todo-popover .todo-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--sem-warning, #f59e0b);
  flex-shrink: 0;
}
.todo-popover .todo-dot--leave {
  background: var(--sem-danger, #e05b5b);
}
.todo-popover .todo-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.todo-popover .todo-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-strong, #1b1d21);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.todo-popover .todo-meta {
  font-size: 12px;
  color: var(--text-secondary, #8b909a);
}
.todo-popover .todo-go {
  font-size: 12px;
  color: var(--brand-500, #1a2e2a);
  font-weight: 500;
  flex-shrink: 0;
}
.todo-popover .todo-empty {
  margin: 0;
  padding: 4px 8px 8px;
  font-size: 12px;
  color: var(--text-disabled, #aab0b8);
}
.todo-popover .todo-more {
  display: block;
  margin: 4px 8px 0;
  padding: 8px;
  border-radius: 10px;
  text-align: center;
  font-size: 13px;
  font-weight: 500;
  color: var(--brand-600, #1f3a33);
  text-decoration: none;
  background: var(--brand-50, #f0f4f2);
}
.todo-popover .todo-more:hover {
  background: var(--brand-100, #e2eae7);
}
</style>
