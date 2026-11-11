export interface TalentQueryReqDTO {
  pageNum: number
  pageSize: number
  departmentId?: number
  positionId?: number
  employmentStatus: 'ACTIVE' | 'RESIGNED' | 'ALL'
  assessmentStatus: 'ALL' | 'COMPLETED' | 'NOT_COMPLETED'
  typeCode?: string
}
export interface TalentDashboardVO {
  employeeCount: number
  completedCount: number
  notCompletedCount: number
  assessmentCount: number
  completionRate: number | null
  typeDistribution: { typeCode: string; employeeCount: number; percent: number | null }[]
  departmentDistribution: {
    departmentId: number | null
    departmentName: string
    employeeCount: number
    completedCount: number
    notCompletedCount: number
    assessmentCount: number
    completionRate: number | null
  }[]
  generatedAt: string
  scopeNotice: string
}
export interface TalentEmployeeVO {
  userId: number
  userName: string
  realName: string
  roleCode: string
  userStatus: string
  departmentId: number | null
  departmentName: string
  positionId: number | null
  positionName: string
  hireDate: string | null
  employmentStatus: 'ACTIVE' | 'RESIGNED'
  assessmentCount: number
  latestResultId: number | null
  typeCode: string | null
  submittedAt: string | null
  scaleName: string | null
  scaleVersion: string | null
  scoringVersion: string | null
}
