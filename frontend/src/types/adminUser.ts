import type { UserVO } from '@/types/user'
export interface AdminUserCreateDTO {
  userName: string
  password: string
  realName: string
  phone?: string
  roleCode: 'ADMIN' | 'DEPARTMENT_MANAGER' | 'EMPLOYEE'
  departmentId?: number
  positionId?: number
}
export type AdminUserVO = UserVO
