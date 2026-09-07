package com.knowledge.ai.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.knowledge.ai.calllog.service.AiCallLogger;
import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.health.AiCallHealthTracker;
import com.knowledge.ai.ops.trace.Span;
import com.knowledge.ai.ops.trace.SpanType;
import com.knowledge.ai.ops.trace.TraceService;
import com.knowledge.ai.service.LLMService;
import com.knowledge.common.alert.AlertLevel;
import com.knowledge.common.alert.AlertService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 服务实现：委托 LangChain4j {@link ChatModel}（同步）与
 * {@link StreamingChatModel}（流式）。
 * <p>每次调用通过 {@link AiCallLogger} 埋点，记录 token/耗时/费用至 ai_call_log。
 * <p><b>生产能力</b>：
 * <ul>
 *   <li>4 个方法均加 {@link SentinelResource}（资源名统一 {@code llm:chat}，共享熔断器状态），
 *       当 LLM 连续失败触发熔断或限流时，由 blockHandler 返回降级文案，不抛异常打断 RAG 流程。</li>
 *   <li>调用成功/失败上报 {@link AiCallHealthTracker}（"llm" 资源），供 {@code LlmHealthIndicator} 被动判定健康。</li>
 *   <li>熔断/限流触发时经 {@link AlertService} 发送 CRITICAL 告警（冷却去重）。</li>
 * </ul>
 * <p>异常语义：业务异常（API 错误等）仍向上抛出（保持原行为，且被 Sentinel 计入异常数熔断统计）；
 * 仅 Sentinel 主动拦截（熔断打开/限流）走 blockHandler 降级。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService {

    private static final String RESOURCE = "llm:chat";
    private static final String DEGRADED_REPLY = "服务繁忙，请稍后再试。";

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final AiProperties aiProperties;
    private final AiCallLogger aiCallLogger;
    private final AiCallHealthTracker healthTracker;
    private final AlertService alertService;
    private final TraceService traceService;

    @Override
    @SentinelResource(value = RESOURCE, blockHandler = "chatBlockHandler")
    public String chat(String prompt) {
        AiCallLogger.Tracer tracer = aiCallLogger.trace("rag_chat", "CHAT", modelName());
        Span span = traceService.startSpan("llm.chat", SpanType.LLM);
        try {
            ChatResponse resp = chatModel.chat(UserMessage.from(prompt));
            TokenUsage u = resp.tokenUsage();
            tracer.success(input(u), output(u));
            span.attribute("promptTokens", input(u)).attribute("completionTokens", output(u));
            healthTracker.recordSuccess("llm");
            span.success();
            return resp.aiMessage().text();
        } catch (RuntimeException e) {
            tracer.failure(e);
            span.error(e);
            healthTracker.recordFailure("llm");
            throw e;
        } finally {
            span.close();
        }
    }

    @Override
    @SentinelResource(value = RESOURCE, blockHandler = "chatWithSystemBlockHandler")
    public String chat(String systemPrompt, String userMessage) {
        AiCallLogger.Tracer tracer = aiCallLogger.trace("rag_chat", "CHAT", modelName());
        Span span = traceService.startSpan("llm.chat", SpanType.LLM);
        try {
            ChatResponse response = chatModel.chat(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userMessage));
            TokenUsage u = response.tokenUsage();
            tracer.success(input(u), output(u));
            span.attribute("promptTokens", input(u)).attribute("completionTokens", output(u));
            healthTracker.recordSuccess("llm");
            span.success();
            return response.aiMessage().text();
        } catch (RuntimeException e) {
            tracer.failure(e);
            span.error(e);
            healthTracker.recordFailure("llm");
            throw e;
        } finally {
            span.close();
        }
    }

    @Override
    @SentinelResource(value = RESOURCE, blockHandler = "chatWithHistoryBlockHandler")
    public String chat(String systemPrompt, List<ChatMessage> history, String userMessage) {
        AiCallLogger.Tracer tracer = aiCallLogger.trace("rag_chat", "CHAT", modelName());
        Span span = traceService.startSpan("llm.chat", SpanType.LLM);
        try {
            List<ChatMessage> messages = new ArrayList<>(history.size() + 2);
            messages.add(SystemMessage.from(systemPrompt));
            messages.addAll(history);
            messages.add(UserMessage.from(userMessage));
            ChatResponse response = chatModel.chat(messages);
            TokenUsage u = response.tokenUsage();
            tracer.success(input(u), output(u));
            span.attribute("promptTokens", input(u)).attribute("completionTokens", output(u));
            healthTracker.recordSuccess("llm");
            span.success();
            return response.aiMessage().text();
        } catch (RuntimeException e) {
            tracer.failure(e);
            span.error(e);
            healthTracker.recordFailure("llm");
            throw e;
        } finally {
            span.close();
        }
    }

    @Override
    @SentinelResource(value = RESOURCE, blockHandler = "streamChatBlockHandler")
    public void streamChat(String systemPrompt, List<ChatMessage> history, String userMessage,
                           StreamingChatResponseHandler handler) {
        // 流式调用：在 handler 完成回调中记录 token/费用，错误回调中记录失败
        AiCallLogger.Tracer tracer = aiCallLogger.trace("rag_chat", "CHAT", modelName());
        List<ChatMessage> messages = new ArrayList<>(history.size() + 2);
        messages.add(SystemMessage.from(systemPrompt));
        messages.addAll(history);
        messages.add(UserMessage.from(userMessage));
        streamingChatModel.chat(messages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onPartialResponse(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                TokenUsage u = completeResponse.tokenUsage();
                tracer.success(input(u), output(u));
                healthTracker.recordSuccess("llm");
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                tracer.failure(error);
                healthTracker.recordFailure("llm");
                handler.onError(error);
            }
        });
    }

    /* ==================== Sentinel blockHandler（熔断/限流降级） ==================== */
    // 签名须与原方法一致 + 末尾追加 BlockException 参数；返回降级文案，不抛异常打断 RAG 流程。

    public String chatBlockHandler(String prompt, BlockException ex) {
        return degradedReply(ex);
    }

    public String chatWithSystemBlockHandler(String systemPrompt, String userMessage, BlockException ex) {
        return degradedReply(ex);
    }

    public String chatWithHistoryBlockHandler(String systemPrompt, List<ChatMessage> history,
                                              String userMessage, BlockException ex) {
        return degradedReply(ex);
    }

    public void streamChatBlockHandler(String systemPrompt, List<ChatMessage> history, String userMessage,
                                       StreamingChatResponseHandler handler, BlockException ex) {
        // 流式降级：通过 handler 回调通知上层（SSE 端点会发送错误事件后 complete）
        alertService.alert(AlertLevel.CRITICAL, RESOURCE, "LLM流式调用降级",
                "blockType=" + ex.getClass().getSimpleName(), ex);
        handler.onError(new RuntimeException("LLM 服务熔断或限流，已降级"));
    }

    /**
     * 同步降级：记录 CRITICAL 告警（冷却去重），返回降级文案。
     */
    private String degradedReply(BlockException ex) {
        alertService.alert(AlertLevel.CRITICAL, RESOURCE, "LLM调用降级",
                "blockType=" + ex.getClass().getSimpleName(), ex);
        return DEGRADED_REPLY;
    }

    /* ==================== 工具 ==================== */

    private String modelName() {
        return aiProperties.getLlm().getModelName();
    }

    private static int input(TokenUsage u) {
        return u == null || u.inputTokenCount() == null ? 0 : u.inputTokenCount();
    }

    private static int output(TokenUsage u) {
        return u == null || u.outputTokenCount() == null ? 0 : u.outputTokenCount();
    }
}
