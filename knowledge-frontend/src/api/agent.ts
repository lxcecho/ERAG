/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

import { request } from '@/api/request'
import { getToken } from '@/utils/auth'

/**
 * Agent 工作流接口封装
 * 对应后端 AgentController（/agent）：启动/详情/分页/SSE 进度流。
 * - 分页用 current/size 参数（后端 IPage 约定）
 * - SSE 因 EventSource 无法设置请求头，token 通过 query 参数传递
 *   （后端 jwt.allow-query-token-paths 白名单放行 /agent/{id}/stream）
 */

/** 分页查询当前用户的 Agent 任务列表 */
export function pageAgentApi(params: {
  current?: number
  size?: number
}): Promise<ApiResponse<PageResult<AgentTaskVo>>> {
  return request<PageResult<AgentTaskVo>>({ url: '/agent/page', method: 'get', params })
}

/** Agent 任务详情（含步骤 steps 与产物 artifacts） */
export function getAgentApi(id: number | string): Promise<ApiResponse<AgentTaskVo>> {
  return request<AgentTaskVo>({ url: `/agent/${id}`, method: 'get' })
}

/** 启动 Agent 任务（异步，立即返回任务ID） */
export function startAgentApi(data: AgentStartRequest): Promise<ApiResponse<number | string>> {
  return request<number | string>({ url: '/agent/start', method: 'post', data })
}

/**
 * 订阅 Agent 任务 SSE 进度流。
 * <p>事件：progress（每 1.5s 推送任务快照）→ complete（终态后推送并关闭）。
 * @returns EventSource 实例，调用方可在卸载时 .close() 释放
 */
export function subscribeAgentStream(
  taskId: number | string,
  handlers: {
    onProgress: (vo: AgentTaskVo) => void
    onComplete: (vo: AgentTaskVo) => void
    onError?: (err: Event) => void
  }
): EventSource {
  const token = getToken() || ''
  const base = import.meta.env.VITE_APP_BASE_API || ''
  const url = `${base}/agent/${taskId}/stream?token=${encodeURIComponent(token)}`
  const es = new EventSource(url)

  es.addEventListener('progress', (e: MessageEvent) => {
    try {
      handlers.onProgress(JSON.parse(e.data))
    } catch {
      /* 忽略解析异常 */
    }
  })
  es.addEventListener('complete', (e: MessageEvent) => {
    try {
      handlers.onComplete(JSON.parse(e.data))
    } catch {
      /* 忽略解析异常 */
    }
    es.close()
  })
  es.onerror = (err) => {
    handlers.onError?.(err)
    es.close()
  }
  return es
}
