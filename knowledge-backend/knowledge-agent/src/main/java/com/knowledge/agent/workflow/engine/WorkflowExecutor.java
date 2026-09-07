package com.knowledge.agent.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.agent.workflow.definition.NodeDefinition;
import com.knowledge.agent.workflow.definition.WorkflowDefinitionModel;
import com.knowledge.agent.workflow.entity.WorkflowDefinition;
import com.knowledge.agent.workflow.entity.WorkflowNodeRun;
import com.knowledge.agent.workflow.entity.WorkflowTask;
import com.knowledge.agent.workflow.enums.NodeType;
import com.knowledge.agent.workflow.enums.WorkflowStatus;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Workflow 执行器（可控流程编排核心）。
 * <p>
 * 与自主式 {@code AgentExecutor} 的区别：
 * <ul>
 *   <li>流程声明式：按 {@link WorkflowDefinitionModel} 节点序列执行，而非固定 4 步流水线；</li>
 *   <li>节点级状态保存：每节点执行后持久化 context_json + current_node，崩溃可恢复；</li>
 *   <li>失败重试：节点 maxRetries 控制自动重试；用户可手动 retry 失败节点；</li>
 *   <li>人工介入：HUMAN 节点暂停流程（WAITING_HUMAN），审批后 resume 继续。</li>
 * </ul>
 * 节点执行委托 {@link NodeHandler} 策略（TOOL/LLM），复用 ToolExecutor 权限与审计链路。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
public class WorkflowExecutor {

    private final WorkflowDefinitionService definitionService;
    private final WorkflowTaskManager taskManager;
    private final ObjectMapper objectMapper;
    private final Map<NodeType, NodeHandler> handlers;

