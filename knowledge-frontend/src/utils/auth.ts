/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 *
 * Token 管理：基于 localStorage 的存取（双 Token 机制）
 * - access token：短期有效（默认 2h），用于 API 鉴权
 * - refresh token：长期有效（默认 7d），仅用于静默刷新 access token
 *
 * 单一职责，供 user store 与 axios 拦截器共用，避免 token 存储分散。
 */

const TOKEN_KEY = 'knowledge_token'
const REFRESH_TOKEN_KEY = 'knowledge_refresh_token'

/* ==================== Access Token ==================== */

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/* ==================== Refresh Token ==================== */

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

export function removeRefreshToken(): void {
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

/* ==================== 全量清理 ==================== */

/** 清除全部 token（access + refresh），用于登出或强制重新登录 */
export function clearAllTokens(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}
