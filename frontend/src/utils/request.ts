import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearSession, SESSION_EXPIRED_EVENT } from '@/utils/session'

const request = axios.create({ baseURL: '/api', timeout: 10000 })
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
request.interceptors.response.use(
  async (response) => {
    if (response.config.responseType === 'blob' && response.data instanceof Blob) {
      if (String(response.headers['content-type']).toLowerCase().startsWith('application/pdf'))
        return response
      let message = '服务器未返回有效PDF，请稍后重试'
      try {
        message = JSON.parse(await response.data.text()).msg || message
      } catch {
        /* Non-JSON error body uses the fallback message. */
      }
      ElMessage.error(message)
      return Promise.reject(new Error(message))
    }
    if (response.data?.code !== 200) {
      const message = response.data?.msg || '请求失败，请稍后重试'
      ElMessage.error(message)
      return Promise.reject(new Error(message))
    }
    return response
  },
  async (error: unknown) => {
    if (axios.isAxiosError(error)) {
      if (error.response?.data instanceof Blob) {
        try {
          error.response.data = JSON.parse(await error.response.data.text())
        } catch {
          /* Keep standard HTTP error handling for non-JSON bodies. */
        }
      }
      if (error.response?.status === 401) {
        clearSession()
        window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
      }
      const retryAfter = Number(error.response?.headers['retry-after'])
      const rateLimitMessage =
        Number.isFinite(retryAfter) && retryAfter > 0
          ? `操作过于频繁，请在${Math.ceil(retryAfter)}秒后重试`
          : error.response?.data?.msg || '操作过于频繁，请稍后重试'
      const message =
        error.response?.status === 429
          ? rateLimitMessage
          : error.response?.status === 403
            ? '当前账号没有此操作权限'
            : error.response?.status === 401
              ? '登录已失效，请重新登录'
              : error.response?.data?.msg ||
                (error.response
                  ? '请求失败，请检查输入或稍后重试'
                  : '无法连接服务器，请检查后端服务')
      ElMessage.error(message)
    } else {
      ElMessage.error('请求失败，请稍后重试')
    }
    return Promise.reject(error)
  },
)
export default request
