/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

import { request } from '@/api/request'

/** 会话分页列表 */
export function pageSessionApi(params: {
  kbId?: number | string
  pageNo?: number
  pageSize?: number
}): Promise<ApiResponse<PageResult<ChatSessionVo>>> {
  return request<PageResult<ChatSessionVo>>({ url: '/chat/sessions', method: 'get', params })
}

/** 新建会话 */
export function createSessionApi(data: { kbId: number | string; title?: string }): Promise<ApiResponse<number | string>> {
  return request<number | string>({ url: '/chat/sessions', method: 'post', data })
}

/** 重命名会话 */
export function renameSessionApi(id: number | string, title: string): Promise<ApiResponse> {
  return request({ url: `/chat/sessions/${id}`, method: 'put', params: { title } })
}

/** 删除会话 */
export function removeSessionApi(id: number | string): Promise<ApiResponse> {
  return request({ url: `/chat/sessions/${id}`, method: 'delete' })
}

/** 会话消息列表 */
export function listMessageApi(sessionId: number | string): Promise<ApiResponse<ChatMessageVo[]>> {
  return request<ChatMessageVo[]>({ url: `/chat/sessions/${sessionId}/messages`, method: 'get' })
}

/** 同步问答（非流式，支持普通/RAG两种模式）；LLM 生成耗时较长，单独放宽超时 */
export function askApi(data: {
  question: string
  kbId?: number | string
  sessionId?: number | string
  useRag?: boolean
}): Promise<ApiResponse<{ answer: string; sources: RetrievalResult[]; sessionId: number | string }>> {
  return request({ url: '/ai/ask', method: 'post', data, timeout: 120000 })
}
