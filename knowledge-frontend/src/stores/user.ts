/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 *
 * 用户状态：token、用户信息、角色与权限（双 Token 机制）
 * token 持久化在 localStorage（utils/auth），刷新页面后从 localStorage 恢复。
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { loginApi, logoutApi, getUserInfoApi } from '@/api/auth'
import { getToken, setToken, setRefreshToken, clearAllTokens } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken() || '')
  const userInfo = ref<UserInfo | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])

  /** 登录：调用接口换取双 token 并持久化 */
  async function login(loginForm: LoginForm) {
    const res = await loginApi(loginForm)
    token.value = res.data.token
    setToken(res.data.token)
    // 持久化 refresh token（用于 access token 过期后静默刷新）
    if (res.data.refreshToken) {
      setRefreshToken(res.data.refreshToken)
    }
    return res.data
  }

  /** 获取当前用户信息（角色 + 权限） */
  async function fetchUserInfo() {
    const res = await getUserInfoApi()
    userInfo.value = res.data
    roles.value = res.data.roles || []
    permissions.value = res.data.permissions || []
    return res.data
  }

  /** 登出：通知后端销毁 token，并清空本地状态 */
  async function logout() {
    try {
      await logoutApi()
    } finally {
      resetState()
    }
  }

  /** 重置全部状态（用于 token 失效强制登出） */
  function resetState() {
    token.value = ''
    userInfo.value = null
    roles.value = []
    permissions.value = []
    clearAllTokens()
  }

  return { token, userInfo, roles, permissions, login, fetchUserInfo, logout, resetState }
})
