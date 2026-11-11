export type EmploymentStatus = 'ACTIVE' | 'RESIGNED'

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface EmployeeVO {
  id: number
  userName: string
  realName: string
  phone?: string
  roleCode: string
  departmentId?: number
  departmentName?: string
  positionId?: number
  positionName?: string
  hireDate?: string
  employmentStatus: EmploymentStatus
  userStatus: string
}

export interface EmployeeQueryDTO {
  pageNum: number
  pageSize: number
  keyword?: string
  departmentId?: number
  employmentStatus?: EmploymentStatus
}

export interface EmployeeUpdateDTO {
  realName: string
  phone: string
  departmentId: number | null
  positionId: number | null
  hireDate: string | null
  employmentStatus: EmploymentStatus
}
