/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

import { request } from '@/api/request'

/** 操作日志分页查询 */
export function pageOperLogApi(params: {
  pageNo?: number
  pageSize?: number
  title?: string
  businessType?: number
  operUser?: string
  status?: number
}): Promise<ApiResponse<PageResult<OperLog>>> {
  return request<PageResult<OperLog>>({ url: '/system/oper-log', method: 'get', params })
}
