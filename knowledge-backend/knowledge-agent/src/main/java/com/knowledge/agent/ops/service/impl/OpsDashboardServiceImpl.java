package com.knowledge.agent.ops.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.agent.mapper.AgentTaskMapper;
import com.knowledge.agent.ops.service.OpsDashboardService;
import com.knowledge.agent.workflow.mapper.WorkflowTaskMapper;
import com.knowledge.ai.calllog.mapper.AiCallLogMapper;
import com.knowledge.ai.ops.cost.CostAnalyzer;
import com.knowledge.ai.ops.dto.AgentStatsVo;
import com.knowledge.ai.ops.dto.CostVo;
import com.knowledge.ai.ops.dto.InfraMetricVo;
import com.knowledge.ai.ops.dto.OpsDashboardVo;
import com.knowledge.ai.ops.dto.TraceQuery;
import com.knowledge.ai.ops.dto.TraceTreeVo;
import com.knowledge.ai.ops.entity.OpsTrace;
import com.knowledge.ai.ops.dto.WorkflowStatsVo;
import com.knowledge.ai.ops.mapper.InfraMetricMapper;
import com.knowledge.ai.ops.mapper.OpsTraceMapper;
import com.knowledge.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运维看板聚合服务实现。
 * <p>组合 ai 侧（AiCallLog/InfraMetric/OpsTrace）与 agent 侧（AgentTask/WorkflowTask）Mapper，
 * 聚合 7 项监控指标。统计窗口默认最近 7 天（卡片）与 30 天（趋势）。
 *
 * @author: lxcechoo@gmail.com
 */
