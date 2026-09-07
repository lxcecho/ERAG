package com.knowledge.agent.agent;

import com.knowledge.agent.dto.Evidence;
import com.knowledge.agent.dto.Plan;
import com.knowledge.agent.engine.Agent;
import com.knowledge.agent.engine.AgentContext;
import com.knowledge.agent.engine.AgentResult;
import com.knowledge.agent.engine.AgentType;
import com.knowledge.agent.engine.ArtifactType;
import com.knowledge.agent.tool.ToolContext;
import com.knowledge.agent.tool.ToolExecutor;
import com.knowledge.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索者 Agent：按 Planner 的检索计划，逐条经 {@link ToolExecutor} 调用 knowledge_search 工具召回证据。
 * <p>本 Agent 不调用 LLM（确定性检索），权限安全由 {@code ToolExecutor} 统一编排（参数校验→权限校验→审计→执行）。
 * 对同一 chunkId 去重，避免下游分析重复消费。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class KnowledgeAgent implements Agent {

    private final ToolExecutor toolExecutor;

    @Override
    public AgentType type() {
        return AgentType.KNOWLEDGE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentResult execute(AgentContext ctx) {
        Plan plan = ctx.getArtifact(ArtifactType.PLAN.name());
        if (plan == null || plan.getSearchQueries() == null || plan.getSearchQueries().isEmpty()) {
            return AgentResult.failure("缺少检索计划（Planner 未执行或未产出查询）");
        }

        int topK = ctx.getProps().getSearchTopK();
        // 工具调用预算：每次 search = 1 次工具调用，查询数不超过 maxToolCalls
        List<String> queries = plan.getSearchQueries();
        int maxCalls = ctx.getProps().getMaxToolCalls();
        if (queries.size() > maxCalls) {
            log.warn("[Knowledge] task={} 检索查询数 {} 超过工具调用预算 {}，截断",
                    ctx.getTaskId(), queries.size(), maxCalls);
            queries = new ArrayList<>(queries.subList(0, maxCalls));
        }

        // 构建 ToolContext（统一走 ToolExecutor 的权限校验 + 审计链路）
        ToolContext toolCtx = ToolContext.builder()
                .tenantId(ctx.getTenantId())
                .userId(ctx.getUserId())
                .kbId(ctx.getKbId())
                .taskId(ctx.getTaskId())
                .build();

        // chunkId 去重（同一切片可能被多查询命中）
        Map<String, Evidence> dedup = new LinkedHashMap<>();

        for (String query : queries) {
            log.info("[Knowledge] task={} 检索: {}", ctx.getTaskId(), query);
            // 统一经 ToolExecutor 调用：参数校验 → 权限校验 → 审计记录 → 执行
            ToolResult toolResult = toolExecutor.execute("knowledge_search", toolCtx,
                    Map.of("query", query, "topK", topK));
            if (!toolResult.isSuccess()) {
                log.warn("[Knowledge] task={} 检索失败 query={}: {}", ctx.getTaskId(), query, toolResult.getErrorMessage());
                continue;
            }
            List<Evidence> hits = (List<Evidence>) toolResult.getData();
            if (hits != null) {
                for (Evidence e : hits) {
                    String key = e.getChunkId() != null ? e.getChunkId()
                            : (e.getDocumentId() + "_" + e.getChunkIndex());
                    dedup.putIfAbsent(key, e);
                }
            }
        }

        List<Evidence> evidences = new ArrayList<>(dedup.values());
        if (evidences.isEmpty()) {
            return AgentResult.failure("未检索到任何资料（可能无权限或知识库为空）");
        }

        String summary = "召回证据 " + evidences.size() + " 条（来自 "
                + evidences.stream().map(Evidence::getDocumentId).distinct().count()
                + " 个文档，查询 " + queries.size() + " 条）";
        log.info("[Knowledge] task={} 完成 {}", ctx.getTaskId(), summary);
        return AgentResult.success(ArtifactType.EVIDENCES.name(), evidences, summary);
    }
}
