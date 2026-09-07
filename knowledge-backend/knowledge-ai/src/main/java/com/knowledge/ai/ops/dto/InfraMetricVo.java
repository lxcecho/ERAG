package com.knowledge.ai.ops.dto;

import java.util.List;

/**
 * 基础设施指标视图对象（Milvus/ES/MQ 时序明细，GET /ops/metrics/infra）。
 *
 * @author: lxcechoo@gmail.com
 */
public final class InfraMetricVo {

    private InfraMetricVo() {
    }

    /**
     * 单资源统计概览。
     *
     * @param resource      资源名 milvus:search / es:search / mq:parse
     * @param calls         调用次数
     * @param successCount  成功数
     * @param failedCount   失败数
     * @param avgDurationMs 平均耗时
     * @param maxDurationMs 最大耗时
     */
    public record ResourceStats(String resource, long calls, long successCount, long failedCount,
                                double avgDurationMs, long maxDurationMs) {
    }

    /**
     * 时序点（按日 × 资源）。
     *
     * @param day           日期
     * @param resource      资源名
     * @param calls         调用次数
     * @param avgDurationMs 平均耗时
     */
    public record MetricPoint(String day, String resource, long calls, double avgDurationMs) {
    }

    /** 基础设施指标聚合结果（概览列表 + 时序点列表） */
    public record InfraOverview(List<ResourceStats> overview, List<MetricPoint> series) {
    }
}
