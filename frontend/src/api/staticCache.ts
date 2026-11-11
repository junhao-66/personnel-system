import request from '@/utils/request'
import type { Result } from '@/types/result'
import type { StaticCacheStatusVO } from '@/types/staticCache'
export const getStaticCache = () => request.get<Result<StaticCacheStatusVO>>('/admin/static-cache')
export const refreshStaticCache = () =>
  request.post<Result<StaticCacheStatusVO>>('/admin/static-cache/refresh')
