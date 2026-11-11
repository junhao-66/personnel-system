import request from '@/utils/request'
import type { Result } from '@/types/result'
import type { GraphImportReqDTO, KnowledgeGraphVO } from '@/types/knowledgeGraph'
export function getKnowledgeGraph() {
  return request.get<Result<KnowledgeGraphVO | null>>('/knowledge-graph')
}
export function getGraphVersions() {
  return request.get<Result<KnowledgeGraphVO[]>>('/admin/knowledge-graph')
}
export function importGraphVersion(data: GraphImportReqDTO) {
  return request.post<Result<KnowledgeGraphVO>>('/admin/knowledge-graph', data)
}
export function publishGraphVersion(id: number) {
  return request.put<Result<void>>(`/admin/knowledge-graph/${id}/publish`)
}
export function archiveGraphVersion(id: number) {
  return request.put<Result<void>>(`/admin/knowledge-graph/${id}/archive`)
}
