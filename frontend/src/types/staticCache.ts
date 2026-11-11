export interface StaticCacheStatusVO {
  enabled: boolean
  state: 'DISABLED' | 'MYSQL_FALLBACK' | 'READY'
  ttlSeconds: number
  failureCount: number
  catalogs: string[]
  notice: string
}
