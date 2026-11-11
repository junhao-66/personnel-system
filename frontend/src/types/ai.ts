export interface AiAnswerVO {
  mode: 'MOCK' | 'REMOTE' | 'RULE_FALLBACK' | 'REFUSED'
  reason: string
  promptVersion: string
  contextVersion: string
  resultId?: number
  paragraphs: { evidenceId: string; text: string; basis: string }[]
  notice: string
}
export interface AiStatusVO {
  configuredMode: 'MOCK' | 'REMOTE' | 'OFF'
  externalEnabled: boolean
  remoteConfigured: boolean
  notice: string
}
