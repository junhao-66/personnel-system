import request from '@/utils/request'
import type { Result } from '@/types/result'

export function getHealth() {
  return request.get<Result<{ status: string; service: string }>>('/system/health')
}
