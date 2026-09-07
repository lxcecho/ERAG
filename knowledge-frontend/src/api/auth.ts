/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

import { request } from '@/api/request'

/**
 * 认证相关接口
 * 对应后端 /auth/** 端点（认证模块开发后接入）
 */

export function loginApi(data: LoginForm): Promise<ApiResponse<LoginResult>> {
  return request<LoginResult>({ url: '/auth/login', method: 'post', data })
}

export function logoutApi(): Promise<ApiResponse> {
  return request({ url: '/auth/logout', method: 'post' })
}

export function getUserInfoApi(): Promise<ApiResponse<UserInfo>> {
  return request<UserInfo>({ url: '/auth/info', method: 'get' })
}

/** 更新账户资料（昵称/头像/邮箱/手机） */
export function updateProfileApi(data: ProfileRequest): Promise<ApiResponse<UserInfo>> {
  return request<UserInfo>({ url: '/auth/profile', method: 'put', data })
}

/** 修改密码（校验旧密码，新密码 ≥ 6 位） */
export function changePasswordApi(data: PasswordRequest): Promise<ApiResponse> {
  return request({ url: '/auth/password', method: 'put', data })
}

/** 上传头像（multipart ≤2MB，png/jpg/jpeg/webp/gif），返回 URL 路径 */
export function uploadAvatarApi(file: File): Promise<ApiResponse<string>> {
  const form = new FormData()
  form.append('file', file)
  return request<string>({ url: '/auth/avatar', method: 'post', data: form })
}

/** 我的任务聚合分页（Agent 任务 + 自定义 Agent 运行，按时间倒序） */
export function myTasksApi(params: {
  pageNum?: number
  pageSize?: number
}): Promise<ApiResponse<PageResult<MyTaskVo>>> {
  return request<PageResult<MyTaskVo>>({ url: '/auth/my-tasks', method: 'get', params })
}
