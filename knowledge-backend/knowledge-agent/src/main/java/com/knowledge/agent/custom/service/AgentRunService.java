package com.knowledge.agent.custom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.agent.custom.dto.AgentRunVo;
import com.knowledge.agent.custom.dto.ChatStartRequest;
import com.knowledge.agent.custom.entity.AgentDefinition;
import com.knowledge.agent.custom.entity.AgentRun;
import com.knowledge.agent.custom.executor.CustomAgentExecutor;
import com.knowledge.agent.custom.mapper.AgentRunMapper;
import com.knowledge.agent.memory.MemoryService;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 自定义 Agent 运行编排门面。
 * <ul>
 *   <li>{@link #startChat}：单步流式（SSE），请求线程内准备上下文，LLM 流式回调落库；</li>
 *   <li>{@link #startRun}：多步异步执行（自定义线程池 + 显式租户上下文传播），立即返回 runId；</li>
 *   <li>{@link #streamRun}：多步 SSE 进度 watcher（对齐 AgentController.stream 模式）；</li>
 *   <li>{@link #page}/{@link #detail}：运行记录查询。</li>
 * </ul>
 * <p>log 模式运行结束后 best-effort 清理临时向量与文件（不阻断主流程）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunService {

    private static final Set<String> TERMINAL_STATUS = Set.of("COMPLETED", "FAILED", "CANCELED");

    private final AgentRunMapper runMapper;
    private final AgentDefinitionService definitionService;
    private final CustomAgentExecutor executor;
    private final LogUploadService logUploadService;
    private final MemoryService memoryService;

    /** 多步异步执行线程池（独立于 Web 请求线程，daemon） */
    private final ExecutorService multiPool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "custom-agent-exec");
        t.setDaemon(true);
        return t;
    });

    /** 单步流式：准备上下文 → LLM 流式 → SSE token/done；结果落库 agent_run。
     * <p>跨轮记忆：首轮后端生成 sessionId 并 SSE 回传（session 事件），后续轮次前端携带复用；
     * 每轮加载历史记忆注入提示词，结束后 recordInteraction 记录本轮交互（达阈值异步抽取摘要/事实）。
     */
    public void startChat(Long agentId, ChatStartRequest req, Long userId, Long tenantId, SseEmitter emitter) {
        AgentDefinition def = definitionService.getOwnedForRun(agentId, userId);
        Long sessionId = req.getSessionId() != null ? req.getSessionId() : IdWorker.getId();
        AgentRun run = createRun(def, req, userId, tenantId, sessionId);
        try {
            // 先回传会话ID（前端持久化，后续轮次携带以延续记忆）
            // 注意：用 String 发送（SseEmitter 对 String 原样输出，避免 Long 被 JSON 序列化为带引号字符串，
            // 前端会将其回传进请求体导致 JSON 非法）
            emitter.send(SseEmitter.event().name("session").data(String.valueOf(sessionId)));
            // 装配跨轮记忆（best-effort：空会话/加载失败均返回空文本，不阻断对话）
            String memoryText = memoryService.loadContext(sessionId, userId, tenantId, req.getQuestion()).toPromptText();
            CustomAgentExecutor.ContextInfo ctx = executor.prepareContext(def, req, userId);
            run.setInputType(ctx.getInputType());
            run.setKbId(ctx.getKbId());
            run.setContextRef(ctx.getContextRef());
            runMapper.updateById(run);
            executor.executeSingle(run, def, ctx, emitter, memoryText);
            // 流式结束（完成或失败）后清理临时向量（log 模式）
            cleanupAfterTerminal(run, ctx);
            // 跨轮记忆记录在 CustomAgentExecutor.onCompleteResponse 内完成（流式回调为异步线程，
            // 此时 run 状态才是终态且已持有完整回答）
        } catch (Exception e) {
            log.error("[自定义Agent] 单步启动失败 run={}", run.getId(), e);
            run.setStatus("FAILED");
            run.setErrorMsg(truncate(e.getMessage()));
            run.setFinishedTime(LocalDateTime.now());
            runMapper.updateById(run);
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
            } catch (Exception ignored) {
            }
            emitter.completeWithError(e);
        }
    }

    /** 多步异步执行：立即返回 runId，后台按步骤顺序执行 */
    public Long startRun(Long agentId, ChatStartRequest req, Long userId, Long tenantId) {
        AgentDefinition def = definitionService.getOwnedForRun(agentId, userId);
        Long sessionId = req.getSessionId() != null ? req.getSessionId() : IdWorker.getId();
        AgentRun run = createRun(def, req, userId, tenantId, sessionId);
        multiPool.submit(() -> {
            // 显式传播租户上下文（TTL 不会自动继承自定义线程池）
            TenantContext.setTenantId(tenantId);
            try {
                String memoryText = memoryService.loadContext(sessionId, userId, tenantId, req.getQuestion()).toPromptText();
                CustomAgentExecutor.ContextInfo ctx = executor.prepareContext(def, req, userId);
                // 记忆并入首步上下文（多步流程以 context 输入源起步）
                if (memoryText != null && !memoryText.isBlank()) {
                    String base = ctx.getContext() == null ? "" : ctx.getContext();
                    ctx.setContext(base + "\n\n【用户历史记忆（跨轮对话背景，与本问题无关时可忽略）】\n" + memoryText);
                }
                run.setInputType(ctx.getInputType());
                run.setKbId(ctx.getKbId());
                run.setContextRef(ctx.getContextRef());
                runMapper.updateById(run);
                executor.executeMulti(run, def, ctx);
                cleanupAfterTerminal(run, ctx);
                if ("COMPLETED".equals(run.getStatus()) && run.getResult() != null && !run.getResult().isBlank()) {
                    memoryService.recordInteraction(sessionId, userId, tenantId, req.getQuestion(), run.getResult());
                }
            } catch (Exception e) {
                log.error("[自定义Agent] 多步执行异常 run={}", run.getId(), e);
                run.setStatus("FAILED");
                run.setErrorMsg("准备上下文失败: " + truncate(e.getMessage()));
                run.setFinishedTime(LocalDateTime.now());
                runMapper.updateById(run);
            } finally {
                TenantContext.clear();
            }
        });
        return run.getId();
    }

    /** 多步 SSE 进度 watcher：每 1.5s 推送 run 快照，终态后推送 complete 并关闭 */
    public SseEmitter streamRun(Long runId, Long tenantId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        Thread watcher = new Thread(() -> {
            TenantContext.setTenantId(tenantId);
            try {
                while (true) {
                    AgentRun run = runMapper.selectById(runId);
                    if (run == null) {
                        emitter.complete();
                        return;
                    }
                    emitter.send(SseEmitter.event().name("progress").data(toVo(run)));
                    if (TERMINAL_STATUS.contains(run.getStatus())) {
                        emitter.send(SseEmitter.event().name("complete").data(toVo(run)));
                        emitter.complete();
                        return;
                    }
                    Thread.sleep(1500L);
                }
            } catch (Exception e) {
                log.warn("[自定义Agent] SSE 流异常 run={}: {}", runId, e.getMessage());
                emitter.completeWithError(e);
            } finally {
                TenantContext.clear();
            }
        }, "custom-agent-sse-" + runId);
        watcher.setDaemon(true);
        watcher.start();
        return emitter;
    }

    /** 分页查询当前用户运行记录（可按 agentId 过滤，供单 Agent 历史面板使用） */
    public IPage<AgentRunVo> page(long current, long size, Long userId, Long agentId) {
        Long tenantId = TenantContext.requiredTenantId();
        LambdaQueryWrapper<AgentRun> wrapper = new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getTenantId, tenantId)
                .eq(AgentRun::getUserId, userId);
        if (agentId != null) {
            wrapper.eq(AgentRun::getAgentId, agentId);
        }
        wrapper.orderByDesc(AgentRun::getCreateTime);
        Page<AgentRun> page = new Page<>(current, size);
        IPage<AgentRun> result = runMapper.selectPage(page, wrapper);
        // 批量填充 agentName（避免 N+1）
        Set<Long> agentIds = result.getRecords().stream().map(AgentRun::getAgentId).collect(Collectors.toSet());
        Map<Long, String> names = agentIds.isEmpty() ? Map.of()
                : definitionService.listNames(agentIds);
        return result.convert(r -> toVoWithAgentName(r, names));
    }

    /** 运行详情（含 agentName） */
    public AgentRunVo detail(Long runId, Long userId) {
        AgentRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BizException("运行记录不存在");
        }
        if (!run.getUserId().equals(userId)) {
            throw new BizException(403, "无权查看该运行记录");
        }
        AgentRunVo vo = toVo(run);
        vo.setAgentName(definitionService.getByIdQuiet(run.getAgentId()));
        return vo;
    }

    private AgentRun createRun(AgentDefinition def, ChatStartRequest req, Long userId, Long tenantId, Long sessionId) {
        AgentRun run = new AgentRun();
        run.setTenantId(tenantId);
        run.setUserId(userId);
        run.setAgentId(def.getId());
        run.setSessionId(sessionId);
        run.setInputType(req.getInputType() != null ? req.getInputType() : def.getSourceMode());
        run.setKbId(req.getKbId() != null ? req.getKbId() : def.getKbId());
        run.setQuestion(req.getQuestion());
        run.setStatus("CREATED");
        runMapper.insert(run);
        return run;
    }

    /** log 模式运行结束后清理临时向量与文件（仅终态，best-effort） */
    private void cleanupAfterTerminal(AgentRun run, CustomAgentExecutor.ContextInfo ctx) {
        if (run != null && ctx != null && "log".equals(ctx.getInputType()) && ctx.getContextRef() != null) {
            logUploadService.cleanupUpload(ctx.getContextRef());
        }
    }

    private AgentRunVo toVo(AgentRun run) {
        AgentRunVo vo = new AgentRunVo();
        BeanUtils.copyProperties(run, vo);
        return vo;
    }

    private AgentRunVo toVoWithAgentName(AgentRun run, Map<Long, String> names) {
        AgentRunVo vo = toVo(run);
        vo.setAgentName(names.getOrDefault(run.getAgentId(), null));
        return vo;
    }

    private String truncate(String msg) {
        if (msg == null) {
            return "未知错误";
        }
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
