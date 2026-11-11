import request from '@/utils/request'
import type { Result } from '@/types/result'
import type { PageResult } from '@/types/employee'
import type {
  AssessmentAnswersReqDTO,
  AssessmentComparisonVO,
  AssessmentHistoryVO,
  AssessmentQueryReqDTO,
  AssessmentResultVO,
  AssessmentScaleVO,
  AssessmentSessionVO,
  AssessmentStartReqDTO,
  ScaleImportReqDTO,
  ScalePublishReqDTO,
} from '@/types/assessment'

export function getAssessmentScales() {
  return request.get<Result<AssessmentScaleVO[]>>('/assessment/scales')
}
export function getCurrentAssessment() {
  return request.get<Result<AssessmentSessionVO | null>>('/assessment/current')
}
export function startAssessment(data: AssessmentStartReqDTO) {
  return request.post<Result<AssessmentSessionVO>>('/assessment/sessions', data)
}
export function getAssessmentSession(id: number) {
  return request.get<Result<AssessmentSessionVO>>(`/assessment/sessions/${id}`)
}
export function saveAssessmentAnswers(id: number, data: AssessmentAnswersReqDTO) {
  return request.put<Result<AssessmentSessionVO>>(`/assessment/sessions/${id}/answers`, data)
}
export function submitAssessment(id: number, data: AssessmentAnswersReqDTO) {
  return request.post<Result<AssessmentResultVO>>(`/assessment/sessions/${id}/submit`, data)
}
export function getAssessmentResult(id: number) {
  return request.get<Result<AssessmentResultVO>>(`/assessment/results/${id}`)
}
export function getAssessmentPdf(id: number) {
  return request.get<Blob>(`/assessment/results/${id}/pdf`, {
    responseType: 'blob',
    timeout: 30000,
  })
}
export function getAssessmentHistory(params: AssessmentQueryReqDTO) {
  return request.get<Result<PageResult<AssessmentHistoryVO>>>('/assessment/history', { params })
}
export function compareAssessmentResults(firstId: number, secondId: number) {
  return request.get<Result<AssessmentComparisonVO>>('/assessment/compare', {
    params: { firstId, secondId },
  })
}
export function getAdminAssessmentScales() {
  return request.get<Result<AssessmentScaleVO[]>>('/admin/assessment/scales')
}
export function importAssessmentScale(data: ScaleImportReqDTO) {
  return request.post<Result<AssessmentScaleVO>>('/admin/assessment/scales', data)
}
export function publishAssessmentScale(id: number, data: ScalePublishReqDTO) {
  return request.put<Result<void>>(`/admin/assessment/scales/${id}/publish`, data)
}
export function archiveAssessmentScale(id: number) {
  return request.put<Result<void>>(`/admin/assessment/scales/${id}/archive`)
}
export function getAdminAssessmentHistory(userId: number, params: AssessmentQueryReqDTO) {
  return request.get<Result<PageResult<AssessmentHistoryVO>>>(
    `/admin/assessment/users/${userId}/history`,
    { params },
  )
}
