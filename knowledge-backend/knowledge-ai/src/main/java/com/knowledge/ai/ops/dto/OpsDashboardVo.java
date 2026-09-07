package com.knowledge.ai.ops.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 运维看板聚合视图对象（GET /ops/dashboard 一次返回全部 7 指标 + 趋势图数据）。
 * <p>7 项指标卡片：
 * <ol>
 *   <li>模型调用次数（ai_call_log）</li>
 *   <li>Token 消耗（ai_call_log）</li>
 *   <li>Agent 平均耗时（agent_task）</li>
 *   <li>Milvus 查询次数 / 平均耗时（infra_metric）</li>
 *   <li>ES 查询次数 / 平均耗时（infra_metric）</li>
 *   <li>RabbitMQ 平均延迟（infra_metric）</li>
 *   <li>Workflow 成功率（workflow_task）</li>
 * </ol>
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class OpsDashboardVo {

    /** 7 指标卡片 */
    private List<MetricCard> cards;

    /** 模型调用趋势（按日，最近 30 天） */
    private List<CallTrendPoint> callTrend;

    /** 费用趋势（按日，复用 CostVo.DailyPoint） */
    private List<CostVo.DailyPoint> costTrend;

    /** 资源平均延迟（Milvus/ES/MQ 柱状图） */
    private List<ResourceLatency> latencyByResource;

    /**
     * 指标卡片。
     *
     * @param key   指标键 modelCalls/tokenUsage/agentDuration/milvusQueries/esQueries/mqLatency/workflowSuccessRate
     * @param title 卡片标题
     * @param value 数值
     * @param unit  单位（次/个/ms/%）
     * @param sub   副标题（如失败数 / P95 延迟）
     */
    public record MetricCard(String key, String title, BigDecimal value, String unit, String sub) {
    }

    /** 模型调用趋势点 */
    public record CallTrendPoint(String day, long calls, long tokens, BigDecimal cost) {
    }

    /** 资源延迟（柱状图） */
    public record ResourceLatency(String resource, long calls, double avgDurationMs, long failedCount) {
    }
}
