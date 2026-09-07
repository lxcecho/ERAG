package com.knowledge.agent.ops.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.ai.ops.dto.AgentStatsVo;
import com.knowledge.ai.ops.dto.InfraMetricVo;
import com.knowledge.ai.ops.dto.OpsDashboardVo;
import com.knowledge.ai.ops.dto.TraceQuery;
import com.knowledge.ai.ops.dto.TraceTreeVo;
import com.knowledge.ai.ops.entity.OpsTrace;
import com.knowledge.ai.ops.dto.WorkflowStatsVo;

/**
 * 运维看板聚合服务：聚合 7 项监控指标 + 基础设施时序 + Agent/Workflow 统计 + 链路追踪查询。
 * <p>放置于 knowledge-agent 模块（依赖 knowledge-ai），可同时注入 ai 侧 Mapper
 * （AiCallLog/InfraMetric/OpsTrace）与 agent 侧 Mapper（AgentTask/WorkflowTask）。
 *
 * @author: lxcechoo@gmail.com
 */
public interface OpsDashboardService {

    /** 7 指标聚合看板（卡片 + 调用趋势 + 费用趋势 + 资源延迟） */
    OpsDashboardVo dashboard();

    /** 基础设施指标（Milvus/ES/MQ 概览 + 时序） */
    InfraMetricVo.InfraOverview infraMetrics(String start, String end);

    /** Agent 耗时统计（指标 #3） */
    AgentStatsVo agentMetrics(String start, String end);

    /** Workflow 成功率统计（指标 #7） */
    WorkflowStatsVo workflowMetrics(String start, String end);

    /** 链路追踪分页（ROOT span 列表） */
    IPage<OpsTrace> pageTraces(TraceQuery query);

    /** 单 trace 的 span 树 */
    TraceTreeVo getTraceTree(String traceId);
}
