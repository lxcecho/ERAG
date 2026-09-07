package com.knowledge.agent.engine;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.knowledge.ai.calllog.service.AiCallLogger;
import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.health.AiCallHealthTracker;
import com.knowledge.common.alert.AlertLevel;
import com.knowledge.common.alert.AlertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 层统一 LLM 调用器：封装 LangChain4j {@link ChatModel} 调用 + {@link AiCallLogger} 埋点。
 * <p>
 * 设计目的：将「调用大模型 + 记录 ai_call_log」收敛到一处，消除各 Agent/Tool/Workflow 节点
 * 中重复的 tracer/try-catch/extractTokens 样板代码，并保证每次调用都被审计。
 * <p>
 * 身份传播：Agent/Workflow 在异步线程池执行（SecurityContext 不可用），故通过
 * {@link LlmIdentity} 显式传入 userId/tenantId；模型名取自配置（{@link AiProperties}）。
 * <p>
 * 异常语义：调用失败时先 {@code tracer.failure(e)} 记录失败日志再抛出，不吞异常，由上游决定兜底。
 * <p>
 * <b>生产能力</b>：两个方法均加 {@link SentinelResource}（资源名统一 {@code agent:llm:call}，
 * 共享熔断器状态，Planner/Analysis/Report 联动熔断）。
 * 熔断/限流时 blockHandler 返回降级值（call→空结果；callEntity→null，由上游 Agent 步骤兜底跳过），
 * 并经 {@link AlertService} 发送 CRITICAL 告警。调用成功/失败上报 {@link AiCallHealthTracker}（"llm" 资源）。
 * <p>
 * <b>框架选型</b>：基于 LangChain4j {@link ChatModel}（OpenAI 兼容协议），
 * 与 RAG 层共用同一 OpenAI 兼容端点与配置。结构化输出通过 prompt 引导 JSON + Jackson 解析实现。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLlmCaller {

    private static final String RESOURCE = "agent:llm:call";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatModel chatModel;
    private final AiProperties aiProperties;
    private final AiCallLogger aiCallLogger;
    private final AiCallHealthTracker healthTracker;
    private final AlertService alertService;

    /**
     * 文本生成调用（同步）。
     *
     * @param systemPrompt 系统提示词（可空）
     * @param userPrompt   用户提示词
     * @param module       业务模块（agent/workflow/document_compare/report_generate）
     * @param identity     调用方身份
     * @return 生成文本 + token 明细
     */
    @SentinelResource(value = RESOURCE, blockHandler = "callBlockHandler")
    public LlmResult call(String systemPrompt, String userPrompt, String module, LlmIdentity identity) {
        AiCallLogger.Tracer tracer = tracer(module, identity);
        try {
            ChatResponse resp = chatModel.chat(
                    SystemMessage.from(systemPrompt != null ? systemPrompt : ""),
                    UserMessage.from(userPrompt));
            String text = resp.aiMessage().text();
            TokenUsage u = resp.tokenUsage();
            int prompt = inputTokens(u);
            int completion = completionTokens(u);
            tracer.success(modelName(), prompt, completion);
            healthTracker.recordSuccess("llm");
            return new LlmResult(text, prompt, completion, prompt + completion);
        } catch (RuntimeException e) {
            tracer.failure(e);
            healthTracker.recordFailure("llm");
            throw e;
        }
    }

    /**
     * 结构化输出调用：通过 prompt 引导 LLM 返回 JSON + Jackson 反序列化为目标类型。
     * <p>
     * 原 Spring AI 的 {@code .entity(Class)} 由框架自动处理 JSON Schema 注入与解析；
     * 迁移到 LangChain4j 后，在 userPrompt 末尾追加 JSON 格式约束，再从响应中提取 JSON 并解析。
     *
     * @param type 目标实体类型
     * @return 解析后的实体（可能为 null，由调用方兜底）
     */
    @SentinelResource(value = RESOURCE, blockHandler = "callEntityBlockHandler")
    public <T> T callEntity(String systemPrompt, String userPrompt, Class<T> type,
                            String module, LlmIdentity identity) {
        AiCallLogger.Tracer tracer = tracer(module, identity);
        try {
            // 追加 JSON 格式约束，引导 LLM 返回纯 JSON
            String jsonPrompt = userPrompt + "\n\n请严格以JSON格式返回结果，不要包含```json代码块标记或任何说明文字。";
            ChatResponse resp = chatModel.chat(
                    SystemMessage.from(systemPrompt != null ? systemPrompt : ""),
                    UserMessage.from(jsonPrompt));
            String text = resp.aiMessage().text();
            T entity = OBJECT_MAPPER.readValue(extractJson(text), type);
            TokenUsage u = resp.tokenUsage();
            tracer.success(modelName(), inputTokens(u), completionTokens(u));
            healthTracker.recordSuccess("llm");
            return entity;
        } catch (RuntimeException e) {
            tracer.failure(e);
            healthTracker.recordFailure("llm");
            throw e;
        } catch (Exception e) {
            tracer.failure(e);
            healthTracker.recordFailure("llm");
            throw new RuntimeException("结构化输出解析失败: " + e.getMessage(), e);
        }
    }

    /* ==================== Sentinel blockHandler（熔断/限流降级） ==================== */

    /**
     * call 降级：返回空 LlmResult（空文本 + 0 token），不抛异常打断 Agent 流水线。
     */
    public LlmResult callBlockHandler(String systemPrompt, String userPrompt, String module,
                                      LlmIdentity identity, BlockException ex) {
        alertService.alert(AlertLevel.CRITICAL, RESOURCE, "Agent LLM调用降级",
                "module=" + module + ", blockType=" + ex.getClass().getSimpleName(), ex);
        return new LlmResult("", 0, 0, 0);
    }

    /**
     * callEntity 降级：返回 null，由上游 Agent 步骤（如 Planner）兜底跳过该步。
     */
    public <T> T callEntityBlockHandler(String systemPrompt, String userPrompt, Class<T> type,
                                        String module, LlmIdentity identity, BlockException ex) {
        alertService.alert(AlertLevel.CRITICAL, RESOURCE, "Agent LLM结构化调用降级",
                "module=" + module + ", blockType=" + ex.getClass().getSimpleName(), ex);
        return null;
    }

    /* ==================== 工具 ==================== */

    private AiCallLogger.Tracer tracer(String module, LlmIdentity identity) {
        return aiCallLogger.trace(module, "CHAT", null,
                identity.userId(), identity.username(), identity.tenantId());
    }

    private String modelName() {
        return aiProperties.getLlm().getModelName();
    }

    private static int inputTokens(TokenUsage u) {
        return u == null || u.inputTokenCount() == null ? 0 : u.inputTokenCount();
    }

    private static int completionTokens(TokenUsage u) {
        return u == null || u.outputTokenCount() == null ? 0 : u.outputTokenCount();
    }

    /**
     * 从 LLM 响应中提取 JSON 字符串。
     * <p>部分模型即使用户要求纯 JSON，仍可能包裹 ```json ... ``` 代码块，此处统一剥离。
     */
    private static String extractJson(String text) {
        if (text == null || text.isBlank()) return "{}";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            if (start > 0) {
                int end = trimmed.lastIndexOf("```");
                if (end > start) {
                    return trimmed.substring(start + 1, end).trim();
                }
            }
        }
        return trimmed;
    }
}
