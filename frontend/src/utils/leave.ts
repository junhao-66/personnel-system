/** Calendar duration for server Asia/Shanghai wall times, independent of the browser timezone. */
export function getLeaveMinutes(start: string, end: string): number | undefined {
  const parse = (value: string) => {
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:00$/.test(value)) return undefined
    const timestamp = Date.parse(value + 'Z')
    if (!Number.isFinite(timestamp) || new Date(timestamp).toISOString().slice(0, 19) !== value)
      return undefined
    return timestamp
  }
  const first = parse(start),
    last = parse(end)
  return first === undefined || last === undefined ? undefined : (last - first) / 60000
}

/** Client guidance only; permission, overlap and current-time checks remain server decisions. */
export function getLeaveTimeError(
  start: string,
  end: string,
  minimumMinutes: number,
  crossDayAllowed: boolean,
): string {
  const minutes = getLeaveMinutes(start, end)
  if (minutes === undefined) return '请选择有效且精确到分钟的开始与结束时间'
  if (minutes <= 0) return '结束时间必须晚于开始时间'
  if (minutes < minimumMinutes || minutes % minimumMinutes !== 0)
    return `请假时长须为${minimumMinutes}分钟的整数倍`
  if (!crossDayAllowed && start.slice(0, 10) !== end.slice(0, 10)) return '当前规则不允许跨日请假'
  return ''
}
