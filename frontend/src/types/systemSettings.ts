export interface SystemSettingsVO {
  assessmentEnabled: boolean
  assessmentNotice: string
  announcementEnabled: boolean
  announcementTitle: string
  announcementContent: string
  revision: number
  updatedAt?: string
}
export type SystemSettingsUpdateReqDTO = Omit<SystemSettingsVO, 'updatedAt'>
