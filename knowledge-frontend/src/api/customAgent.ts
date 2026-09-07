/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 *
 * 自定义 Agent 接口封装
 * 对应后端 CustomAgentController（/custom-agent）：定义管理 / 上传 / 单步流式对话 / 多步异步执行 / 运行记录。
 *
 * v2 优化：SSE 解析逻辑统一使用 @/utils/sse 模块，消除重复代码。
 */

import { request } from '@/api/request'
import { getToken } from '@/utils/auth'
import { fetchStream } from '@/utils/sse'

/* ==================== 定义管理 ==================== */

/** 分页查询定义（自己的全部 + 租户内已发布） */
export function pageAgentDefApi(params: {
  current?: number
  size?: number
}): Promise<ApiResponse<PageResult<AgentDefinitionVo>>> {
  return request<PageResult<AgentDefinitionVo>>({ url: '/custom-agent/page', method: 'get', params })
}

/** 定义详情 */
export function getAgentDefApi(id: number | string): Promise<ApiResponse<AgentDefinitionVo>> {
  return request<AgentDefinitionVo>({ url: `/custom-agent/${id}`, method: 'get' })
}

/** 创建定义，返回定义ID */
export function createAgentDefApi(data: AgentDefinitionRequest): Promise<ApiResponse<number | string>> {
  return request<number | string>({ url: '/custom-agent', method: 'post', data })
}

/** 编辑定义，返回定义ID */
export function updateAgentDefApi(id: number | string, data: AgentDefinitionRequest): Promise<ApiResponse<number | string>> {
  return request<number | string>({ url: `/custom-agent/${id}`, method: 'put', data })
}

/** 发布定义 */
export function publishAgentDefApi(id: number | string): Promise<ApiResponse> {
  return request({ url: `/custom-agent/${id}/publish`, method: 'post' })
}

/** 删除定义（软删） */
export function deleteAgentDefApi(id: number | string): Promise<ApiResponse> {
  return request({ url: `/custom-agent/${id}`, method: 'delete' })
}

/* ==================== 日志/文档上传 ==================== */

/**
 * 上传日志/文档文件（≤100MB，文本类后缀），返回 fileRef 供运行使用。
 * 上传接口单独设置 5 分钟超时（大文件上传可能超过全局 30s 默认超时）。
 */
export function uploadLogApi(file: File): Promise<ApiResponse<UploadLogResult>> {
  const form = new FormData()
  form.append('file', file)
  return request<UploadLogResult>({ url: '/custom-agent/upload', method: 'post', data: form, timeout: 300000 })
}

/* ==================== 运行 ==================== */

/** 多步异步执行（立即返回 runId，再订阅 SSE 进度） */
export function runAgentApi(agentId: number | string, data: ChatStartRequest): Promise<ApiResponse<number | string>> {
  return request<number | string>({ url: `/custom-agent/${agentId}/run`, method: 'post', data })
}

/** 分页查询当前用户运行记录（agentId 可选，按 Agent 过滤历史） */
export function pageAgentRunApi(params: {
  current?: number
  size?: number
  agentId?: number | string
}): Promise<ApiResponse<PageResult<AgentRunVo>>> {
  return request<PageResult<AgentRunVo>>({ url: '/custom-agent/runs/page', method: 'get', params })
}

/** 运行记录详情 */
export function getAgentRunApi(runId: number | string): Promise<ApiResponse<AgentRunVo>> {
  return request<AgentRunVo>({ url: `/custom-agent/runs/${runId}`, method: 'get' })
}

/**
 * 单步流式对话（SSE，带断线重连）。
 * <p>事件序列：session（会话ID，首轮回传）→ status（generating）→ token×N → done（runId）/ error。
 * @returns AbortController，可调用 .abort() 中断流
 */
export function chatCustomAgent(
  agentId: number | string,
  body: ChatStartRequest,
  cb: {
    onStatus?: (stage: string) => void
    onToken?: (token: string) => void
    onSession?: (sessionId: number | string) => void
    onDone?: (runId: number | string) => void
    onError?: (err: string) => void
  }
): AbortController {
  const token = getToken()
  const base = import.meta.env.VITE_APP_BASE_API
  return fetchStream(
    `${base}/custom-agent/${agentId}/chat`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify(body)
    },
    (event, data) => {
      switch (event) {
        case 'status':
          cb.onStatus?.(data)
          break
        case 'token':
          cb.onToken?.(data)
          break
        case 'session':
          cb.onSession?.(data)
          break
        case 'done':
          cb.onDone?.(data)
          break
        case 'error':
          cb.onError?.(data)
          break
      }
    },
    cb.onError,
    { maxRetries: 3, initialDelayMs: 1000, maxDelayMs: 10000 }
  )
}

/**
 * 多步执行 SSE 进度流（GET，带断线重连）。
 * <p>事件序列：progress（每 1.5s 推送运行快照）→ complete（终态后推送并关闭）/ error。
 * @returns AbortController，可调用 .abort() 中断订阅
 */
export function subscribeAgentRunStream(
  runId: number | string,
  cb: {
    onProgress: (vo: AgentRunVo) => void
    onComplete: (vo: AgentRunVo) => void
    onError?: (err: string) => void
  }
): AbortController {
  const token = getToken()
  const base = import.meta.env.VITE_APP_BASE_API
  return fetchStream(
    `${base}/custom-agent/runs/${runId}/stream`,
    {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    },
    (event, data) => {
      if (event === 'progress') {
        try { cb.onProgress(JSON.parse(data)) } catch { /* 忽略解析异常 */ }
      } else if (event === 'complete') {
        try { cb.onComplete(JSON.parse(data)) } catch { /* 忽略解析异常 */ }
      } else if (event === 'error') {
        cb.onError?.(data)
      }
    },
    cb.onError,
    { maxRetries: 3, initialDelayMs: 1000, maxDelayMs: 10000 }
  )
}
