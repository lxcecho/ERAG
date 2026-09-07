package com.knowledge.agent.ops.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.agent.ops.service.OpsDashboardService;
import com.knowledge.ai.ops.cost.CostAnalyzer;
import com.knowledge.ai.ops.dto.AgentStatsVo;
import com.knowledge.ai.ops.dto.CostVo;
import com.knowledge.ai.ops.dto.InfraMetricVo;
import com.knowledge.ai.ops.dto.OpsDashboardVo;
import com.knowledge.ai.ops.dto.TraceQuery;
import com.knowledge.ai.ops.dto.TraceTreeVo;
import com.knowledge.ai.ops.entity.OpsTrace;
import com.knowledge.ai.ops.dto.WorkflowStatsVo;
import com.knowledge.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * AI 运维中心接口（@RequestMapping /ops，全局 context-path /api → /api/ops）。
 * <p>聚合 7 项监控指标看板 + 基础设施时序 + Agent/Workflow 统计 + 费用分析 + 链路追踪。
 *
 * @author: lxcechoo@gmail.com
 */
@Tag(name = "AI运维中心接口")
@RestController
@RequestMapping("/ops")
@RequiredArgsConstructor
public class OpsController {

    private final OpsDashboardService dashboardService;
    private final CostAnalyzer costAnalyzer;

    /* ==================== 看板 ==================== */

    @Operation(summary = "运维看板（7 指标聚合）")
    @GetMapping("/dashboard")
    public Result<OpsDashboardVo> dashboard() {
        return Result.success(dashboardService.dashboard());
    }

    /* ==================== 基础设施指标 ==================== */

    @Operation(summary = "基础设施指标（Milvus/ES/MQ 概览+时序）")
    @GetMapping("/metrics/infra")
    public Result<InfraMetricVo.InfraOverview> infraMetrics(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return Result.success(dashboardService.infraMetrics(start, end));
    }

    @Operation(summary = "Agent 耗时统计")
    @GetMapping("/metrics/agent")
    public Result<AgentStatsVo> agentMetrics(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return Result.success(dashboardService.agentMetrics(start, end));
    }

    @Operation(summary = "Workflow 成功率统计")
    @GetMapping("/metrics/workflow")
    public Result<WorkflowStatsVo> workflowMetrics(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return Result.success(dashboardService.workflowMetrics(start, end));
    }

    /* ==================== 费用分析 ==================== */

    @Operation(summary = "费用趋势（按日）")
    @GetMapping("/cost/trend")
    public Result<java.util.List<CostVo.DailyPoint>> costTrend(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return Result.success(costAnalyzer.dailyTrend(null, start, end));
    }

    @Operation(summary = "按模型费用占比")
    @GetMapping("/cost/by-model")
    public Result<java.util.List<CostVo.ModelPoint>> costByModel(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return Result.success(costAnalyzer.costByModel(null, start, end));
    }

    @Operation(summary = "预算校验")
    @GetMapping("/cost/budget")
    public Result<CostVo.BudgetStatus> costBudget(
            @RequestParam(required = false) BigDecimal budget) {
        return Result.success(costAnalyzer.checkBudget(null, budget));
    }

    @Operation(summary = "月度费用预测")
    @GetMapping("/cost/forecast")
    public Result<CostVo.Forecast> costForecast() {
        return Result.success(costAnalyzer.forecast(null));
    }

    /* ==================== 链路追踪 ==================== */

    @Operation(summary = "链路追踪分页")
    @GetMapping("/traces/page")
    public Result<IPage<OpsTrace>> pageTraces(TraceQuery query) {
        return Result.success(dashboardService.pageTraces(query));
    }

    @Operation(summary = "单 trace 的 span 树")
    @GetMapping("/traces/{traceId}")
    public Result<TraceTreeVo> getTraceTree(@PathVariable String traceId) {
        return Result.success(dashboardService.getTraceTree(traceId));
    }
}
