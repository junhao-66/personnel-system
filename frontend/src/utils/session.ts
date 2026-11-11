import type { UserVO } from '@/types/user'

export const SESSION_EXPIRED_EVENT = 'personnel-session-expired'

export function clearSession() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('currentUser')
}

export function readUser(): UserVO | null {
  try {
    const value: unknown = JSON.parse(localStorage.getItem('currentUser') ?? 'null')
    if (!value || typeof value !== 'object') return null
    const user = value as Partial<UserVO>
    if (
      typeof user.id !== 'number' ||
      typeof user.userName !== 'string' ||
      !['ADMIN', 'DEPARTMENT_MANAGER', 'EMPLOYEE'].includes(user.roleCode ?? '')
    )
      return null
    return user as UserVO
  } catch {
    clearSession()
    return null
  }
}
