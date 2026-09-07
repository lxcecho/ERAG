/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

import { request } from '@/api/request'

/** Prompt 模板分页查询（每个 code 最新版本） */
export function pagePromptApi(params: {
  pageNo?: number
  pageSize?: number
  name?: string
  type?: string
  promptCode?: string
}): Promise<ApiResponse<PageResult<PromptTemplate>>> {
  return request<PageResult<PromptTemplate>>({ url: '/prompt', method: 'get', params })
}

/** 版本详情 */
export function getPromptApi(id: number): Promise<ApiResponse<PromptTemplate>> {
  return request<PromptTemplate>({ url: `/prompt/${id}`, method: 'get' })
}

/** 某 promptCode 的全部版本列表 */
export function listPromptVersionsApi(promptCode: string): Promise<ApiResponse<PromptTemplate[]>> {
  return request<PromptTemplate[]>({ url: `/prompt/versions/${promptCode}`, method: 'get' })
}

/** 创建新 Prompt（新 code，v1 草稿） */
export function createPromptApi(data: PromptTemplateRequest): Promise<ApiResponse<number>> {
  return request<number>({ url: '/prompt', method: 'post', data })
}

/** 编辑模板（DRAFT原地更新；PUBLISHED/ARCHIVED派生新版本） */
export function editPromptApi(data: PromptTemplateRequest): Promise<ApiResponse<number>> {
  return request<number>({ url: '/prompt', method: 'put', data })
}

/** 发布版本（同 code 同租户旧发布版归档） */
export function publishPromptApi(id: number): Promise<ApiResponse> {
  return request({ url: `/prompt/${id}/publish`, method: 'put' })
}

/** 回滚至历史版本（派生新发布版本） */
export function rollbackPromptApi(id: number): Promise<ApiResponse> {
  return request({ url: `/prompt/${id}/rollback`, method: 'put' })
}

/** 删除版本（软删） */
export function removePromptApi(id: number): Promise<ApiResponse> {
  return request({ url: `/prompt/${id}`, method: 'delete' })
}

/** 测试模板（渲染变量 + 调用 LLM 预览） */
export function testPromptApi(data: PromptTestRequest): Promise<ApiResponse<PromptTestResult>> {
  return request<PromptTestResult>({ url: '/prompt/test', method: 'post', data })
}
