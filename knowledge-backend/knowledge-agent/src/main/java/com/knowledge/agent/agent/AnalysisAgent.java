package com.knowledge.agent.agent;

import com.knowledge.agent.dto.Evidence;
import com.knowledge.agent.dto.Plan;
import com.knowledge.agent.engine.Agent;
import com.knowledge.agent.engine.AgentContext;
import com.knowledge.agent.engine.AgentLlmCaller;
import com.knowledge.agent.engine.AgentResult;
import com.knowledge.agent.engine.AgentType;
import com.knowledge.agent.engine.ArtifactType;
import com.knowledge.agent.engine.LlmIdentity;
import com.knowledge.agent.engine.LlmResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 分析者 Agent：基于检索证据，针对用户目标进行对比、归纳、推理。
 * <p>使用 {@link AgentLlmCaller}（封装 ChatModel + 调用埋点）生成文本分析结论；严格基于资料，不足处标注[资料不足]。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class AnalysisAgent implements Agent {

    private final AgentLlmCaller llmCaller;

    private static final String SYSTEM_PROMPT = """
            你是企业知识库分析者。基于检索到的资料，针对用户目标进行对比、归纳、推理。
            要求：
            - 严格基于提供的资料，不可凭空臆测
            - 资料不足的维度，明确标注"[资料不足]"
            - 输出结构化分析（分点、对比、结论），便于撰写报告复用""";

    @Override
    public AgentType type() {
        return AgentType.ANALYSIS;
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        List<Evidence> evidences = ctx.getArtifact(ArtifactType.EVIDENCES.name());
        if (evidences == null || evidences.isEmpty()) {
            return AgentResult.failure("缺少证据（KnowledgeAgent 未召回资料）");
        }
        Plan plan = ctx.getArtifact(ArtifactType.PLAN.name());

        String evidenceBlock = formatEvidence(evidences);
        String approach = plan != null && plan.getAnalysisApproach() != null
                ? plan.getAnalysisApproach() : "无特定方向，请全面分析";

        String userPrompt = "用户目标：" + ctx.getGoal()
                + "\n分析方向：" + approach
                + "\n\n检索资料：\n" + evidenceBlock;

        log.info("[Analysis] task={} 分析中（证据{}条）", ctx.getTaskId(), evidences.size());
        LlmResult llm = llmCaller.call(SYSTEM_PROMPT, userPrompt, "agent",
                LlmIdentity.of(ctx.getUserId(), ctx.getTenantId()));
        String analysis = llm.text();
        int tokens = llm.totalTokens();

        if (analysis == null || analysis.isBlank()) {
            return AgentResult.failure("分析结果为空");
        }

        log.info("[Analysis] task={} 完成，结论长度={} token={}", ctx.getTaskId(), analysis.length(), tokens);
        return AgentResult.success(ArtifactType.ANALYSIS.name(), analysis,
                "分析完成，长度 " + analysis.length(), tokens);
    }

    /** 格式化证据为 LLM 可读文本块 */
    private static String formatEvidence(List<Evidence> evidences) {
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (Evidence e : evidences) {
            sb.append("[").append(idx++).append("] ")
                    .append("文件:").append(e.getSource() != null ? e.getSource() : e.getDocumentId())
                    .append(" (chunk=").append(e.getChunkId()).append(", score=").append(e.getScore()).append(")\n")
                    .append("内容: ").append(e.getContent()).append("\n---\n");
        }
        return sb.toString();
    }
}
