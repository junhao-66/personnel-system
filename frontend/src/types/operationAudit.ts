export interface OperationAuditVO {
  id: number
  actorUserId?: number
  actorRole: string
  operation: string
  targetType: string
  targetId: number
  changedFields: string[]
  occurredAt: string
}
export interface OperationAuditQueryReqDTO {
  pageNum: number
  pageSize: number
  actorUserId?: number
  targetId?: number
  targetType?: string
  operation?: string
  startDate?: string
  endDate?: string
}
