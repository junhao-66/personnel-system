import request from '@/utils/request'
import type { Result } from '@/types/result'
import type { PageResult } from '@/types/employee'
import type { TalentDashboardVO, TalentEmployeeVO, TalentQueryReqDTO } from '@/types/talent'

export function getTalentDashboard(params: TalentQueryReqDTO) {
  return request.get<Result<TalentDashboardVO>>('/admin/talent/dashboard', { params })
}
export function getTalentEmployees(params: TalentQueryReqDTO) {
  return request.get<Result<PageResult<TalentEmployeeVO>>>('/admin/talent/employees', { params })
}
export function getTalentEmployee(id: number) {
  return request.get<Result<TalentEmployeeVO>>(`/admin/talent/employees/${id}`)
}
