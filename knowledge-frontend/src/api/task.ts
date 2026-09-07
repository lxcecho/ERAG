/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

import { request } from '@/api/request'

/** 解析任务分页查询 */
export function pageTaskApi(params: {
  pageNo?: number
  pageSize?: number
  kbId?: number
  status?: number
}): Promise<ApiResponse<PageResult<ParseTask>>> {
  return request<PageResult<ParseTask>>({ url: '/kb/task', method: 'get', params })
}

/** 任务详情 */
export function getTaskApi(id: number): Promise<ApiResponse<any>> {
  return request<any>({ url: `/kb/task/${id}`, method: 'get' })
}

/** 重试失败任务 */
export function retryTaskApi(id: number): Promise<ApiResponse> {
  return request({ url: `/kb/task/${id}/retry`, method: 'post' })
}
