import request from '@/utils/request'
import type { Result } from '@/types/result'
import type { EmployeeQueryDTO, EmployeeUpdateDTO, EmployeeVO, PageResult } from '@/types/employee'

export function getEmployees(params: EmployeeQueryDTO) {
  return request.get<Result<PageResult<EmployeeVO>>>('/admin/employees', { params })
}

export function updateEmployee(id: number, data: EmployeeUpdateDTO) {
  return request.put<Result<void>>(`/admin/employees/${id}`, data)
}
