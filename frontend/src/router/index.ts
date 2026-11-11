import { createRouter, createWebHistory } from 'vue-router'
import { readUser } from '@/utils/session'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/workspace',
      component: () => import('@/layout/AdminLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '/account-center',
          name: 'AccountCenter',
          component: () => import('@/views/account-center/AccountCenter.vue'),
          meta: { requiresAuth: true, title: '账号中心' },
        },
        {
          path: '/job-preferences',
          component: () => import('@/views/assessment/JobExploration.vue'),
          meta: { requiresAuth: true, title: '岗位偏好探索' },
        },
        {
          path: '/knowledge-graph',
          name: 'KnowledgeGraph',
          component: () => import('@/views/knowledge-graph/KnowledgeGraphPage.vue'),
          meta: { requiresAuth: true, title: '人格知识关系图' },
        },
        {
          path: '/assessment',
          name: 'Assessment',
          component: () => import('@/views/assessment/AssessmentPage.vue'),
          meta: { requiresAuth: true, title: '人格偏好测评' },
        },
        {
          path: '/assessment/history',
          name: 'AssessmentHistory',
          component: () => import('@/views/assessment/AssessmentHistoryPage.vue'),
          meta: { requiresAuth: true, title: '历次报告与结果对比' },
        },
        {
          path: '/leave',
          name: 'Leave',
          component: () => import('@/views/leave/LeavePage.vue'),
          meta: { requiresAuth: true, title: '请假与审批' },
        },
        {
          path: '/workbench',
          name: 'Workbench',
          component: () => import('@/views/workbench/Workbench.vue'),
          meta: { requiresAuth: true, title: '个人工作台' },
        },
        {
          path: '/mbti',
          name: 'MBTIOverview',
          component: () => import('@/views/assessment/MBTIOverview.vue'),
          meta: { title: 'MBTI人格偏好' },
        },
      ],
    },
    {
      path: '/',
      redirect: () => {
        const user = readUser()
        if (!localStorage.getItem('accessToken') || !user) return '/login'
        return user.roleCode === 'ADMIN' ? '/admin/dashboard' : '/workbench'
      },
    },
    { path: '/login', name: 'Login', component: () => import('@/views/auth/login.vue') },
    { path: '/register', name: 'Register', component: () => import('@/views/auth/register.vue') },
    {
      path: '/review-status',
      name: 'AccountReview',
      component: () => import('@/views/auth/AccountReview.vue'),
      meta: { title: '查询审核状态' },
    },
    {
      path: '/admin',
      component: () => import('@/layout/AdminLayout.vue'),
      meta: { requiresAdmin: true },
      redirect: '/admin/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/admin/dashboard/Dashboard.vue'),
          meta: { title: '数据总览' },
        },
        {
          path: 'management',
          component: () => import('@/views/admin/ManagementCenter.vue'),
          meta: { title: '管理配置' },
        },
        {
          path: 'employees/:employeeId',
          redirect: '/admin/employees',
        },
        {
          path: 'attendance/rules',
          component: () => import('@/views/admin/attendance-manage/AttendanceManage.vue'),
          meta: { title: '作息设置' },
        },
        {
          path: 'system-settings',
          name: 'SystemSettings',
          component: () => import('@/views/admin/system-settings/SystemSettings.vue'),
          meta: { title: '系统公告与测评开关' },
        },
        {
          path: 'ai-assistant',
          component: () => import('@/views/admin/ai-assistant/AiAssistant.vue'),
          meta: { title: '人才与团队辅助问答' },
        },
        {
          path: 'static-cache',
          component: () => import('@/views/admin/static-cache/StaticCache.vue'),
          meta: { title: '静态缓存维护' },
        },
        {
          path: 'job-preferences',
          component: () => import('@/views/admin/job-preferences/JobProfiles.vue'),
          meta: { title: '岗位探索配置' },
        },
        {
          path: 'team-analysis',
          component: () => import('@/views/admin/team-analysis/TeamAnalysis.vue'),
          meta: { title: '部门协作分析' },
        },
        {
          path: 'job-preferences/users/:userId',
          component: () => import('@/views/assessment/JobExploration.vue'),
          props: (route) => ({ ownerId: Number(route.params.userId) }),
          meta: { title: '员工岗位偏好探索' },
        },
        {
          path: 'growth-templates',
          name: 'GrowthTemplates',
          component: () => import('@/views/admin/growth-templates/GrowthTemplates.vue'),
          meta: { title: '建议模板管理' },
        },
        {
          path: 'operation-audits',
          name: 'OperationAudits',
          component: () => import('@/views/admin/operation-audits/OperationAudits.vue'),
          meta: { title: '操作记录' },
        },
        {
          path: 'talent-dashboard',
          name: 'TalentDashboard',
          component: () => import('@/views/admin/talent-dashboard/TalentDashboard.vue'),
          meta: { title: '测评统计' },
        },
        {
          path: 'talent-employees',
          name: 'TalentEmployees',
          component: () => import('@/views/admin/talent-dashboard/TalentEmployees.vue'),
          meta: { title: '看板人员明细' },
        },
        {
          path: 'knowledge-graph',
          name: 'KnowledgeGraphManage',
          component: () => import('@/views/admin/knowledge-graph/KnowledgeGraphManage.vue'),
          meta: { title: '知识图谱管理' },
        },
        {
          path: 'assessment-scales',
          name: 'AssessmentScales',
          component: () => import('@/views/admin/assessment-scales/AssessmentScales.vue'),
          meta: { title: '量表版本与题库' },
        },
        {
          path: 'assessment-history/:userId',
          name: 'AdminAssessmentHistory',
          component: () => import('@/views/assessment/AssessmentHistoryPage.vue'),
          props: (route) => ({ ownerId: Number(route.params.userId) }),
          meta: { title: '员工测评历史' },
        },
        {
          path: 'attendance',
          name: 'AdminAttendance',
          component: () => import('@/views/admin/attendance-manage/AttendanceManage.vue'),
          meta: { title: '考勤管理' },
        },
        {
          path: 'employees',
          name: 'AdminEmployees',
          component: () => import('@/views/admin/employee-manage/EmployeeManage.vue'),
          meta: { title: '员工档案' },
        },
        {
          path: 'review',
          name: 'AdminReview',
          component: () => import('@/views/admin/review.vue'),
          meta: { title: '注册审核' },
        },
        {
          path: 'organization',
          name: 'AdminOrganization',
          component: () => import('@/views/admin/organization.vue'),
          meta: { title: '部门与岗位' },
        },
        {
          path: 'users',
          name: 'AdminUsers',
          component: () => import('@/views/admin/users.vue'),
          meta: { title: '用户与角色' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAdmin || to.meta.requiresAuth) {
    const user = readUser()
    if (!localStorage.getItem('accessToken') || !user)
      return { path: '/login', query: { redirect: to.fullPath } }
    if (to.meta.requiresAdmin && user?.roleCode !== 'ADMIN') return '/workbench'
  }
  document.title = `${typeof to.meta.title === 'string' ? to.meta.title + ' · ' : ''}企业人事管理系统`
  return true
})

export default router
