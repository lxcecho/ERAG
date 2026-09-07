/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

import { request } from '@/api/request'

/** 知识库分页查询 */
export function pageKbApi(params: {
  pageNo?: number
  pageSize?: number
  name?: string
  status?: number
}): Promise<ApiResponse<PageResult<KbItem>>> {
  return request<PageResult<KbItem>>({ url: '/kb/bases', method: 'get', params })
}

/** 新建知识库 */
export function createKbApi(data: { name: string; description?: string }): Promise<ApiResponse<number | string>> {
  return request<number | string>({ url: '/kb/bases', method: 'post', data })
}

/** 修改知识库 */
export function updateKbApi(id: number | string, data: { name?: string; description?: string }): Promise<ApiResponse> {
  return request({ url: `/kb/bases/${id}`, method: 'put', data })
}

/** 删除知识库 */
export function removeKbApi(id: number | string): Promise<ApiResponse> {
  return request({ url: `/kb/bases/${id}`, method: 'delete' })
}

/** 成员分页列表 */
export function pageMemberApi(kbId: number | string, params: { pageNo?: number; pageSize?: number }): Promise<ApiResponse<PageResult<KbMember>>> {
  return request<PageResult<KbMember>>({ url: `/kb/bases/${kbId}/members`, method: 'get', params })
}

/** 添加成员 */
export function addMemberApi(kbId: number | string, data: { userId: number | string; role: string }): Promise<ApiResponse> {
  return request({ url: `/kb/bases/${kbId}/members`, method: 'post', data })
}

/** 修改成员角色 */
export function updateMemberRoleApi(kbId: number | string, memberId: number | string, role: string): Promise<ApiResponse> {
  return request({ url: `/kb/bases/${kbId}/members/${memberId}`, method: 'put', params: { role } })
}

/** 移除成员 */
export function removeMemberApi(kbId: number | string, memberId: number | string): Promise<ApiResponse> {
  return request({ url: `/kb/bases/${kbId}/members/${memberId}`, method: 'delete' })
}
