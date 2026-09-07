/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

import { request } from '@/api/request'

/**
 * Workflow 可控流程接口封装
 * 对应后端 WorkflowController（/workflow）：启动/详情/分页/审批/重试/取消/流程定义。
 * - definitionId 与 code 二选一启动
 * - 含 kbId 的检索类流程需 viewer 权限（后端校验）
 */

/** 分页查询当前用户的 Workflow 流程任务 */
export function pageWorkflowApi(params: {
  current?: number
  size?: number
  status?: string
}): Promise<ApiResponse<PageResult<WorkflowTaskVo>>> {
  return request<PageResult<WorkflowTaskVo>>({ url: '/workflow/page', method: 'get', params })
}

/** 流程任务详情（含节点执行记录 nodeRuns） */
export function getWorkflowApi(id: number | string): Promise<ApiResponse<WorkflowTaskVo>> {
  return request<WorkflowTaskVo>({ url: `/workflow/${id}`, method: 'get' })
}

/** 启动流程（异步，立即返回任务ID） */
export function startWorkflowApi(data: WorkflowStartRequest): Promise<ApiResponse<number | string>> {
  return request<number | string>({ url: '/workflow/start', method: 'post', data })
}

/** 人工审批（恢复 WAITING_HUMAN 任务） */
export function approveWorkflowApi(id: number | string, data: ApproveRequest): Promise<ApiResponse<number | string>> {
  return request<number | string>({ url: `/workflow/${id}/approve`, method: 'post', data })
}

/** 重试失败节点（仅 FAILED 任务可重试） */
export function retryWorkflowApi(id: number | string): Promise<ApiResponse<number | string>> {
  return request<number | string>({ url: `/workflow/${id}/retry`, method: 'post' })
}

/** 取消流程 */
export function cancelWorkflowApi(id: number | string, reason?: string): Promise<ApiResponse<number | string>> {
  return request<number | string>({
    url: `/workflow/${id}/cancel`,
    method: 'post',
    params: reason ? { reason } : undefined
  })
}

/** 流程定义列表（当前租户 + 系统预置） */
export function listWorkflowDefinitionsApi(): Promise<ApiResponse<WorkflowDefinition[]>> {
  return request<WorkflowDefinition[]>({ url: '/workflow/definitions', method: 'get' })
}
