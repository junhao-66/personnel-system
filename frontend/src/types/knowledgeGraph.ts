export interface GraphSource {
  id: string
  title: string
  author: string
  url: string
  accessedDate: string
  license: string
  attribution: string
  contentVersion: string
  evidenceLevel: 'PROJECT_DEMO' | 'CURATED_REFERENCE'
}
export interface GraphNode {
  id: string
  type: string
  label: string
  description: string
  sourceId: string
}
export interface GraphEdge {
  id: string
  from: string
  to: string
  relation: string
  explanation: string
  sourceId: string
}
export interface GraphCondition {
  nodeId: string
  dimension: string
  pole: string
  minimumPercent: number
}
export interface GraphRule {
  id: string
  actionId: string
  contextId: string
  explanation: string
  conditions: GraphCondition[]
  edgeIds: string[]
}
export interface GraphImportReqDTO {
  version: string
  title: string
  sources: GraphSource[]
  nodes: GraphNode[]
  edges: GraphEdge[]
  rules: GraphRule[]
}
export interface KnowledgeGraphVO extends GraphImportReqDTO {
  id: number
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  contentHash: string
}
export interface GraphMatch {
  ruleId: string
  context: string
  action: string
  explanation: string
  matchedConditions: string[]
  nodes: GraphNode[]
  edges: GraphEdge[]
  sources: GraphSource[]
}
export interface GraphExplanationVO {
  graphVersion?: string
  contentHash?: string
  notice: string
  matches: GraphMatch[]
}
