import request from '@/utils/request'
import type {
  AccountReviewVO,
  LoginVO,
  UserAccessReqDTO,
  UserLoginDTO,
  UserRegisterDTO,
  UserVO,
} from '@/types/user'
import type { Result } from '@/types/result'
import type { AdminUserCreateDTO, AdminUserVO } from '@/types/adminUser'
export function registerUser(data: UserRegisterDTO) {
  return request.post<Result<void>>('/user/register', data)
}
export function loginUser(data: UserLoginDTO) {
  return request.post<Result<LoginVO>>('/user/login', data)
}
export function getPendingUsers() {
  return request.get<Result<UserVO[]>>('/user/pending')
}
export function auditUser(id: number, auditStatus: 'APPROVED' | 'REJECTED', auditReason?: string) {
  return request.put<Result<void>>(`/user/${id}/audit`, { auditStatus, auditReason })
}
export function getAccountReview(data: UserLoginDTO) {
  return request.post<Result<AccountReviewVO>>('/user/review-status', data)
}
export function updateUserAccess(id: number, data: UserAccessReqDTO) {
  return request.put<Result<void>>(`/admin/users/${id}/access`, data)
}
export function getUsers() {
  return request.get<Result<AdminUserVO[]>>('/admin/users')
}
export function createAdminUser(data: AdminUserCreateDTO) {
  return request.post<Result<AdminUserVO>>('/admin/users', data)
}
