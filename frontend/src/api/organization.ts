import request from '@/utils/request'
import type { Result } from '@/types/result'
import type {
  DepartmentVO,
  PositionVO,
  DepartmentSaveReqDTO,
  PositionSaveReqDTO,
} from '@/types/organization'

export function getDepartments() {
  return request.get<Result<DepartmentVO[]>>('/admin/departments')
}
export function updateDepartment(id: number, data: DepartmentSaveReqDTO) {
  return request.put<Result<DepartmentVO>>(`/admin/departments/${id}`, data)
}
export function updatePosition(id: number, data: PositionSaveReqDTO) {
  return request.put<Result<PositionVO>>(`/admin/positions/${id}`, data)
}
export function addDepartment(data: DepartmentSaveReqDTO) {
  return request.post<Result<DepartmentVO>>('/admin/departments', data)
}
export function setDepartmentEnabled(id: number, enabled: boolean) {
  return request.put<Result<void>>(`/admin/departments/${id}/enabled`, null, {
    params: { enabled },
  })
}
export function getPositions(departmentId?: number) {
  return request.get<Result<PositionVO[]>>('/admin/positions', {
    params: departmentId ? { departmentId } : {},
  })
}
export function addPosition(data: PositionSaveReqDTO) {
  return request.post<Result<PositionVO>>('/admin/positions', data)
}
export function setPositionEnabled(id: number, enabled: boolean) {
  return request.put<Result<void>>(`/admin/positions/${id}/enabled`, null, { params: { enabled } })
}
