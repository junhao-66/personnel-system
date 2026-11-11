import request from '@/utils/request'
import type { Result } from '@/types/result'
import type { AiAnswerVO, AiStatusVO } from '@/types/ai'
export const getAiStatus = () => request.get<Result<AiStatusVO>>('/ai/status')
export const expandAiReport = (resultId: number) =>
  request.post<Result<AiAnswerVO>>(`/ai/reports/${resultId}/expand`, {}, { timeout: 35000 })
export const askAiEmployee = (userId: number, question: string) =>
  request.post<Result<AiAnswerVO>>(
    `/admin/ai/employees/${userId}/ask`,
    { question },
    { timeout: 35000 },
  )
export const askAiDepartment = (departmentId: number, question: string) =>
  request.post<Result<AiAnswerVO>>(
    `/admin/ai/departments/${departmentId}/ask`,
    { question },
    { timeout: 35000 },
  )
