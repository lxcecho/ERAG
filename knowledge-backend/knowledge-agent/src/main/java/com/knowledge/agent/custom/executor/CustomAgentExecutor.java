package com.knowledge.agent.custom.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.agent.custom.config.CustomAgentProperties;
import com.knowledge.agent.custom.dto.AgentStepRunVo;
import com.knowledge.agent.custom.dto.ChatStartRequest;
import com.knowledge.agent.custom.dto.StepDefinition;
import com.knowledge.agent.custom.entity.AgentDefinition;
import com.knowledge.agent.custom.entity.AgentRun;
import com.knowledge.agent.custom.mapper.AgentRunMapper;
import com.knowledge.agent.custom.service.LogUploadService;
import com.knowledge.agent.memory.MemoryService;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.prompt.PromptTemplates;
import com.knowledge.ai.service.EmbeddingService;
import com.knowledge.ai.service.LLMService;
import com.knowledge.ai.service.MilvusService;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.permission.service.DocPermissionService;
import com.knowledge.kb.service.KbPermissionService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 自定义 Agent 执行器：上下文准备 + 单步流式 / 多步流程执行。
 * <p>职责：
 * <ul>
 *   <li>{@link #prepareContext}：按输入类型（kb/content/log）组装编号上下文；
 *       kb 复用向量检索 + 文档级权限后过滤；log 小文件全文 / 大文件切片检索；content 直接注入（截断）；</li>
 *   <li>{@link #executeSingle}：单步 LLM 流式调用，逐 token 推送 SSE；</li>
 *   <li>{@link #executeMulti}：多步流程顺序执行，步骤产物逐步传递（{@code {outputKey}} 占位符），
 *       末步输出即最终 result；任一步失败标记 FAILED 并保留已完成步骤。</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomAgentExecutor {

    private final LLMService llmService;
    private final EmbeddingService embeddingService;
    private final MilvusService milvusService;
    private final DocPermissionService docPermissionService;
    private final KbPermissionService kbPermissionService;
    private final LogUploadService logUploadService;
    private final AgentRunMapper runMapper;
    private final CustomAgentProperties props;
    private final ObjectMapper objectMapper;
    private final MemoryService memoryService;

    /**
     * 上下文准备结果：注入文本 + 实际输入信息（供 agent_run 落库）。
     */
    @Data
    @AllArgsConstructor
    public static class ContextInfo {
        /** 注入 LLM 的上下文文本 */
        private String context;
        /** 实际输入类型 kb/content/log */
        private String inputType;
        /** 实际检索知识库ID（input_type=kb） */
        private Long kbId;
        /** context_ref（content 原文摘要或 log fileRef） */
        private String contextRef;
    }

    /** 按输入类型准备上下文（含权限校验） */
    public ContextInfo prepareContext(AgentDefinition def, ChatStartRequest req, Long userId) {
        String inputType = req.getInputType() != null ? req.getInputType() : def.getSourceMode();
        return switch (inputType) {
            case "kb" -> prepareKbContext(def, req, userId);
            case "content" -> prepareContentContext(def, req);
            case "log" -> prepareLogContext(def, req);
            // 纯模型回答：不注入任何上下文（对应对话框关闭知识库检索开关）
            case "plain" -> new ContextInfo("", "plain", null, null);
            default -> throw new BizException("输入类型非法：" + inputType);
        };
    }

    private ContextInfo prepareKbContext(AgentDefinition def, ChatStartRequest req, Long userId) {
        Long kbId = req.getKbId() != null ? req.getKbId() : def.getKbId();
        if (kbId == null) {
            throw new BizException("知识库模式必须指定 kbId");
        }
        kbPermissionService.checkViewer(kbId, userId);
        List<RetrievalResult> results = retrieve(kbId, req.getQuestion(), userId);
        String context = results.isEmpty() ? "" : buildContext(results);
        return new ContextInfo(context, "kb", kbId, null);
    }

    private ContextInfo prepareContentContext(AgentDefinition def, ChatStartRequest req) {
        String content = req.getContent();
        if (content == null || content.isBlank()) {
            throw new BizException("自定义内容模式必须提供 content");
        }
        if (content.length() > props.getContentLimit()) {
            content = content.substring(0, props.getContentLimit());
        }
        return new ContextInfo(content, "content", null, content.substring(0, Math.min(200, content.length())));
    }

    private ContextInfo prepareLogContext(AgentDefinition def, ChatStartRequest req) {
        String fileRef = req.getFileRef();
        if (fileRef == null || fileRef.isBlank()) {
            throw new BizException("日志模式必须提供 fileRef（请先调用上传接口）");
        }
        // 全文读取注入（上传时未切片向量化），超长按 logContextLimit 截断
        String text = logUploadService.readText(fileRef);
        if (text.length() > props.getLogContextLimit()) {
            log.info("[自定义Agent] 文件超长，截断注入 chars={} limit={} ref={}",
                    text.length(), props.getLogContextLimit(), fileRef);
            text = text.substring(0, props.getLogContextLimit());
        }
        return new ContextInfo(text, "log", null, fileRef);
    }

    /** 知识库检索：向量检索 + 文档级权限后过滤 + topK 截断 */
    private List<RetrievalResult> retrieve(Long kbId, String question, Long userId) {
        Embedding queryEmbedding = embeddingService.embed(question);
        int initTopK = Math.max(props.getKbTopK() * 4, 50);
        List<RetrievalResult> results = milvusService.search(queryEmbedding, kbId, initTopK);
        Set<Long> allowDocIds = docPermissionService.filterDocIds(userId, kbId,
                results.stream().map(RetrievalResult::getDocumentId).filter(Objects::nonNull).toList());
        results = results.stream()
                .filter(r -> r.getDocumentId() != null && allowDocIds.contains(r.getDocumentId()))
                .toList();
        if (results.size() > props.getKbTopK()) {
            results = results.subList(0, props.getKbTopK());
        }
        return results;
    }

    /** 检索结果 → 编号参考资料上下文文本 */
    private String buildContext(List<RetrievalResult> results) {
        List<String> texts = results.stream().map(RetrievalResult::getText).toList();
        List<String> sources = results.stream()
                .map(RetrievalResult::getSource).map(s -> s == null ? "未知" : s).collect(Collectors.toList());
        return PromptTemplates.buildContext(texts, sources);
    }

    /** 单步流式执行：逐 token 推送，完成后落库。
     * @param memoryText 跨轮记忆文本（MemoryService.loadContext().toPromptText()，可为 null/空）
     */
    public void executeSingle(AgentRun run, AgentDefinition def, ContextInfo ctx, SseEmitter emitter, String memoryText) {
        String systemPrompt = CustomPromptRenderer.renderSystemPrompt(def.getSystemPrompt(), ctx.getContext(), run.getQuestion());
        if (memoryText != null && !memoryText.isBlank()) {
            systemPrompt += "\n\n【用户历史记忆（跨轮对话背景，与本问题无关时可忽略）】\n" + memoryText;
        }
        run.setStatus("RUNNING");
        runMapper.updateById(run);

        StringBuilder full = new StringBuilder();
        try {
            emitter.send(SseEmitter.event().name("status").data("generating"));
            llmService.streamChat(systemPrompt, List.of(), run.getQuestion(), new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partial) {
                    full.append(partial);
                    try {
                        emitter.send(SseEmitter.event().name("token").data(partial));
                    } catch (IOException e) {
                        log.warn("[自定义Agent] token 推送失败 run={}: {}", run.getId(), e.getMessage());
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    run.setResult(full.toString());
                    run.setStatus("COMPLETED");
                    run.setFinishedTime(LocalDateTime.now());
                    runMapper.updateById(run);
                    // 记录本轮交互（跨轮记忆）：流式回调为异步线程，需显式传播租户上下文；
                    // best-effort，失败仅告警不阻断主流程
                    recordInteraction(run, full.toString());
                    try {
                        emitter.send(SseEmitter.event().name("done").data(run.getId()));
                        emitter.complete();
                    } catch (IOException e) {
                        log.warn("[自定义Agent] done 推送失败 run={}", run.getId());
                    }
                    log.info("[自定义Agent] 单步完成 run={} answerLen={}", run.getId(), full.length());
                }

                @Override
                public void onError(Throwable error) {
                    run.setStatus("FAILED");
                    run.setErrorMsg(truncateError(error.getMessage()));
                    run.setFinishedTime(LocalDateTime.now());
                    runMapper.updateById(run);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                    } catch (IOException ignored) {
                    }
                    emitter.completeWithError(error);
                }
            });
        } catch (Exception e) {
            log.error("[自定义Agent] 单步执行异常 run={}", run.getId(), e);
            run.setStatus("FAILED");
            run.setErrorMsg(truncateError(e.getMessage()));
            run.setFinishedTime(LocalDateTime.now());
            runMapper.updateById(run);
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
            } catch (IOException ignored) {
            }
            emitter.completeWithError(e);
        }
    }

    /** 多步流程执行（异步线程内调用，调用方负责租户上下文传播） */
    public void executeMulti(AgentRun run, AgentDefinition def, ContextInfo ctx) {
        List<StepDefinition> steps = parseSteps(def.getSteps());
        List<AgentStepRunVo> stepRuns = steps.stream().map(s -> {
            AgentStepRunVo vo = new AgentStepRunVo();
            vo.setStepName(s.getStepName());
            vo.setOutputKey(s.getOutputKey());
            vo.setStatus("PENDING");
            return vo;
        }).collect(Collectors.toCollection(ArrayList::new));

        run.setStatus("RUNNING");
        updateSteps(run, stepRuns);

        Map<String, String> stepVars = new LinkedHashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            StepDefinition step = steps.get(i);
            stepRuns.get(i).setStatus("RUNNING");
            updateSteps(run, stepRuns);
            try {
                String context = "context".equals(step.getInputFrom()) ? ctx.getContext() : null;
                String prompt = CustomPromptRenderer.renderStepPrompt(step.getPrompt(), context, run.getQuestion(), stepVars);
                String output = llmService.chat(prompt);
                stepVars.put(step.getOutputKey(), output);
                stepRuns.get(i).setStatus("SUCCESS");
                stepRuns.get(i).setOutput(output);
                updateSteps(run, stepRuns);
                if (i == steps.size() - 1) {
                    run.setResult(output);
                }
                log.info("[自定义Agent] 步骤完成 run={} step={}/{} out={}", run.getId(), i + 1, steps.size(), step.getStepName());
            } catch (RuntimeException e) {
                stepRuns.get(i).setStatus("FAILED");
                stepRuns.get(i).setOutput(truncateError(e.getMessage()));
                updateSteps(run, stepRuns);
                run.setStatus("FAILED");
                run.setErrorMsg("步骤[" + step.getStepName() + "]执行失败: " + truncateError(e.getMessage()));
                run.setFinishedTime(LocalDateTime.now());
                runMapper.updateById(run);
                log.warn("[自定义Agent] 多步执行失败 run={} step={}", run.getId(), step.getStepName(), e);
                return;
            }
        }
        run.setStatus("COMPLETED");
        run.setFinishedTime(LocalDateTime.now());
        runMapper.updateById(run);
        log.info("[自定义Agent] 多步完成 run={} steps={}", run.getId(), steps.size());
    }

    /** 解析多步流程定义（校验失败抛业务异常） */
    public List<StepDefinition> parseSteps(String stepsJson) {
        try {
            return objectMapper.readValue(stepsJson, new TypeReference<List<StepDefinition>>() {
            });
        } catch (Exception e) {
            throw new BizException("多步骤流程 JSON 解析失败: " + e.getMessage());
        }
    }

    /** 记录一轮交互到记忆中心（跨轮记忆）。流式回调在异步线程执行，需显式传播租户上下文。 */
    private void recordInteraction(AgentRun run, String answer) {
        if (run.getSessionId() == null || answer == null || answer.isBlank()) {
            return;
        }
        TenantContext.setTenantId(run.getTenantId());
        try {
            memoryService.recordInteraction(run.getSessionId(), run.getUserId(), run.getTenantId(),
                    run.getQuestion(), answer);
        } catch (Exception e) {
            log.warn("[自定义Agent] 记录记忆失败 run={} err={}", run.getId(), e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    /** 更新 steps_result 快照（SSE progress 轮询读取） */
    private void updateSteps(AgentRun run, List<AgentStepRunVo> stepRuns) {
        try {
            run.setStepsResult(objectMapper.writeValueAsString(stepRuns));
        } catch (Exception e) {
            log.warn("[自定义Agent] steps_result 序列化失败 run={}", run.getId());
        }
        runMapper.updateById(run);
    }

    private String truncateError(String msg) {
        if (msg == null) {
            return "未知错误";
        }
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
