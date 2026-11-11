import type { GraphExplanationVO } from './knowledgeGraph'
export interface ScaleQuestionVO {
  key: string
  leftText: string
  rightText: string
  dimension: 'EI' | 'SN' | 'TF' | 'JP'
  highPole: string
  weight: number
}
export interface ScaleDefinitionVO {
  name: string
  version: string
  sourceTitle: string
  author: string
  sourceUrl: string
  accessedDate: string
  license: string
  licenseEvidence: string
  chineseVersion: string
  translationEvidence: string
  scoringVersion: string
  scoringEvidence: string
  retentionPolicy: string
  contact: string
  questions: ScaleQuestionVO[]
}
export type ScaleImportReqDTO = ScaleDefinitionVO
export interface AssessmentScaleVO {
  id: number
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  contentHash: string
  definition: ScaleDefinitionVO
  publishedBy?: number
  publishedAt?: string
}
export interface ScalePublishReqDTO {
  commercialUseVerified: boolean
  translationVerified: boolean
  scoringVerified: boolean
}
export interface AssessmentStartReqDTO {
  scaleId: number
  consentAccepted: boolean
}
export interface AssessmentAnswersReqDTO {
  revision: number
  answers: Record<string, number>
}
export interface AssessmentSessionVO {
  id: number
  status: 'DRAFT' | 'COMPLETED'
  revision: number
  contentHash: string
  scale: ScaleDefinitionVO
  answers: Record<string, number>
  consentAt: string
  updatedAt: string
  submittedAt?: string
}
export interface AssessmentDimensionVO {
  dimension: string
  rawFirstScore: number
  minimumScore: number
  maximumScore: number
  firstPercent: number
  secondPercent: number
  preferredPole: string
  balanced: boolean
  weakPreference: boolean
}
export interface GrowthPlanVO {
  id: string
  context: string
  action: string
  suggestedCycle: string
  observationTarget: string
  reviewPrompt: string
  basis: string
  ruleId?: string
  graphVersion?: string
  sourceIds: string[]
  evidenceLevel: string
  templateVersion?: string
  templateContentHash?: string
}
export interface AssessmentReportVO {
  reportVersion: string
  disclaimer: string
  preferenceSummary: string[]
  strengths: string[]
  developmentDirections: string[]
  workPreferences: string[]
  careerSuggestions: string[]
  growthSuggestions: string[]
  sourceBasis: string
  limitations: string
  graphExplanation?: GraphExplanationVO
  growthPlans?: GrowthPlanVO[]
}
export interface AssessmentResultVO {
  id: number
  userId: number
  scaleName: string
  scaleVersion: string
  scoringVersion: string
  contentHash: string
  typeCode: string
  submittedAt: string
  dimensions: AssessmentDimensionVO[]
  report: AssessmentReportVO
  scale: ScaleDefinitionVO
}
export interface AssessmentHistoryVO {
  id: number
  scaleName: string
  scaleVersion: string
  scoringVersion: string
  contentHash: string
  typeCode: string
  submittedAt: string
}
export interface AssessmentQueryReqDTO {
  pageNum: number
  pageSize: number
}
export interface AssessmentComparisonVO {
  comparable: boolean
  explanation: string
  first: AssessmentResultVO
  second: AssessmentResultVO
}
