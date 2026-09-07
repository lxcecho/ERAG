/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

import { request } from '@/api/request'

/** AI 调用日志分页查询（支持模块/类型/模型/状态/用户/时间范围过滤） */
export function pageAiCallLogApi(params: AiCallLogQuery): Promise<ApiResponse<PageResult<AiCallLog>>> {
  return request<PageResult<AiCallLog>>({ url: '/ai-call-log/page', method: 'get', params })
}

/** AI 调用统计（概览 + 按模型/用户/日期排行） */
export function statsAiCallApi(params: AiCallStatsQuery): Promise<ApiResponse<AiCallStatsVo>> {
  return request<AiCallStatsVo>({ url: '/ai-call-log/stats', method: 'get', params })
}
