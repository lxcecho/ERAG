package com.knowledge.agent.agent;

import com.knowledge.agent.dto.Evidence;
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
 * 撰写者 Agent：基于分析结论生成最终报告，结论标注来源引用。
 * <p>使用 {@link AgentLlmCaller}（封装 ChatModel + 调用埋点）生成结构化报告；每个关键结论后用 [来源:文件名#chunkId] 标注引用。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class ReportAgent implements Agent {

    private final AgentLlmCaller llmCaller;

    private static final String SYSTEM_PROMPT = """
            你是企业知识库报告撰写者。基于分析结果生成最终报告。
            要求：
            - 结构清晰：标题、摘要、正文（分点）、结论
            - 每个关键结论后用 [来源:文件名#chunkId] 标注引用
            - 客观陈述，区分"资料证实"与"合理推断"
            - 末尾列出"参考资料清单"
            - 使用 Markdown 格式""";

    @Override
    public AgentType type() {
        return AgentType.REPORT;
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        String analysis = ctx.getArtifact(ArtifactType.ANALYSIS.name());
        if (analysis == null || analysis.isBlank()) {
            return AgentResult.failure("缺少分析结论（AnalysisAgent 未产出）");
        }
        List<Evidence> evidences = ctx.getArtifact(ArtifactType.EVIDENCES.name());

        String citationList = formatCitations(evidences);
        String userPrompt = "用户目标：" + ctx.getGoal()
                + "\n\n分析结果：\n" + analysis
                + "\n\n可用引用源（用于标注引用，格式 [来源:文件名#chunkId]）：\n" + citationList;

        log.info("[Report] task={} 生成报告中", ctx.getTaskId());
        LlmResult llm = llmCaller.call(SYSTEM_PROMPT, userPrompt, "agent",
                LlmIdentity.of(ctx.getUserId(), ctx.getTenantId()));
        String report = llm.text();
        int tokens = llm.totalTokens();

        if (report == null || report.isBlank()) {
            return AgentResult.failure("报告生成结果为空");
        }

        log.info("[Report] task={} 完成，报告长度={} token={}", ctx.getTaskId(), report.length(), tokens);
        return AgentResult.success(ArtifactType.REPORT.name(), report,
                "报告生成完成，长度 " + report.length(), tokens);
    }

    /** 格式化引用源清单 */
    private static String formatCitations(List<Evidence> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return "（无）";
        }
        StringBuilder sb = new StringBuilder();
        for (Evidence e : evidences) {
            String source = e.getSource() != null ? e.getSource() : String.valueOf(e.getDocumentId());
            sb.append("- 文件:").append(source)
                    .append(" | chunkId:").append(e.getChunkId())
                    .append(" | docId:").append(e.getDocumentId()).append("\n");
        }
        return sb.toString();
    }
}
