export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export interface LeaveCreateReqDTO {
  leaveType: string
  startTime: string
  endTime: string
  reason: string
}
export interface LeaveReviewReqDTO {
  status: 'APPROVED' | 'REJECTED'
  reviewComment: string
}
export interface LeaveQueryReqDTO {
  pageNum: number
  pageSize: number
  status?: LeaveStatus
}
export interface LeaveOptionsVO {
  leaveTypes: string[]
  minimumMinutes: number
  crossDayAllowed: boolean
  timeZone: string
  approverName?: string
  unavailableReason?: string
}
export interface LeaveVO {
  id: number
  userId: number
  realName?: string
  departmentId: number
  leaveType: string
  startTime: string
  endTime: string
  reason: string
  status: LeaveStatus
  reviewerId?: number
  reviewComment?: string
  reviewedAt?: string
  createTime: string
}
