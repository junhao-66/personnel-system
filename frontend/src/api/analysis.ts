import request from '@/utils/request'
import type { Result } from '@/types/result'
import type {
  JobProfileVO,
  JobProfileCreateReqDTO,
  JobExplorationVO,
  TeamAnalysisVO,
} from '@/types/analysis'
export const getJobProfiles = () => request.get<Result<JobProfileVO[]>>('/admin/job-preferences')
export const createJobProfile = (data: JobProfileCreateReqDTO) =>
  request.post<Result<JobProfileVO>>('/admin/job-preferences', data)
export const publishJobProfile = (id: number) =>
  request.put<Result<void>>(`/admin/job-preferences/${id}/publish`)
export const archiveJobProfile = (id: number) =>
  request.put<Result<void>>(`/admin/job-preferences/${id}/archive`)
export const getJobExploration = (ownerId?: number) =>
  request.get<Result<JobExplorationVO>>(
    ownerId ? `/admin/job-preferences/users/${ownerId}` : '/job-preferences',
  )
export const getTeamAnalysis = (departmentId: number) =>
  request.get<Result<TeamAnalysisVO>>('/admin/team-analysis', { params: { departmentId } })
