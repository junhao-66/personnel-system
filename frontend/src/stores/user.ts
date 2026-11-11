import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { loginUser } from '@/api/user'
import { getCurrentUser } from '@/api/session'
import type { UserLoginDTO, UserVO } from '@/types/user'
import { clearSession, readUser } from '@/utils/session'
export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('accessToken') ?? '')
  const user = ref<UserVO | null>(readUser())
  const isLoggedIn = computed(() => Boolean(token.value))
  const login = async (data: UserLoginDTO) => {
    const result = await loginUser(data)
    token.value = result.data.data.accessToken
    user.value = result.data.data.user
    localStorage.setItem('accessToken', token.value)
    localStorage.setItem('currentUser', JSON.stringify(user.value))
  }
  const logout = () => {
    token.value = ''
    user.value = null
    clearSession()
  }
  const refreshUser = async () => {
    const response = await getCurrentUser()
    user.value = response.data.data
    localStorage.setItem('currentUser', JSON.stringify(user.value))
  }
  return { token, user, isLoggedIn, login, logout, refreshUser }
})
