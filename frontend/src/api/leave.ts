import request from '@/utils/request'
import type { Result } from '@/types/result'
import type { PageResult } from '@/types/employee'
import type {
  LeaveCreateReqDTO,
  LeaveOptionsVO,
  LeaveQueryReqDTO,
  LeaveReviewReqDTO,
  LeaveVO,
} from '@/types/leave'

export function getLeaveOptions() {
  return request.get<Result<LeaveOptionsVO>>('/leave/options')
}
export function addLeave(data: LeaveCreateReqDTO) {
  return request.post<Result<LeaveVO>>('/leave', data)
}
export function getMyLeaves(params: LeaveQueryReqDTO) {
  return request.get<Result<PageResult<LeaveVO>>>('/leave', { params })
}
export function getReviewLeaves(params: LeaveQueryReqDTO) {
  return request.get<Result<PageResult<LeaveVO>>>('/leave/reviews', { params })
}
export function reviewLeave(id: number, data: LeaveReviewReqDTO) {
  return request.put<Result<void>>(`/leave/${id}/review`, data)
}
export function configureLeaveManager(id: number, managerUserId: number) {
  return request.put<Result<void>>(`/admin/departments/${id}/manager`, { managerUserId })
}
export function configureSelfApprover(id: number, managerUserId?: number) {
  return request.put<Result<void>>(`/admin/departments/${id}/self-approver`, {
    managerUserId: managerUserId ?? null,
  })
}
