import request from '@/utils/request'
import type { Result } from '@/types/result'
import type { GrowthTemplateCreateReqDTO, GrowthTemplateVO } from '@/types/growthTemplate'
export function getGrowthTemplates() {
  return request.get<Result<GrowthTemplateVO[]>>('/admin/growth-templates')
}
export function getEffectiveGrowthTemplate() {
  return request.get<Result<GrowthTemplateVO>>('/admin/growth-templates/effective')
}
export function createGrowthTemplate(data: GrowthTemplateCreateReqDTO) {
  return request.post<Result<GrowthTemplateVO>>('/admin/growth-templates', data)
}
export function publishGrowthTemplate(id: number) {
  return request.put<Result<void>>(`/admin/growth-templates/${id}/publish`)
}
export function archiveGrowthTemplate(id: number) {
  return request.put<Result<void>>(`/admin/growth-templates/${id}/archive`)
}
