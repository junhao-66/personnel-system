import request from '@/utils/request'
import type { Result } from '@/types/result'
import type { PageResult } from '@/types/employee'
import type { OperationAuditQueryReqDTO, OperationAuditVO } from '@/types/operationAudit'

export function getOperationAudits(params: OperationAuditQueryReqDTO) {
  return request.get<Result<PageResult<OperationAuditVO>>>('/admin/operation-audits', { params })
}
