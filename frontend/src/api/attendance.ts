import request from '@/utils/request'
import type { Result } from '@/types/result'
import type { PageResult } from '@/types/employee'
import type {
  AttendanceEmployeeVO,
  AttendanceQueryDTO,
  AttendanceRecordVO,
  AttendanceRuleUpdateDTO,
  AttendanceRuleVO,
  TodayAttendanceVO,
} from '@/types/attendance'

export function getAttendanceRule() {
  return request.get<Result<AttendanceRuleVO>>('/attendance/rule')
}
export function updateAttendanceRule(data: AttendanceRuleUpdateDTO) {
  return request.put<Result<void>>('/admin/attendance/rule', data)
}
export function getTodayAttendance() {
  return request.get<Result<TodayAttendanceVO>>('/attendance/today')
}
export function clockIn() {
  return request.post<Result<AttendanceRecordVO>>('/attendance/clock-in')
}
export function clockOut() {
  return request.post<Result<AttendanceRecordVO>>('/attendance/clock-out')
}
export function getMyAttendanceRecords(params: AttendanceQueryDTO) {
  return request.get<Result<PageResult<AttendanceEmployeeVO>>>('/attendance/records', { params })
}
export function getAdminAttendanceRecords(params: AttendanceQueryDTO) {
  return request.get<Result<PageResult<AttendanceEmployeeVO>>>('/admin/attendance/records', {
    params,
  })
}
