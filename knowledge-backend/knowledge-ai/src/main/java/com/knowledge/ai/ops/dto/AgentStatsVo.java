package com.knowledge.ai.ops.dto;

import java.util.List;

/**
 * Agent 耗时统计视图对象（运维指标 #3，GET /ops/metrics/agent）。
 *
 * @param total          总任务数（已完成）
 * @param completed      成功数
 * @param failed         失败数
 * @param avgDurationMs  平均耗时（ms）
 * @param maxDurationMs  最大耗时（ms）
 * @param tokens         累计 Token
 * @param dailyTrend     按日趋势（最近 30 天）
 *
 * @author: lxcechoo@gmail.com
 */
public record AgentStatsVo(long total, long completed, long failed, double avgDurationMs,
                           long maxDurationMs, long tokens,
                           List<AgentDailyPoint> dailyTrend) {

    /** Agent 按日趋势点 */
    public record AgentDailyPoint(String day, long total, long completed, double avgDurationMs) {
    }
}
