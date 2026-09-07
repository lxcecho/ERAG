package com.knowledge.agent.agent;

import com.knowledge.agent.dto.Plan;
import com.knowledge.agent.engine.Agent;
import com.knowledge.agent.engine.AgentContext;
import com.knowledge.agent.engine.AgentLlmCaller;
import com.knowledge.agent.engine.AgentResult;
import com.knowledge.agent.engine.AgentType;
import com.knowledge.agent.engine.ArtifactType;
import com.knowledge.agent.engine.LlmIdentity;
import com.knowledge.agent.memory.MemoryService;
import com.knowledge.agent.memory.dto.MemoryContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 规划者 Agent：理解用户目标，输出结构化检索计划（understanding + searchQueries + analysisApproach）。
 * <p>使用 {@link AgentLlmCaller#callEntity} 结构化输出（{@code .entity(Plan.class)}），由 LLM 生成 JSON 并解析为 {@link Plan}；
 * 同一调用同时记录 token 消耗至 ai_call_log。
 * <p>
 * <b>长期记忆注入</b>：当任务源自聊天（{@code ctx.getSessionId() != null}）时，调
 * {@link MemoryService#loadContext} 装配用户历史记忆（会话摘要/长期事实/近期对话/向量召回），
 * 拼接为背景段追加到 user prompt，使规划兼顾用户偏好与历史上下文。加载失败 best-effort 降级为无记忆，不阻断规划。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class PlannerAgent implements Agent {

    private final AgentLlmCaller llmCaller;
    private final MemoryService memoryService;

    private static final String SYSTEM_PROMPT = """
            你是企业知识库任务规划者。分析用户目标，输出结构化检索计划。
            要求：
            1. understanding：用一句话复述任务本质
            2. searchQueries：拆解出 2-5 个精准检索查询（覆盖目标所需信息维度，避免冗余重叠）
            3. analysisApproach：指出分析重点（如"按年度对比关键条款变化"）
            若提供"用户记忆/历史背景"，应结合其中偏好与已知事实调整检索方向，避免重复已确认信息。
            严格输出 JSON，不要附加任何解释性文字。""";

    @Override
    public AgentType type() {
        return AgentType.PLANNER;
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        String memoryText = loadMemoryBestEffort(ctx);
        String userPrompt = buildUserPrompt(ctx.getGoal(), memoryText);

        log.info("[Planner] task={} 规划中 memory={}", ctx.getTaskId(), memoryText != null ? "on" : "off");
        Plan plan = llmCaller.callEntity(SYSTEM_PROMPT, userPrompt, Plan.class, "agent",
                LlmIdentity.of(ctx.getUserId(), ctx.getTenantId()));

        if (plan == null || plan.getSearchQueries() == null || plan.getSearchQueries().isEmpty()) {
            return AgentResult.failure("Planner 未生成有效检索查询");
        }

        String summary = "理解:" + plan.getUnderstanding()
                + " | 查询数:" + plan.getSearchQueries().size()
                + " | 方向:" + plan.getAnalysisApproach();
        log.info("[Planner] task={} 完成 {}", ctx.getTaskId(), summary);
        return AgentResult.success(ArtifactType.PLAN.name(), plan, summary);
    }

    /**
     * best-effort 加载长期记忆：仅在 sessionId 非空时装配，失败返回 null（降级无记忆）。
     */
    private String loadMemoryBestEffort(AgentContext ctx) {
        if (ctx.getSessionId() == null) {
            return null;
        }
        try {
            MemoryContext mc = memoryService.loadContext(
                    ctx.getSessionId(), ctx.getUserId(), ctx.getTenantId(), ctx.getGoal());
            String text = mc.toPromptText();
            return (text == null || text.isBlank()) ? null : text;
        } catch (Exception e) {
            log.warn("[Planner] task={} 记忆加载失败，降级无记忆: {}", ctx.getTaskId(), e.getMessage());
            return null;
        }
    }

    private static String buildUserPrompt(String goal, String memoryText) {
        if (memoryText == null || memoryText.isBlank()) {
            return "用户目标：" + goal + "\n\n请生成检索计划。";
        }
        return "用户记忆/历史背景：\n" + memoryText + "\n\n用户目标：" + goal + "\n\n请生成检索计划。";
    }
}