    /** 异步执行线程池（与 AgentExecutor 隔离，独立命名便于监控） */
    private final ExecutorService asyncPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "workflow-exec");
                t.setDaemon(true);
                return t;
            });

    public WorkflowExecutor(WorkflowDefinitionService definitionService,
                            WorkflowTaskManager taskManager,
                            ObjectMapper objectMapper,
                            List<NodeHandler> handlerList) {
        this.definitionService = definitionService;
        this.taskManager = taskManager;
        this.objectMapper = objectMapper;
        Map<NodeType, NodeHandler> map = new LinkedHashMap<>();
        for (NodeHandler h : handlerList) {
            map.put(h.type(), h);
        }
        this.handlers = map;
    }

    @PreDestroy
    public void shutdown() {
        asyncPool.shutdown();
        try {
            if (!asyncPool.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 对外入口 ====================

    /** 启动流程（从首节点执行） */
    public void start(Long taskId) {
        WorkflowTask task = taskManager.getById(taskId);
        assertNotTerminal(task);
        WorkflowDefinition def = definitionService.getById(task.getDefinitionId());
        WorkflowDefinitionModel model = definitionService.toModel(def);
        runFrom(task, model, null, null);
    }

    /**
     * 人工审批恢复（HUMAN 节点暂停后调用）。
     *
     * @param approved      是否通过
     * @param approverUserId 审批人
     * @param comment        审批意见（同时作为节点输入写入上下文 outputKey）
     */
    public void resume(Long taskId, boolean approved, Long approverUserId, String comment) {
        WorkflowTask task = taskManager.getById(taskId);
        if (!WorkflowStatus.WAITING_HUMAN.name().equals(task.getStatus())) {
            throw new BizException("任务非等待审批态，无法恢复: status=" + task.getStatus());
        }
        WorkflowDefinition def = definitionService.getById(task.getDefinitionId());
        WorkflowDefinitionModel model = definitionService.toModel(def);
        ApprovalInput approval = new ApprovalInput(approved, approverUserId, comment);
        runFrom(task, model, task.getCurrentNode(), approval);
    }

    /** 手动重试失败节点（从最近一个 FAILED 节点重新执行） */
    public void retry(Long taskId) {
        WorkflowTask task = taskManager.getById(taskId);
        if (!WorkflowStatus.FAILED.name().equals(task.getStatus())) {
            throw new BizException("仅 FAILED 任务可重试: status=" + task.getStatus());
        }
        WorkflowDefinition def = definitionService.getById(task.getDefinitionId());
        WorkflowDefinitionModel model = definitionService.toModel(def);

        String failedNodeId = findLastFailedNodeId(taskId);
        if (failedNodeId == null) {
            // 无明确失败节点，从头开始
            runFrom(task, model, null, null);
        } else {
            runFrom(task, model, failedNodeId, null);
        }
    }

    /** 取消流程 */
    public void cancel(Long taskId, String reason) {
        WorkflowTask task = taskManager.getById(taskId);
        if (WorkflowStatus.valueOf(task.getStatus()).isTerminal()) {
            throw new BizException("任务已终态，无法取消");
        }
        taskManager.cancelTask(taskId, reason);
        log.info("[Workflow] 任务={} 已取消: {}", taskId, reason);
    }

    /** 异步启动（Controller 入口用，立即返回，显式传播租户上下文） */
    public void executeAsync(Long taskId, Long tenantId) {
        asyncPool.submit(() -> runAsync(taskId, tenantId, () -> start(taskId)));
    }

    /** 异步审批恢复（记录审批后异步执行剩余节点） */
    public void resumeAsync(Long taskId, Long tenantId, boolean approved, Long approverUserId, String comment) {
        asyncPool.submit(() -> runAsync(taskId, tenantId,
                () -> resume(taskId, approved, approverUserId, comment)));
    }

    /** 异步重试失败节点 */
    public void retryAsync(Long taskId, Long tenantId) {
        asyncPool.submit(() -> runAsync(taskId, tenantId, () -> retry(taskId)));
    }

    /** 异步执行模板：统一传播租户上下文 + 异常兜底 + 清理 */
    private void runAsync(Long taskId, Long tenantId, Runnable action) {
        TenantContext.setTenantId(tenantId);
        try {
            action.run();
        } catch (Exception e) {
            log.error("[Workflow] 异步执行失败 task={}", taskId, e);
            try {
                taskManager.failTask(taskId, "ASYNC_EXCEPTION", e.getMessage());
            } catch (Exception ignore) {
                // 任务可能已终态
            }
        } finally {
            TenantContext.clear();
        }
    }

    // ==================== 核心编排循环 ====================

    private void runFrom(WorkflowTask task, WorkflowDefinitionModel model,
                         String startNodeId, ApprovalInput approval) {
        Long taskId = task.getId();
        Long tenantId = task.getTenantId();

        // 1. 构建/恢复上下文（断点恢复：从 context_json 反序列化变量）
        WorkflowContext ctx = new WorkflowContext(task);
        ctx.deserializeVariables(objectMapper, task.getContextJson());

        taskManager.updateStatus(taskId, WorkflowStatus.RUNNING);
        log.info("[Workflow] 任务={} 开始执行 从节点={} goal={}",
                taskId, startNodeId != null ? startNodeId : "START", truncate(task.getGoal(), 100));

        Map<String, NodeDefinition> idx = model.indexById();
        NodeDefinition node = resolveStartNode(model, idx, startNodeId);

        while (node != null) {
            // END：收尾
            if (node.getType() == NodeType.END) {
                String result = resolveResult(ctx, node);
                taskManager.completeTask(taskId, result);
                log.info("[Workflow] 任务={} 流程完成 结果长度={}", taskId, result.length());
                return;
            }
            // START：跳过
            if (node.getType() == NodeType.START) {
                node = model.nextOf(node);
                continue;
            }
            // HUMAN：人工审批门禁
            if (node.getType() == NodeType.HUMAN) {
                if (approval != null) {
                    // 恢复：回写审批结果并推进
                    WorkflowNodeRun waitRun = taskManager.findWaitingHumanRun(taskId, node.getId());
                    if (waitRun != null) {
                        taskManager.approveNodeRun(waitRun.getId(), approval.approved,
                                approval.approverUserId, approval.comment);
                    }
                    if (node.getOutputKey() != null) {
                        ctx.setVariable(node.getOutputKey(), approval.comment);
                    }
                    saveProgress(task, ctx, node.getId());
                    if (approval.approved) {
                        node = model.nextOf(node);
                    } else {
                        // 驳回：跳到 rejectNext，无则取消
                        node = (node.getRejectNext() != null && !node.getRejectNext().isBlank())
                                ? idx.get(node.getRejectNext()) : null;
                        if (node == null) {
                            taskManager.cancelTask(taskId, "人工审批驳回，流程终止");
                            log.info("[Workflow] 任务={} 审批驳回，已取消", taskId);
                            return;
                        }
                    }
                    approval = null; // 消费一次
                    continue;
                } else {
                    // 暂停：记录 WAITING_HUMAN 并返回
                    int runIndex = ctx.nextRunIndex();
                    WorkflowNodeRun run = taskManager.startNodeRun(tenantId, taskId, node.getId(),
                            node.getName(), node.getType().name(), runIndex, 1, null);
                    taskManager.waitHumanNodeRun(run.getId());
                    taskManager.pauseForHuman(taskId, node.getId());
                    saveProgress(task, ctx, node.getId());
                    log.info("[Workflow] 任务={} 暂停于人工节点={} 等待审批", taskId, node.getId());
                    return;
                }
            }

            // TOOL / LLM：带重试执行
            NodeExecutionResult r = executeWithRetry(node, ctx, task);
            if (!r.isSuccess()) {
                taskManager.failTask(taskId, "NODE_FAILED",
                        "节点[" + node.getId() + "]失败: " + r.getErrorMessage());
                log.warn("[Workflow] 任务={} 在节点={} 失败终止: {}", taskId, node.getId(), r.getErrorMessage());
                return;
            }
            if (node.getOutputKey() != null) {
                ctx.setVariable(node.getOutputKey(), r.getOutput());
            }
            ctx.addTokens(r.getTokensUsed());
            saveProgress(task, ctx, node.getId());
            node = model.nextOf(node);
        }

        // 无显式 END 节点而结束：以最后产物收尾
        String result = resolveResult(ctx, null);
        taskManager.completeTask(taskId, result);
        log.info("[Workflow] 任务={} 流程完成（无 END 节点）", taskId);
    }

    /** 节点带自动重试执行（maxRetries 控制额外尝试次数） */
    private NodeExecutionResult executeWithRetry(NodeDefinition node, WorkflowContext ctx, WorkflowTask task) {
        int maxRetries = Math.max(0, node.getMaxRetries());
        int attempt = 0;
        while (true) {
            attempt++;
            int runIndex = ctx.nextRunIndex();
            String inputJson = serializeInput(node, ctx);
            WorkflowNodeRun run = taskManager.startNodeRun(task.getTenantId(), task.getId(),
                    node.getId(), node.getName(), node.getType().name(), runIndex, attempt, inputJson);
            long start = System.currentTimeMillis();
            try {
                NodeHandler handler = handlers.get(node.getType());
                if (handler == null) {
                    throw new IllegalStateException("无节点处理器: " + node.getType());
                }
                NodeExecutionResult r = handler.handle(node, ctx);
                long dur = System.currentTimeMillis() - start;
                if (r.isSuccess()) {
                    taskManager.successNodeRun(run.getId(), serializeOutput(r.getOutput()),
                            r.getTokensUsed(), dur);
                    return r;
                }
                taskManager.failNodeRun(run.getId(), r.getErrorMessage());
                if (attempt <= maxRetries) {
                    log.warn("[Workflow] 节点={} 第{}次失败，自动重试: {}",
                            node.getId(), attempt, r.getErrorMessage());
                    continue;
                }
                return r;
            } catch (Exception e) {
                long dur = System.currentTimeMillis() - start;
                taskManager.failNodeRun(run.getId(), e.getMessage());
                log.error("[Workflow] 节点={} 第{}次执行异常", node.getId(), attempt, e);
                if (attempt <= maxRetries) {
                    continue;
                }
                return NodeExecutionResult.failure(e.getMessage());
            }
        }
    }

    // ==================== 辅助 ====================

    private NodeDefinition resolveStartNode(WorkflowDefinitionModel model,
                                            Map<String, NodeDefinition> idx, String startNodeId) {
        if (startNodeId != null) {
            NodeDefinition n = idx.get(startNodeId);
            if (n != null) {
                return n;
            }
        }
        try {
            return model.nextOf(model.startNode());
        } catch (IllegalStateException e) {
            // 无 START 节点：从首节点开始
            return model.getNodes().isEmpty() ? null : model.getNodes().get(0);
        }
    }

    /** 流程结果：END.outputKey 指定变量，否则取最后一个变量，再否则 goal */
    private String resolveResult(WorkflowContext ctx, NodeDefinition endNode) {
        String key = endNode != null ? endNode.getOutputKey() : null;
        if (key != null && ctx.hasVariable(key)) {
            return toText(ctx.getVariable(key));
        }
        // 兜底：最后一个变量
        Map<String, Object> vars = ctx.getVariables();
        if (!vars.isEmpty()) {
            Object last = vars.values().stream().reduce((a, b) -> b).orElse(null);
            return toText(last);
        }
        return "";
    }

    private String findLastFailedNodeId(Long taskId) {
        List<WorkflowNodeRun> runs = taskManager.listNodeRuns(taskId);
        for (int i = runs.size() - 1; i >= 0; i--) {
            if ("FAILED".equals(runs.get(i).getStatus())) {
                return runs.get(i).getNodeId();
            }
        }
        return null;
    }

    private void saveProgress(WorkflowTask task, WorkflowContext ctx, String currentNode) {
        taskManager.saveProgress(task.getId(), ctx.serializeVariables(objectMapper),
                currentNode, ctx.getRunIndex().get(), ctx.getTokensUsed(), task.getRetryCount() == null ? 0 : task.getRetryCount());
    }

    private String serializeInput(NodeDefinition node, WorkflowContext ctx) {
        try {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("type", node.getType().name());
            if (node.getType() == NodeType.TOOL) {
                snap.put("tool", node.getToolName());
                snap.put("args", node.getArguments());
            } else if (node.getType() == NodeType.LLM) {
                snap.put("prompt", node.getPromptTemplate());
            }
            return objectMapper.writeValueAsString(snap);
        } catch (Exception e) {
            return null;
        }
    }

    private String serializeOutput(Object output) {
        if (output == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            return String.valueOf(output);
        }
    }

    private static String toText(Object val) {
        if (val == null) {
            return "";
        }
        return val instanceof String s ? s : String.valueOf(val);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }

    private static void assertNotTerminal(WorkflowTask task) {
        WorkflowStatus status = WorkflowStatus.valueOf(task.getStatus());
        if (status.isTerminal()) {
            throw new BizException("任务已终态(" + status + ")，不可重复执行");
        }
    }

    /** 审批输入（内部传递） */
    private record ApprovalInput(boolean approved, Long approverUserId, String comment) {
    }
}
