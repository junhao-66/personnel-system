import type { GraphRule, GraphNode, GraphEdge, GraphSource } from './knowledgeGraph'
import type { TalentDashboardVO } from './talent'
export interface JobProfileVO {
  id: number
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  contentHash: string
  definition: {
    positionId: number
    positionName: string
    version: string
    title: string
    graphVersion: string
    graphContentHash: string
    attribution: string
    rules: WeightedRule[]
  }
}
export interface WeightedRule {
  weight: number
  rule: GraphRule
  context: string
  action: string
  nodes: GraphNode[]
  edges: GraphEdge[]
  sources: GraphSource[]
}
export interface JobProfileCreateReqDTO {
  positionId?: number
  version: string
  title: string
  graphId?: number
  attribution: string
  rules: { ruleId: string; weight: number }[]
}
export interface JobExplorationVO {
  resultId: number | null
  scaleVersion: string | null
  graphVersion: string | null
  notice: string
  positions: {
    profile: JobProfileVO
    comparable: boolean
    ruleCoveragePercent: number | null
    matchedRuleIds: string[]
    explanation: string
  }[]
}
export interface TeamAnalysisVO {
  departmentId: number
  departmentName: string
  distribution: TalentDashboardVO
  dimensions: { dimension: string; firstCount: number; secondCount: number }[]
  collaborationTips: string[]
  ruleVersion: string
  notice: string
}
