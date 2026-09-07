package com.knowledge.ai.ops.dto;

import java.util.List;

/**
 * Workflow 成功率统计视图对象（运维指标 #7，GET /ops/metrics/workflow）。
 *
 * @param total        总流程数
 * @param completed    成功数
 * @param failed       失败数
 * @param successRate  成功率（%，保留 2 位）
 * @param tokens       累计 Token
 * @param dailyTrend   按日趋势（最近 30 天）
 *
 * @author: lxcechoo@gmail.com
 */
public record WorkflowStatsVo(long total, long completed, long failed, double successRate,
                              long tokens, List<WorkflowDailyPoint> dailyTrend) {

    /** Workflow 按日趋势点 */
    public record WorkflowDailyPoint(String day, long total, long completed, double successRate) {
    }
}
