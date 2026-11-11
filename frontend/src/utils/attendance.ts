const statusLabels: Record<string, string> = {
  IN_PROGRESS: '已上班，待下班',
  LATE_IN_PROGRESS: '迟到，待下班',
  NORMAL: '正常',
  LATE: '迟到',
  EARLY: '早退',
  LATE_AND_EARLY: '迟到且早退',
  REST_DAY: '休息日',
  APPROVED_LEAVE: '工作时段已获准请假',
  PARTIAL_LEAVE: '部分请假，其余打卡正常',
  NOT_STARTED: '尚未到岗时间',
  NOT_CLOCKED: '待上班打卡',
  MISSING_BOTH: '上下班均缺卡',
  MISSING_IN: '缺上班卡',
  MISSING_OUT: '缺下班卡',
}

export const attendanceStatusLabel = (status?: string) =>
  status ? statusLabels[status] || status : '未打卡'
export const formatAttendanceTime = (value?: string) =>
  value ? value.replace('T', ' ').slice(0, 19) : '未打卡'
export const shortTime = (value?: string) => value?.slice(0, 5) || '--:--'