@Service
@RequiredArgsConstructor
public class OpsDashboardServiceImpl implements OpsDashboardService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiCallLogMapper aiCallLogMapper;
    private final InfraMetricMapper infraMetricMapper;
    private final OpsTraceMapper opsTraceMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final WorkflowTaskMapper workflowTaskMapper;
    private final CostAnalyzer costAnalyzer;

    /* ==================== 7 指标聚合看板 ==================== */

    @Override
    public OpsDashboardVo dashboard() {
        Long tid = currentTenantId();
        LocalDateTime now = LocalDateTime.now();
        String start7 = now.minusDays(7).format(FMT);
        String end = now.format(FMT);
        String start30 = now.minusDays(30).format(FMT);

        OpsDashboardVo vo = new OpsDashboardVo();
        vo.setCards(buildCards(tid, start7, end));
        vo.setCallTrend(buildCallTrend(tid, start30, end));
        vo.setCostTrend(costAnalyzer.dailyTrend(tid, start30, end));
        vo.setLatencyByResource(buildLatencyByResource(tid, start7, end));
        return vo;
    }

    private List<OpsDashboardVo.MetricCard> buildCards(Long tid, String start, String end) {
        // 模型调用 + Token（ai_call_log 概览）
        Map<String, Object> aiOv = aiCallLogMapper.statsOverview(tid, start, end);
        long modelCalls = toLong(aiOv.get("calls"));
        long tokenUsage = toLong(aiOv.get("tokens"));
        long aiFailed = toLong(aiOv.get("failedCount"));

        // Agent 耗时（agent_task）
        Map<String, Object> agentOv = agentTaskMapper.statsDuration(tid, start, end);
        double agentAvgMs = toDouble(agentOv.get("avgDurationMs"));
        long agentTotal = toLong(agentOv.get("total"));

        // Milvus / ES / MQ（infra_metric）
        Map<String, Object> milvusOv = infraMetricMapper.statsByResource(tid, "milvus:search", start, end);
        long milvusCalls = toLong(milvusOv.get("calls"));
        double milvusAvgMs = toDouble(milvusOv.get("avgDuration"));

        Map<String, Object> esOv = infraMetricMapper.statsByResource(tid, "es:search", start, end);
        long esCalls = toLong(esOv.get("calls"));
        double esAvgMs = toDouble(esOv.get("avgDuration"));

        Map<String, Object> mqOv = infraMetricMapper.statsByResource(tid, "mq:parse", start, end);
        long mqCalls = toLong(mqOv.get("calls"));
        double mqAvgMs = toDouble(mqOv.get("avgDuration"));

        // Workflow 成功率（workflow_task）
        Map<String, Object> wfOv = workflowTaskMapper.statsSuccessRate(tid, start, end);
        double wfSuccessRate = toDouble(wfOv.get("successRate"));
        long wfTotal = toLong(wfOv.get("total"));

        List<OpsDashboardVo.MetricCard> cards = new ArrayList<>(7);
        cards.add(new OpsDashboardVo.MetricCard("modelCalls", "模型调用次数",
                BigDecimal.valueOf(modelCalls), "次", "失败 " + aiFailed));
        cards.add(new OpsDashboardVo.MetricCard("tokenUsage", "Token 消耗",
                BigDecimal.valueOf(tokenUsage), "个", null));
        cards.add(new OpsDashboardVo.MetricCard("agentDuration", "Agent 平均耗时",
                BigDecimal.valueOf(agentAvgMs).setScale(0, java.math.RoundingMode.HALF_UP), "ms",
                agentTotal + " 个任务"));
        cards.add(new OpsDashboardVo.MetricCard("milvusQueries", "Milvus 查询",
                BigDecimal.valueOf(milvusCalls), "次",
                "均耗时 " + (long) milvusAvgMs + "ms"));
        cards.add(new OpsDashboardVo.MetricCard("esQueries", "ES 查询",
                BigDecimal.valueOf(esCalls), "次",
                "均耗时 " + (long) esAvgMs + "ms"));
        cards.add(new OpsDashboardVo.MetricCard("mqLatency", "RabbitMQ 延迟",
                BigDecimal.valueOf(mqAvgMs).setScale(0, java.math.RoundingMode.HALF_UP), "ms",
                mqCalls + " 次消费"));
        cards.add(new OpsDashboardVo.MetricCard("workflowSuccessRate", "Workflow 成功率",
                BigDecimal.valueOf(wfSuccessRate).setScale(2, java.math.RoundingMode.HALF_UP), "%",
                wfTotal + " 个流程"));
        return cards;
    }

    private List<OpsDashboardVo.CallTrendPoint> buildCallTrend(Long tid, String start, String end) {
        List<OpsDashboardVo.CallTrendPoint> points = new ArrayList<>();
        for (Map<String, Object> row : aiCallLogMapper.statsByDay(tid, start, end)) {
            Object dayObj = row.get("day");
            points.add(new OpsDashboardVo.CallTrendPoint(
                    dayObj == null ? "" : dayObj.toString(),
                    toLong(row.get("calls")),
                    toLong(row.get("tokens")),
                    toBigDecimal(row.get("cost"))));
        }
        return points;
    }

    private List<OpsDashboardVo.ResourceLatency> buildLatencyByResource(Long tid, String start, String end) {
        List<OpsDashboardVo.ResourceLatency> list = new ArrayList<>();
        for (Map<String, Object> row : infraMetricMapper.statsAllResources(tid, start, end)) {
            list.add(new OpsDashboardVo.ResourceLatency(
                    (String) row.get("resource"),
                    toLong(row.get("calls")),
                    toDouble(row.get("avgDuration")),
                    toLong(row.get("failedCount"))));
        }
        return list;
    }

    /* ==================== 基础设施指标 ==================== */

    @Override
    public InfraMetricVo.InfraOverview infraMetrics(String start, String end) {
        Long tid = currentTenantId();
        String[] range = defaultRange(start, end);
        List<InfraMetricVo.ResourceStats> overview = new ArrayList<>();
        for (Map<String, Object> row : infraMetricMapper.statsAllResources(tid, range[0], range[1])) {
            overview.add(new InfraMetricVo.ResourceStats(
                    (String) row.get("resource"),
                    toLong(row.get("calls")),
                    toLong(row.get("successCount")),
                    toLong(row.get("failedCount")),
                    toDouble(row.get("avgDuration")),
                    0L));
        }
        List<InfraMetricVo.MetricPoint> series = new ArrayList<>();
        for (Map<String, Object> row : infraMetricMapper.dailyByResource(tid, null, range[0], range[1])) {
            Object dayObj = row.get("day");
            series.add(new InfraMetricVo.MetricPoint(
                    dayObj == null ? "" : dayObj.toString(),
                    (String) row.get("resource"),
                    toLong(row.get("calls")),
                    toDouble(row.get("avgDuration"))));
        }
        return new InfraMetricVo.InfraOverview(overview, series);
    }

    /* ==================== Agent 耗时统计 ==================== */

    @Override
    public AgentStatsVo agentMetrics(String start, String end) {
        Long tid = currentTenantId();
        String[] range = defaultRange(start, end);
        Map<String, Object> ov = agentTaskMapper.statsDuration(tid, range[0], range[1]);
        List<AgentStatsVo.AgentDailyPoint> trend = new ArrayList<>();
        for (Map<String, Object> row : agentTaskMapper.dailyStats(tid, range[0], range[1])) {
            Object dayObj = row.get("day");
            trend.add(new AgentStatsVo.AgentDailyPoint(
                    dayObj == null ? "" : dayObj.toString(),
                    toLong(row.get("total")),
                    toLong(row.get("completed")),
                    toDouble(row.get("avgDurationMs"))));
        }
        return new AgentStatsVo(
                toLong(ov.get("total")),
                toLong(ov.get("completed")),
                toLong(ov.get("failed")),
                toDouble(ov.get("avgDurationMs")),
                (long) toDouble(ov.get("maxDurationMs")),
                toLong(ov.get("tokens")),
                trend);
    }

    /* ==================== Workflow 成功率 ==================== */

    @Override
    public WorkflowStatsVo workflowMetrics(String start, String end) {
        Long tid = currentTenantId();
        String[] range = defaultRange(start, end);
        Map<String, Object> ov = workflowTaskMapper.statsSuccessRate(tid, range[0], range[1]);
        List<WorkflowStatsVo.WorkflowDailyPoint> trend = new ArrayList<>();
        for (Map<String, Object> row : workflowTaskMapper.dailyStats(tid, range[0], range[1])) {
            Object dayObj = row.get("day");
            trend.add(new WorkflowStatsVo.WorkflowDailyPoint(
                    dayObj == null ? "" : dayObj.toString(),
                    toLong(row.get("total")),
                    toLong(row.get("completed")),
                    toDouble(row.get("successRate"))));
        }
        return new WorkflowStatsVo(
                toLong(ov.get("total")),
                toLong(ov.get("completed")),
                toLong(ov.get("failed")),
                toDouble(ov.get("successRate")),
                toLong(ov.get("tokens")),
                trend);
    }

    /* ==================== 链路追踪 ==================== */

    @Override
    public IPage<OpsTrace> pageTraces(TraceQuery query) {
        return opsTraceMapper.pageRootSpans(query.toPage(), currentTenantId(), query);
    }

    @Override
    public TraceTreeVo getTraceTree(String traceId) {
        Long tid = currentTenantId();
        List<OpsTrace> spans = opsTraceMapper.listByTraceId(tid, traceId);
        if (spans.isEmpty()) {
            return null;
        }
        // 按 spanId 索引
        Map<String, TraceTreeVo> idx = new HashMap<>();
        for (OpsTrace s : spans) {
            TraceTreeVo node = new TraceTreeVo();
            node.setTraceId(s.getTraceId());
            node.setSpanId(s.getSpanId());
            node.setParentSpanId(s.getParentSpanId());
            node.setSpanName(s.getSpanName());
            node.setSpanType(s.getSpanType());
            node.setStartTime(s.getStartTime());
            node.setDurationMs(s.getDurationMs());
            node.setStatus(s.getStatus());
            node.setAttributesJson(s.getAttributesJson());
            idx.put(s.getSpanId(), node);
        }
        // 按 parentSpanId 构建子树；ROOT（parent 为空）作为返回根
        TraceTreeVo root = null;
        for (TraceTreeVo node : idx.values()) {
            if (node.getParentSpanId() == null || node.getParentSpanId().isBlank()) {
                root = node;
            } else {
                TraceTreeVo parent = idx.get(node.getParentSpanId());
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    // 父 span 缺失（采样漏落），挂到 root 兜底
                    if (root != null) {
                        root.getChildren().add(node);
                    }
                }
            }
        }
        return root;
    }

    /* ==================== 工具 ==================== */

    private String[] defaultRange(String start, String end) {
        if ((start == null || start.isBlank()) && (end == null || end.isBlank())) {
            LocalDateTime now = LocalDateTime.now();
            return new String[]{now.minusDays(7).format(FMT), now.format(FMT)};
        }
        return new String[]{start, end};
    }

    private static Long currentTenantId() {
        Long id = TenantContext.getTenantId();
        return id != null ? id : TenantContext.PLATFORM_TENANT_ID;
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return 0L; }
    }

    private static double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
