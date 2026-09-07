/**
 * @since: 2026-08-02
 * @author: lxcechoo@gmail.com
 */

import { request } from '@/api/request'

/* ==================== 工作台统计 ==================== */

/** 工作台四项统计（知识库/文档/切片/今日对话） */
export function getDashboardStatsApi(): Promise<ApiResponse<DashboardStats>> {
  return request<DashboardStats>({ url: '/dashboard/stats', method: 'get' })
}
