import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import type { TalentQueryReqDTO } from '@/types/talent'

export const PERSONALITY_TYPES = [
  'INTJ',
  'INTP',
  'ENTJ',
  'ENTP',
  'INFJ',
  'INFP',
  'ENFJ',
  'ENFP',
  'ISTJ',
  'ISFJ',
  'ESTJ',
  'ESFJ',
  'ISTP',
  'ISFP',
  'ESTP',
  'ESFP',
]
export const defaultTalentQuery = (): TalentQueryReqDTO => ({
  pageNum: 1,
  pageSize: 10,
  employmentStatus: 'ACTIVE',
  assessmentStatus: 'ALL',
})
/** Restore shareable filters and reject malformed URL values before sending them to the API. */
export function parseTalentQuery(query: LocationQuery): TalentQueryReqDTO {
  const result = defaultTalentQuery()
  const integer = (value: unknown, min: number, max: number) =>
    typeof value === 'string' &&
    /^\d+$/.test(value) &&
    Number.isSafeInteger(Number(value)) &&
    Number(value) >= min &&
    Number(value) <= max
      ? Number(value)
      : undefined
  result.departmentId = integer(query.departmentId, 0, Number.MAX_SAFE_INTEGER)
  result.positionId = integer(query.positionId, 0, Number.MAX_SAFE_INTEGER)
  result.pageNum = integer(query.pageNum, 1, 1000000) ?? 1
  result.pageSize = integer(query.pageSize, 1, 100) ?? 10
  if (query.employmentStatus === 'RESIGNED' || query.employmentStatus === 'ALL')
    result.employmentStatus = query.employmentStatus
  if (query.assessmentStatus === 'COMPLETED' || query.assessmentStatus === 'NOT_COMPLETED')
    result.assessmentStatus = query.assessmentStatus
  if (typeof query.typeCode === 'string' && PERSONALITY_TYPES.includes(query.typeCode))
    result.typeCode = query.typeCode
  return result
}
export function talentRouteQuery(query: TalentQueryReqDTO): LocationQueryRaw {
  return {
    departmentId: query.departmentId,
    positionId: query.positionId,
    employmentStatus: query.employmentStatus,
    assessmentStatus: query.assessmentStatus,
    typeCode: query.typeCode,
    pageNum: query.pageNum,
    pageSize: query.pageSize,
  }
}
export function talentRate(value: number | null) {
  return value === null ? '暂无数据' : `${value.toFixed(2)}%`
}
export function talentTime(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}
