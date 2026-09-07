/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

import { request } from '@/api/request'

/** 文档分页查询 */
export function pageDocumentApi(params: {
  pageNo?: number
  pageSize?: number
  kbId?: number | string
  status?: number
  originalName?: string
}): Promise<ApiResponse<PageResult<KbDocument>>> {
  return request<PageResult<KbDocument>>({ url: '/kb/documents', method: 'get', params })
}

/** 文档详情 */
export function getDocumentApi(id: number | string): Promise<ApiResponse<KbDocument>> {
  return request<KbDocument>({ url: `/kb/documents/${id}`, method: 'get' })
}

/** 上传文档（multipart） */
export function uploadDocumentApi(kbId: number | string, file: File): Promise<ApiResponse<any>> {
  const formData = new FormData()
  formData.append('file', file)
  return request<any>({
    url: '/kb/documents/upload',
    method: 'post',
    params: { kbId },
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

/** 删除文档 */
export function removeDocumentApi(id: number | string): Promise<ApiResponse> {
  return request({ url: `/kb/documents/${id}`, method: 'delete' })
}

/** 触发文档解析 */
export function triggerParseApi(id: number | string): Promise<ApiResponse<number>> {
  return request<number>({ url: `/kb/documents/${id}/parse`, method: 'post' })
}
