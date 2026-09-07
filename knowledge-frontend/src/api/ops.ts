/**
 * @since: 2026-08-02
 * @author: lxcechoo@gmail.com
 */

import { request } from '@/api/request'

/* ==================== 运维看板 ==================== */

/** 7 指标聚合看板（卡片 + 调用趋势 + 费用趋势 + 资源延迟） */
export function getDashboardApi(): Promise<ApiResponse<OpsDashboardVo>> {
  return request<OpsDashboardVo>({ url: '/ops/dashboard', method: 'get' })
}

/* ==================== 基础设施指标 ==================== */

/** Milvus/ES/MQ 概览 + 时序 */
export function getInfraMetricsApi(params: {
  start?: string
  end?: string
}): Promise<ApiResponse<InfraOverview>> {
  return request<InfraOverview>({ url: '/ops/metrics/infra', method: 'get', params })
}

/** Agent 耗时统计 */
export function getAgentMetricsApi(params: {
  start?: string
  end?: string
}): Promise<ApiResponse<AgentStatsVo>> {
  return request<AgentStatsVo>({ url: '/ops/metrics/agent', method: 'get', params })
}

/** Workflow 成功率统计 */
export function getWorkflowMetricsApi(params: {
  start?: string
  end?: string
}): Promise<ApiResponse<WorkflowStatsVo>> {
  return request<WorkflowStatsVo>({ url: '/ops/metrics/workflow', method: 'get', params })
}

/* ==================== 费用分析 ==================== */

/** 费用趋势（按日） */
export function getCostTrendApi(params: {
  start?: string
  end?: string
}): Promise<ApiResponse<CostDailyPoint[]>> {
  return request<CostDailyPoint[]>({ url: '/ops/cost/trend', method: 'get', params })
}

/** 按模型费用占比 */
export function getCostByModelApi(params: {
  start?: string
  end?: string
}): Promise<ApiResponse<CostModelPoint[]>> {
  return request<CostModelPoint[]>({ url: '/ops/cost/by-model', method: 'get', params })
}

/** 预算校验 */
export function getCostBudgetApi(params: { budget?: number }): Promise<ApiResponse<CostBudgetStatus>> {
  return request<CostBudgetStatus>({ url: '/ops/cost/budget', method: 'get', params })
}

/** 月度费用预测 */
export function getCostForecastApi(): Promise<ApiResponse<CostForecast>> {
  return request<CostForecast>({ url: '/ops/cost/forecast', method: 'get' })
}

/* ==================== 链路追踪 ==================== */

/** 链路追踪分页（ROOT span 列表） */
export function pageTracesApi(params: TraceQuery): Promise<ApiResponse<PageResult<OpsTrace>>> {
  return request<PageResult<OpsTrace>>({ url: '/ops/traces/page', method: 'get', params })
}

/** 单 trace 的 span 树 */
export function getTraceTreeApi(traceId: string): Promise<ApiResponse<TraceTreeVo>> {
  return request<TraceTreeVo>({ url: `/ops/traces/${traceId}`, method: 'get' })
}

/* ==================== 告警规则 ==================== */

/** 规则列表 */
export function listAlertRulesApi(): Promise<ApiResponse<AlertRule[]>> {
  return request<AlertRule[]>({ url: '/ops/alert-rules', method: 'get' })
}

/** 新建规则 */
export function createAlertRuleApi(data: AlertRuleRequest): Promise<ApiResponse<AlertRule>> {
  return request<AlertRule>({ url: '/ops/alert-rules', method: 'post', data })
}

/** 更新规则 */
export function updateAlertRuleApi(
  id: number,
  data: AlertRuleRequest
): Promise<ApiResponse<AlertRule>> {
  return request<AlertRule>({ url: `/ops/alert-rules/${id}`, method: 'put', data })
}

/** 删除规则 */
export function deleteAlertRuleApi(id: number): Promise<ApiResponse<void>> {
  return request<void>({ url: `/ops/alert-rules/${id}`, method: 'delete' })
}

/** 启停切换 */
export function toggleAlertRuleApi(id: number, enabled: boolean): Promise<ApiResponse<void>> {
  return request<void>({ url: `/ops/alert-rules/${id}/enabled`, method: 'put', params: { enabled } })
}
