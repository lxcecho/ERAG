/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 *
 * SSE 流式客户端（统一模块）
 * - RAG 流式问答（POST /ai/stream）
 * - 自定义 Agent 单步/多步流式（POST/GET /custom-agent/...）
 *
 * 后端端点为 POST 且需 Authorization 头，浏览器原生 EventSource 不支持，
 * 故采用 fetch + ReadableStream 手动解析 SSE 事件流。
 *
 * v2 优化：
 * - 断线自动重连（指数退避，最多 3 次）
 * - 统一 SSE 解析逻辑，消除 customAgent.ts 中的重复代码
 * - 重连时保留原始请求体，对后端透明
 */

import { getToken } from '@/utils/auth'

/* ==================== 类型定义 ==================== */

export interface StreamCallbacks {
  onSources?: (sources: RetrievalResult[]) => void
  onToken?: (token: string) => void
  /** 阶段状态：retrieving（正在检索知识库）/ generating（正在生成回答） */
  onStatus?: (stage: 'retrieving' | 'generating' | string) => void
  /** 会话ID：后端 Long 经 Jackson 序列化为 String，保留原始字符串避免 Number 精度丢失 */
  onDone?: (sessionId: number | string) => void
  /**
   * 引用修复：后端流式结束后校验发现回答缺 [编号] 引用标注，触发二次修复后
   * 推送修正后的完整回答，前端应整体替换当前消息内容
   */
  onCorrect?: (answer: string) => void
  /** 自定义 Agent 的 session 事件（首轮回传会话ID） */
  onSession?: (sessionId: number | string) => void
  /** 自定义 Agent 多步执行进度 */
  onProgress?: (data: string) => void
  /** 自定义 Agent 多步执行完成 */
  onComplete?: (data: string) => void
  onError?: (err: string) => void
}

/** SSE 重连配置 */
interface ReconnectConfig {
  /** 最大重试次数，默认 3 */
  maxRetries?: number
  /** 初始延迟（毫秒），默认 1000 */
  initialDelayMs?: number
  /** 最大延迟（毫秒），默认 10000 */
  maxDelayMs?: number
}

const DEFAULT_RECONNECT: Required<ReconnectConfig> = {
  maxRetries: 3,
  initialDelayMs: 1000,
  maxDelayMs: 10000
}

/* ==================== 核心 SSE 解析 ==================== */

/** 解析单个 SSE 事件块（event:xxx\ndata:yyy） */
export function parseEventBlock(block: string, onEvent: (event: string, data: string) => void) {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5))
    }
  }
  const data = dataLines.join('\n').trim()
  if (!data) return
  onEvent(event, data)
}

/** 基于 fetch + ReadableStream 的 SSE 事件流读取（支持自定义请求头） */
async function readSSEStream(
  resp: Response,
  onEvent: (event: string, data: string) => void
): Promise<void> {
  const reader = resp.body!.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  // eslint-disable-next-line no-constant-condition
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let sep: number
    while ((sep = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      parseEventBlock(block, onEvent)
    }
  }
  // 处理缓冲区残余
  if (buffer.trim()) {
    parseEventBlock(buffer, onEvent)
  }
}

/* ==================== 通用 fetch SSE（带重连） ==================== */

/**
 * 通用 fetch SSE 流式请求（支持重连）。
 *
 * @param url     请求地址
 * @param init    fetch 请求配置
 * @param onEvent SSE 事件回调 (event, data)
 * @param onError 错误回调
 * @param reconnect 重连配置（null 则不重连）
 * @returns AbortController，可调用 .abort() 中断流
 */
export function fetchStream(
  url: string,
  init: RequestInit,
  onEvent: (event: string, data: string) => void,
  onError?: (err: string) => void,
  reconnect: ReconnectConfig | null = null
): AbortController {
  const controller = new AbortController()
  const cfg = reconnect ?? DEFAULT_RECONNECT

  const attempt = async (retryCount: number) => {
    try {
      const resp = await fetch(url, { ...init, signal: controller.signal })
      if (!resp.ok || !resp.body) {
        onError?.(`请求失败：${resp.status}`)
        return
      }
      await readSSEStream(resp, onEvent)
    } catch (e: any) {
      if (e?.name === 'AbortError') return // 主动中断，不重连
      // 重连逻辑：仅网络异常触发重连，业务错误不重连
      if (reconnect !== null && retryCount < cfg.maxRetries) {
        const delay = Math.min(cfg.initialDelayMs * Math.pow(2, retryCount), cfg.maxDelayMs)
        console.warn(`[SSE] 连接异常，${delay}ms 后第 ${retryCount + 1} 次重连: ${e?.message}`)
        await new Promise(r => setTimeout(r, delay))
        if (!controller.signal.aborted) {
          await attempt(retryCount + 1)
        }
      } else {
        onError?.(e?.message || '流式连接异常')
      }
    }
  }

  attempt(0)
  return controller
}

/* ==================== RAG 流式问答 ==================== */

/**
 * 发起 RAG 流式问答（带断线重连）。
 *
 * @param body    请求体 { question, kbId?, sessionId?, useRag? }
 * @param cb      事件回调
 * @param reconnect 重连配置（默认 3 次指数退避；传 null 禁用重连）
 * @returns AbortController，可调用 .abort() 中断流
 */
export async function streamChat(
  body: { question: string; kbId?: number | string; sessionId?: number | string; useRag?: boolean },
  cb: StreamCallbacks,
  reconnect: ReconnectConfig | null = DEFAULT_RECONNECT
): Promise<AbortController> {
  const token = getToken()
  const base = import.meta.env.VITE_APP_BASE_API

  return fetchStream(
    `${base}/ai/stream`,
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
        case 'sources':
          try { cb.onSources?.(JSON.parse(data)) } catch { cb.onSources?.([]) }
          break
        case 'token':
          cb.onToken?.(data)
          break
        case 'done':
          cb.onDone?.(data)
          break
        case 'correct':
          cb.onCorrect?.(data)
          break
        case 'error':
          cb.onError?.(data)
          break
      }
    },
    cb.onError,
    reconnect
  )
}
