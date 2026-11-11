export interface GrowthTemplateCreateReqDTO {
  version: string
  title: string
  suggestedCycle: string
  observationTarget: string
  reviewPrompt: string
  fallbackContext: string
  fallbackAction: string
}
export interface GrowthTemplateVO extends GrowthTemplateCreateReqDTO {
  id: number | null
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  contentHash?: string
}
