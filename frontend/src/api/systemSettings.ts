import request from '@/utils/request'
import type { Result } from '@/types/result'
import type { SystemSettingsVO, SystemSettingsUpdateReqDTO } from '@/types/systemSettings'

export const getSystemSettings = () => request.get<Result<SystemSettingsVO>>('/system/settings')
export const getAdminSystemSettings = () =>
  request.get<Result<SystemSettingsVO>>('/admin/system-settings')
export const updateSystemSettings = (data: SystemSettingsUpdateReqDTO) =>
  request.put<Result<SystemSettingsVO>>('/admin/system-settings', data)
