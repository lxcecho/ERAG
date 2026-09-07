package com.knowledge.agent.orchestrator;

import com.knowledge.agent.config.AgentProperties;
import com.knowledge.agent.engine.Agent;
import com.knowledge.agent.engine.AgentContext;
import com.knowledge.agent.engine.AgentResult;
import com.knowledge.agent.engine.AgentType;
import com.knowledge.agent.engine.ArtifactType;
import com.knowledge.agent.entity.AgentTask;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Agent 编排执行器（v3-4 核心）：以 {@link AgentGraph} 编排 Agent 节点，带重试/硬超时/Saga 补偿回滚。
 * <p>
 * 与 {@code engine.AgentExecutor}（线性流水线，fail-fast）并列共存，同名不同包：
 * <ul>
 *   <li>图编排：按 {@link AgentGraph#nextOf} 遍历节点（顺序 + next 分支跳转），非固定 4 步；</li>
 *   <li>节点级重试：{@link AgentNode#getMaxRetries()} 控制额外尝试，backoff 线性退避；</li>
 *   <li>节点级硬超时：{@code Future.get(timeoutMs)} + {@code cancel(true)} 中断（best-effort）；</li>
 *   <li>Saga 回滚：节点终态失败时，逆序执行已 SUCCESS 节点的 {@link Compensation}（best-effort，不阻断链）；</li>
 *   <li>持久化：节点执行/补偿记 agent_node_run/agent_compensation，任务级复用 agent_task。</li>
 * </ul>
 * 复用 {@link AgentContext}/{@link AgentResult} 与 {@link OrchestratorTaskManager}；Agent 无状态，产物经 Context 传递。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service("orchestratorAgentExecutor")
public class AgentExecutor {

    private final List<Agent> agents;
    private final OrchestratorTaskManager taskManager;
    private final AgentProperties props;
    private final ExecutorService orchestratorPool;

    /** Agent 角色索引（type → 实例），@PostConstruct 构建 */
    private Map<AgentType, Agent> agentMap;

    /** 默认编排图（由注册的 Agent 按 @Order 自动构建） */
    private AgentGraph defaultGraph;

    /** 自定义图注册表（code → graph），扩展点 */
    private final Map<String, AgentGraph> customGraphs = new ConcurrentHashMap<>();

    public AgentExecutor(List<Agent> agents,
                         OrchestratorTaskManager taskManager,
                         AgentProperties props,
                         ExecutorService orchestratorPool) {
        this.agents = agents;
        this.taskManager = taskManager;
        this.props = props;
        this.orchestratorPool = orchestratorPool;
    }

    @PostConstruct
    void init() {
        Map<AgentType, Agent> map = new LinkedHashMap<>();
        for (Agent a : agents) {
            map.put(a.type(), a);
        }
        this.agentMap = map;
        this.defaultGraph = buildDefaultGraph();
        log.info("[Orchestrator] 注册 {} 个 Agent，默认图节点: {}",
                map.size(), defaultGraph.getNodes().stream().map(AgentNode::getNodeId).toList());
    }

    // ==================== 对外入口 ====================

    /** 默认图同步执行（测试/同步场景） */
    public AgentTask execute(Long taskId) {
        return execute(taskId, defaultGraph);
    }

    /** 指定图同步执行（自定义图/测试用） */
    public AgentTask execute(Long taskId, AgentGraph graph) {
        AgentTask task = taskManager.getById(taskId);
        assertNotTerminal(task);
        if (graph == null) {
            graph = defaultGraph;
        }
        graph.validate();

        AgentContext ctx = new AgentContext(task, props);
        taskManager.updateStatus(taskId, OrchestratorStatus.RUNNING);
        long taskStart = System.currentTimeMillis();
        log.info("[Orchestrator] task={} 开始编排，目标={} 图={}",
                taskId, truncate(ctx.getGoal(), 100), graph.getCode());

        Deque<ExecutedNode> executed = new ArrayDeque<>();
        int runIndex = 0;
        AgentNode node = graph.startNode();

        while (node != null) {
            runIndex++;
            // 步数预算
            if (runIndex > props.getMaxSteps()) {
                return finishFail(taskId, ctx, executed, "MAX_STEPS",
                        "超过最大步数 " + props.getMaxSteps(), graph, runIndex, node);
            }
            // 时长预算（节点间校验兜底）
            if (System.currentTimeMillis() - taskStart > props.getTaskTimeoutSeconds() * 1000L) {
                return finishFail(taskId, ctx, executed, "TIMEOUT",
                        "超过任务时长预算 " + props.getTaskTimeoutSeconds() + "s", graph, runIndex, node);
            }

            AgentResult result = executeWithRetry(node, ctx, task, runIndex, executed);
            if (!result.isSuccess()) {
                return finishFail(taskId, ctx, executed, "NODE_FAILED",
                        node.getNodeId() + ":" + result.getErrorMessage(), graph, runIndex, node);
            }

            // 产物写入 Context + 持久化
            String artifactKey = result.getArtifactKey() != null
                    ? result.getArtifactKey() : node.resolveArtifactKey();
            if (artifactKey != null && result.getArtifact() != null) {
                ctx.putArtifact(artifactKey, result.getArtifact());
                Long runId = executed.isEmpty() ? null : executed.peek().runId;
                taskManager.recordArtifact(task.getTenantId(), taskId, runId, artifactKey, result.getArtifact());
            }
            // token 预算
            if (ctx.getTokensUsed() > props.getMaxTokensPerTask()) {
                return finishFail(taskId, ctx, executed, "TOKEN_BUDGET_EXCEEDED",
                        "token 累计 " + ctx.getTokensUsed() + " 超过预算 " + props.getMaxTokensPerTask(),
                        graph, runIndex, node);
            }

            // 动态路由：优先使用 Agent 建议的 nextNodeId（校验合法性），无建议走默认 nextOf()
            node = resolveNextNode(graph, node, result, executed);
        }

        // 到达 END：以 REPORT 产物收尾
        Object report = ctx.getArtifact(ArtifactType.REPORT.name());
        String reportText = report != null ? report.toString() : "";
        taskManager.updateTokenUsage(taskId, ctx.getTokensUsed());
        taskManager.completeTask(taskId, reportText);
        log.info("[Orchestrator] task={} 编排完成 (累计token={})", taskId, ctx.getTokensUsed());
        return taskManager.getById(taskId);
    }

    /** 异步执行（Controller 入口用，显式传播租户上下文） */
    public void executeAsync(Long taskId, Long tenantId) {
        orchestratorPool.submit(() -> {
            TenantContext.setTenantId(tenantId);
            try {
                execute(taskId);
            } catch (Exception e) {
                log.error("[Orchestrator] 异步执行失败 task={}", taskId, e);
                try {
                    taskManager.failTask(taskId, "ASYNC_EXCEPTION", e.getMessage());
                } catch (Exception ignore) {
                    // 任务可能已终态
                }
            } finally {
                TenantContext.clear();
            }
        });
    }

    /** 取消编排（非终态任务） */
    public void cancel(Long taskId, String reason) {
        AgentTask task = taskManager.getById(taskId);
        OrchestratorStatus st = OrchestratorStatus.valueOf(task.getStatus());
        if (st.isTerminal()) {
            throw new BizException("任务已终态(" + st + ")，无法取消");
        }
        taskManager.cancelTask(taskId, reason);
        log.info("[Orchestrator] task={} 已取消: {}", taskId, reason);
    }

    // ==================== 注册（自定义图） ====================

    /** 注册自定义编排图（覆盖默认需显式指定 code） */
    public void registerGraph(String code, AgentGraph graph) {
        graph.validate();
        customGraphs.put(code, graph);
        log.info("[Orchestrator] 注册自定义图 code={} 节点数={}", code, graph.getNodes().size());
    }

    public AgentGraph getGraph(String code) {
        return customGraphs.get(code);
    }

    public AgentGraph defaultGraph() {
        return defaultGraph;
    }

    // ==================== 节点执行（重试 + 硬超时） ====================

    /**
     * 节点带自动重试 + 硬超时执行。
     *
     * @return 成功返回 AgentResult.success；重试耗尽返回失败 AgentResult（触发回滚）
     */
    private AgentResult executeWithRetry(AgentNode node, AgentContext ctx, AgentTask task,
                                        int runIndex, Deque<ExecutedNode> executed) {
        Agent agent = agentMap.get(node.getAgentType());
        if (agent == null) {
            throw new IllegalStateException("未注册的 Agent 类型: " + node.getAgentType());
        }
        int maxRetries = Math.max(0, node.getMaxRetries());
        long backoff = Math.max(0, node.getRetryBackoffMs());
        long timeoutMs = Math.max(0, node.getTimeoutMs());

        int attempt = 0;
        while (true) {
            attempt++;
            AgentNodeRunHolder holder = new AgentNodeRunHolder();
            var run = taskManager.startNodeRun(task.getTenantId(), task.getId(), node.getNodeId(),
                    node.getName(), node.getAgentType().name(), runIndex, attempt, timeoutMs,
                    buildInputSummary(ctx, node));
            holder.runId = run.getId();

            long start = System.currentTimeMillis();
            AgentResult result = null;
            String failMsg = null;
            boolean timedOut = false;

            try {
                if (timeoutMs > 0) {
                    Future<AgentResult> future = orchestratorPool.submit(() -> agent.execute(ctx));
                    try {
                        result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException te) {
                        future.cancel(true);
                        timedOut = true;
                        failMsg = "节点硬超时 " + timeoutMs + "ms";
                    } catch (ExecutionException ee) {
                        failMsg = rootMessage(ee.getCause() != null ? ee.getCause() : ee);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        failMsg = "节点执行被中断";
                    }
                } else {
                    result = agent.execute(ctx);
                }
            } catch (Exception e) {
                failMsg = rootMessage(e);
            }
            long duration = System.currentTimeMillis() - start;

            // 成功
            if (result != null && result.isSuccess()) {
                taskManager.successNodeRun(holder.runId, result.getSummary(),
                        result.getTokensUsed(), duration);
                ctx.addTokens(result.getTokensUsed());
                // 记录已成功节点（回滚逆序用）—— runId 关联最新成功执行
                executed.push(new ExecutedNode(node.getNodeId(), node.getAgentType(), holder.runId));
                log.info("[Orchestrator] task={} 节点{} {} 成功(attempt={}, {}ms, 累计token={})",
                        task.getId(), runIndex, node.getNodeId(), attempt, duration, ctx.getTokensUsed());
                return result;
            }

            // 失败/超时：重试 or 耗尽
            String errMsg = result != null ? result.getErrorMessage() : failMsg;
            if (attempt <= maxRetries) {
                taskManager.retryNodeRun(holder.runId, errMsg);
                log.warn("[Orchestrator] task={} 节点{} {} 第{}次失败({})，重试中",
                        task.getId(), runIndex, node.getNodeId(), attempt, errMsg);
                sleep(backoff * attempt);
                continue;
            }
            // 重试耗尽：终态失败
            if (timedOut) {
                taskManager.timeoutNodeRun(holder.runId, errMsg, duration);
            } else {
                taskManager.failNodeRun(holder.runId, errMsg, duration);
            }
            log.warn("[Orchestrator] task={} 节点{} {} 终态失败(attempt={})：{}",
                    task.getId(), runIndex, node.getNodeId(), attempt, errMsg);
            return result != null ? result : AgentResult.failure(errMsg);
        }
    }

    // ==================== Saga 补偿回滚 ====================

    /**
     * 逆序执行已 SUCCESS 节点的补偿（best-effort）。
     * <p>无副作用 Agent（未实现 Compensable）记一条空补偿 SUCCESS 以保证审计完整；
     * 补偿抛异常不阻断链，仅记 FAILED。
     */
    private void rollback(Long taskId, Long tenantId, Deque<ExecutedNode> executed, AgentContext ctx) {
        if (executed.isEmpty()) {
            log.info("[Orchestrator] task={} 无已成功节点，跳过补偿", taskId);
            return;
        }
        taskManager.updateStatus(taskId, OrchestratorStatus.ROLLING_BACK);
        log.info("[Orchestrator] task={} 触发补偿回滚，待补偿节点 {} 个", taskId, executed.size());

        while (!executed.isEmpty()) {
            ExecutedNode en = executed.pop();
            Agent agent = agentMap.get(en.agentType);
            var c = taskManager.startCompensation(tenantId, taskId, en.nodeId, en.runId,
                    "补偿节点 " + en.nodeId);
            long start = System.currentTimeMillis();
            try {
                Compensation comp = (agent instanceof Compensable cb) ? cb.compensation(ctx) : null;
                if (comp == null) {
                    taskManager.successCompensation(c.getId(), 0L);
                    log.info("[Orchestrator] task={} 节点={} 无补偿动作(只读 Agent)，记空补偿",
                            taskId, en.nodeId);
                } else {
                    comp.execute(ctx);
                    taskManager.successCompensation(c.getId(), System.currentTimeMillis() - start);
                    log.info("[Orchestrator] task={} 节点={} 补偿成功: {}",
                            taskId, en.nodeId, comp.description());
                }
            } catch (Exception e) {
                taskManager.failCompensation(c.getId(), rootMessage(e), System.currentTimeMillis() - start);
                log.warn("[Orchestrator] task={} 节点={} 补偿失败(best-effort，继续): {}",
                        taskId, en.nodeId, rootMessage(e));
            }
        }
    }

    // ==================== 内部工具 ====================

    /** 终态失败统一收尾：回滚（若启用）+ 跳过下游未执行节点（审计）+ 标记 FAILED */
    private AgentTask finishFail(Long taskId, AgentContext ctx, Deque<ExecutedNode> executed,
                                 String errorCode, String errorMsg, AgentGraph graph,
                                 int runIndex, AgentNode failedNode) {
        AgentTask task = taskManager.getById(taskId);
        if (props.getOrchestrator().isRollbackEnabled()) {
            rollback(taskId, task.getTenantId(), executed, ctx);
        }
        // 跳过失败节点之后的未执行下游节点（审计完整）
        AgentNode skip = graph.nextOf(failedNode);
        int skipIndex = runIndex + 1;
        while (skip != null) {
            taskManager.skipNodeRun(task.getTenantId(), taskId, skip.getNodeId(),
                    skip.getName(), skip.getAgentType().name(), skipIndex);
            skipIndex++;
            skip = graph.nextOf(skip);
        }
        taskManager.updateTokenUsage(taskId, ctx.getTokensUsed());
        taskManager.failTask(taskId, errorCode, errorMsg);
        log.warn("[Orchestrator] task={} 编排失败终止: {}", taskId, errorMsg);
        return taskManager.getById(taskId);
    }

    /**
     * 解析下一节点：优先使用 Agent 建议的 suggestedNextNodeId，校验合法性后跳转。
     * <p>合法性校验：
     * <ul>
     *   <li>nodeId 在图中存在</li>
     *   <li>未跳到已执行过的节点（防环）</li>
     * </ul>
     * 不合法时记录警告，降级为默认 nextOf()。
     */
    private AgentNode resolveNextNode(AgentGraph graph, AgentNode current,
                                       AgentResult result, Deque<ExecutedNode> executed) {
        String suggested = result.getSuggestedNextNodeId();
        if (suggested == null || suggested.isBlank()) {
            return graph.nextOf(current);
        }
        // 校验 nodeId 在图中存在
        Map<String, AgentNode> index = graph.indexById();
        AgentNode target = index.get(suggested);
        if (target == null) {
            log.warn("[Orchestrator] Agent 建议的 nextNodeId={} 在图中不存在，降级为默认顺序", suggested);
            return graph.nextOf(current);
        }
        // 校验未跳到已执行节点（防环）
        for (ExecutedNode en : executed) {
            if (en.nodeId().equals(suggested)) {
                log.warn("[Orchestrator] Agent 建议的 nextNodeId={} 已执行过，降级为默认顺序（防环）", suggested);
                return graph.nextOf(current);
            }
        }
        log.info("[Orchestrator] Agent 动态路由: {} → {}", current.getNodeId(), suggested);
        return target;
    }

    /** 已成功节点记录（供回滚逆序补偿） */
    private record ExecutedNode(String nodeId, AgentType agentType, Long runId) {
    }

    private String buildInputSummary(AgentContext ctx, AgentNode node) {
        return switch (node.getAgentType()) {
            case PLANNER -> "goal=" + truncate(ctx.getGoal(), 200);
            case KNOWLEDGE -> "按计划检索 (kbId=" + ctx.getKbId() + ")";
            case ANALYSIS -> "基于证据分析目标";
            case REPORT -> "基于分析生成报告";
        };
    }

    private AgentGraph buildDefaultGraph() {
        AgentProperties.Orchestrator o = props.getOrchestrator();
        List<AgentNode> nodes = new ArrayList<>();
        for (Agent a : agents) {
            nodes.add(AgentNode.builder()
                    .nodeId(a.type().name().toLowerCase())
                    .agentType(a.type())
                    .name(a.roleName())
                    .maxRetries(o.getDefaultMaxRetries())
                    .retryBackoffMs(o.getDefaultRetryBackoffMs())
                    .timeoutMs(o.getDefaultNodeTimeoutMs())
                    .build());
        }
        AgentGraph g = new AgentGraph("default", nodes);
        g.validate();
        return g;
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getMessage() != null ? c.getMessage() : c.getClass().getSimpleName();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }

    private static void assertNotTerminal(AgentTask task) {
        OrchestratorStatus status = OrchestratorStatus.valueOf(task.getStatus());
        if (status.isTerminal()) {
            throw new BizException("任务已终态(" + status + ")，不可重复执行");
        }
    }

    /** 节点执行记录持有者（内部传参用，承载 runId） */
    private static class AgentNodeRunHolder {
        Long runId;
    }
}
