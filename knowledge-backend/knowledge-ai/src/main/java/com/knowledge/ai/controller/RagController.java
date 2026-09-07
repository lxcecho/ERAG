package com.knowledge.ai.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.dto.ChatRequest;
import com.knowledge.ai.dto.ChatResult;
import com.knowledge.ai.service.RagService;
import com.knowledge.common.result.Result;
import com.knowledge.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 问答接口：基于知识库的 RAG 问答，支持同步与 SSE 流式输出。
 * <p><b>限流保护</b>：{@code /ai/ask} 与 {@code /ai/stream} 均加 {@link SentinelResource}，
 * 默认规则见 {@code sentinel-flow-rules.json}（ask QPS=10，stream QPS=5），
 * 超限时 blockHandler 返回 4290 限流码（SSE 端点推送 error 事件后 complete）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Tag(name = "AI 问答接口")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final AiProperties aiProperties;

    @Operation(summary = "对话（同步，支持普通/RAG两种模式）")
    @PostMapping("/ask")
    @SentinelResource(value = "api:/ai/ask", blockHandler = "askBlockHandler")
    public Result<ChatResult> ask(@RequestBody @Valid ChatRequest request) {
        // RAG 模式要求 kbId 非空（限定检索范围）；普通模式 kbId 可空
        if (Boolean.TRUE.equals(request.getUseRag()) && request.getKbId() == null) {
            return Result.failed(ResultCode.BAD_REQUEST.getCode(), "RAG 对话必须指定知识库ID");
        }
        if (Boolean.TRUE.equals(request.getUseRag())) {
            return Result.success(ragService.ask(request.getQuestion(), request.getKbId(), request.getSessionId()));
        }
        return Result.success(ragService.plainChat(request.getQuestion(), request.getKbId(), request.getSessionId()));
    }

    @Operation(summary = "对话流式输出（SSE，支持普通/RAG两种模式）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SentinelResource(value = "api:/ai/stream", blockHandler = "streamBlockHandler")
    public SseEmitter stream(@RequestBody @Valid ChatRequest request) {
        // SSE 超时按流式配置设定，超时后容器自动关闭连接
        SseEmitter emitter = new SseEmitter((long) aiProperties.getLlm().getStreamTimeout() * 1000);
        // RAG 模式要求 kbId 非空；普通模式 kbId 可空
        if (Boolean.TRUE.equals(request.getUseRag()) && request.getKbId() == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("RAG 对话必须指定知识库ID"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }
        if (Boolean.TRUE.equals(request.getUseRag())) {
            ragService.askStream(request.getQuestion(), request.getKbId(), request.getSessionId(), emitter);
        } else {
            ragService.plainChatStream(request.getQuestion(), request.getKbId(), request.getSessionId(), emitter);
        }
        return emitter;
    }

    /* ==================== Sentinel 限流 blockHandler ==================== */

    /** 同步问答限流：返回 4290 限流码 */
    public Result<ChatResult> askBlockHandler(ChatRequest request, BlockException ex) {
        log.warn("[限流] /ai/ask 资源=api:/ai/ask type={}", ex.getClass().getSimpleName());
        return Result.failed(ResultCode.RATE_LIMITED.getCode(), ResultCode.RATE_LIMITED.getMessage());
    }

    /** SSE 流式问答限流：推送 error 事件后 complete（EventSource 客户端可正常解析降级消息） */
    public SseEmitter streamBlockHandler(ChatRequest request, BlockException ex) {
        log.warn("[限流] /ai/stream 资源=api:/ai/stream type={}", ex.getClass().getSimpleName());
        SseEmitter emitter = new SseEmitter(10_000L);
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Result.failed(ResultCode.RATE_LIMITED.getCode(), ResultCode.RATE_LIMITED.getMessage())));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }
}
