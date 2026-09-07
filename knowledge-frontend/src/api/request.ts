/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 *
 * Axios 封装（双 Token 自动刷新）
 * - 请求拦截：自动注入 Bearer Token
 * - 响应拦截：解包 ApiResponse，统一处理业务码
 * - 401 自动刷新：access token 过期时，自动用 refresh token 静默换取新 token，对业务层透明
 * - 刷新失败才弹窗提示重新登录
 */

import axios, { type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getToken, setToken, getRefreshToken, clearAllTokens } from '@/utils/auth'
import router from '@/router'

const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  // AI 场景接口可能因 LLM 生成/后端重启而较慢，默认放宽到 60s
  timeout: 60000
})

// ==================== Token 自动刷新状态 ====================

/** 是否正在刷新 token（防止并发请求重复刷新） */
let isRefreshing = false
/** 等待 token 刷新完成的请求队列 */
let pendingRequests: Array<(token: string) => void> = []

/** 处理等待队列：刷新成功后用新 token 重试所有挂起请求 */
function processPendingRequests(newToken: string) {
  pendingRequests.forEach(cb => cb(newToken))
  pendingRequests = []
}

/**
 * 尝试用 refresh token 静默刷新 access token。
 * @returns true 刷新成功，false 刷新失败（需重新登录）
 */
async function tryRefreshToken(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false

  try {
    // 直接用 axios（避免拦截器循环）调用刷新接口
    const resp = await axios.post(
      `${import.meta.env.VITE_APP_BASE_API}/auth/refresh`,
      { refreshToken },
      { timeout: 10000 }
    )
    const data = resp.data
    if (data.code === 200 && data.data?.token) {
      setToken(data.data.token)
      // refresh token 也更新（旋转机制）
      if (data.data.refreshToken) {
        const { setRefreshToken } = await import('@/utils/auth')
        setRefreshToken(data.data.refreshToken)
      }
      return true
    }
    return false
  } catch {
    return false
  }
}

// ==================== 请求拦截器 ====================

service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`)
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ==================== 响应拦截器 ====================

let isReloginShown = false

/** 401 失效处理：弹窗提示并跳转登录页（带 redirect 回跳） */
function handleUnauthorized() {
  if (isReloginShown) return
  isReloginShown = true
  ElMessageBox.confirm('登录状态已过期，请重新登录', '系统提示', {
    confirmButtonText: '重新登录',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      clearAllTokens()
      router.push(`/login?redirect=${router.currentRoute.value.fullPath}`)
    })
    .catch(() => {})
    .finally(() => {
      isReloginShown = false
    })
}

service.interceptors.response.use(
  (response: AxiosResponse) => {
    // 文件下载（二进制流）直接返回原始响应
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data as ApiResponse
    if (res.code === 200) {
      return res
    }
    if (res.code === 401) {
      // 401 时尝试自动刷新 token（仅非刷新接口本身）
      const originalConfig = response.config as InternalAxiosRequestConfig & { _retried?: boolean }
      if (!originalConfig._retried && !originalConfig.url?.includes('/auth/refresh')) {
        if (!isRefreshing) {
          isRefreshing = true
          return tryRefreshToken().then(success => {
            isRefreshing = false
            if (success) {
              // 刷新成功：重试当前请求 + 队列中所有挂起请求
              const newToken = getToken()!
              processPendingRequests(newToken)
              originalConfig.headers.set('Authorization', `Bearer ${newToken}`)
              originalConfig._retried = true
              return service.request(originalConfig)
            } else {
              // 刷新失败：弹窗重新登录
              handleUnauthorized()
              return Promise.reject(new Error(res.message || '未授权'))
            }
          })
        } else {
          // 已有刷新请求在进行中：加入等待队列
          return new Promise(resolve => {
            pendingRequests.push((newToken: string) => {
              originalConfig.headers.set('Authorization', `Bearer ${newToken}`)
              originalConfig._retried = true
              resolve(service.request(originalConfig))
            })
          })
        }
      }
      // 已重试过仍 401，或刷新接口本身 401：直接弹窗
      handleUnauthorized()
      return Promise.reject(new Error(res.message || '未授权'))
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || 'Error'))
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      // 网络层 401（非业务码）也尝试刷新
      const originalConfig = error.config as InternalAxiosRequestConfig & { _retried?: boolean }
      if (!originalConfig._retried && !originalConfig.url?.includes('/auth/refresh')) {
        if (!isRefreshing) {
          isRefreshing = true
          return tryRefreshToken().then(success => {
            isRefreshing = false
            if (success) {
              const newToken = getToken()!
              processPendingRequests(newToken)
              originalConfig.headers.set('Authorization', `Bearer ${newToken}`)
              originalConfig._retried = true
              return service.request(originalConfig)
            } else {
              handleUnauthorized()
              return Promise.reject(error)
            }
          })
        } else {
          return new Promise(resolve => {
            pendingRequests.push((newToken: string) => {
              originalConfig.headers.set('Authorization', `Bearer ${newToken}`)
              originalConfig._retried = true
              resolve(service.request(originalConfig))
            })
          })
        }
      }
      handleUnauthorized()
    } else {
      ElMessage.error(error.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

/** 通用请求方法，泛型 T 为 data 字段类型 */
export function request<T = any>(config: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return service.request(config) as unknown as Promise<ApiResponse<T>>
}

export default service
