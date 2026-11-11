export interface AttendanceRuleVO {
  workStartTime: string
  workEndTime: string
  timeZone: string
  weekendRest: boolean
}

export interface AttendanceRecordVO {
  id?: number
  userId: number
  attendanceDate: string
  clockInTime?: string
  clockOutTime?: string
  workStartTime: string
  workEndTime: string
  restDay: boolean
  late: boolean
  early: boolean
  status: string
  approvedLeaveMinutes?: number
  missingIn?: boolean
  missingOut?: boolean
  requiredStart?: string
  requiredEnd?: string
  scheduleBasis?: 'CURRENT_RULE' | 'RECORDED_RULE'
}

export interface TodayAttendanceVO extends Omit<AttendanceRuleVO, 'weekendRest'> {
  attendanceDate: string
  serverTime: string
  restDay: boolean
  record?: AttendanceRecordVO
  dayStatus?: AttendanceRecordVO
}

export interface AttendanceEmployeeVO {
  attendance: AttendanceRecordVO
  userName?: string
  realName?: string
  departmentName?: string
}

export interface AttendanceQueryDTO {
  pageNum: number
  pageSize: number
  startDate?: string
  endDate?: string
  keyword?: string
  departmentId?: number
  includeMissing?: boolean
}

export interface AttendanceRuleUpdateDTO {
  workStartTime: string
  workEndTime: string
}
