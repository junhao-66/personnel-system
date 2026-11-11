import request from '@/utils/request'
import type { Result } from '@/types/result'
import type { UserVO } from '@/types/user'

export function getCurrentUser() {
  return request.get<Result<UserVO>>('/user/me')
}
