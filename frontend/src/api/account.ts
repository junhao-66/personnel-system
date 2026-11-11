import request from '@/utils/request'
import type { Result } from '@/types/result'
import type { UserVO } from '@/types/user'
import type { AccountContactReqDTO, AccountPasswordReqDTO } from '@/types/account'

export const updateOwnContact = (data: AccountContactReqDTO) =>
  request.put<Result<UserVO>>('/user/me/contact', data)
export const changeOwnPassword = (data: AccountPasswordReqDTO) =>
  request.put<Result<void>>('/user/me/password', data)
