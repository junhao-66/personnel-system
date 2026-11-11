export interface UserVO {
  id: number
  userName: string
  realName: string
  phone?: string
  roleCode: string
  auditStatus: string
  userStatus: string
  departmentId?: number
  positionId?: number
  auditReason?: string
  reviewedAt?: string
}
export interface AccountReviewVO {
  userName: string
  auditStatus: string
  userStatus: string
  auditReason?: string
  reviewedAt?: string
}
export interface UserAccessReqDTO {
  roleCode: 'ADMIN' | 'DEPARTMENT_MANAGER' | 'EMPLOYEE'
  userStatus: 'ENABLED' | 'DISABLED'
}
export interface LoginVO {
  accessToken: string
  user: UserVO
}
export interface UserRegisterDTO {
  userName: string
  password: string
  realName: string
  phone?: string
}
export interface UserLoginDTO {
  userName: string
  password: string
}
