package com.knowledge.agent.engine;

import com.knowledge.agent.config.AgentProperties;
import com.knowledge.agent.entity.AgentStep;
import com.knowledge.agent.entity.AgentTask;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Agent 执行器：编排 4 个 Agent 的流水线执行（Planner→Knowledge→Analysis→Report）。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>按 {@code @Order} 顺序执行注入的 {@link Agent} 列表；</li>
 *   <li>每步记录 step（开始/成功/失败）+ artifact（结构化产物）；</li>
 *   <li>任一步失败立即终止任务并标记 FAILED；</li>
 *   <li>提供异步执行入口 {@link #executeAsync}，显式传播租户上下文。</li>
 * </ul>
 * <p>
 * Agent 无状态，产物通过 {@link AgentContext} 在步骤间传递；持久化统一由 {@link AgentTaskManager} 负责。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExecutor {

    private final List<Agent> agents;
    private final AgentTaskManager taskManager;
    private final AgentProperties props;

    /** Agent 异步执行线程池（独立于 Web 请求线程，避免阻塞接口） */
    private final ExecutorService asyncPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "agent-exec");
                t.setDaemon(true);
                return t;
            });

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

    /**
     * 同步执行工作流（用于测试或同步场景）。
     *
     * @param taskId 任务ID
     * @return 最终任务状态
     */
    public AgentTask execute(Long taskId) {
        AgentTask task = taskManager.getById(taskId);
        AgentStatus current = AgentStatus.valueOf(task.getStatus());
        if (current.isTerminal()) {
            throw new BizException("任务已终态(" + current + ")，不可重复执行");
        }

        AgentContext ctx = new AgentContext(task, props);
        taskManager.startTask(taskId);
        long taskStart = System.currentTimeMillis();
        log.info("[Executor] task={} 开始执行，目标={}", taskId, truncate(ctx.getGoal(), 100));

        int idx = 0;
        for (Agent agent : agents) {
            idx++;
            // 步数预算
            if (idx > props.getMaxSteps()) {
                taskManager.updateTokenUsage(taskId, ctx.getTokensUsed());
                taskManager.failTask(taskId, "MAX_STEPS", "超过最大步数 " + props.getMaxSteps());
                return taskManager.getById(taskId);
            }
            // 时长预算：单步前校验累计 wall-clock（注：单步内长耗时无法中断，下一轮校验兜底）
            if (System.currentTimeMillis() - taskStart > props.getTaskTimeoutSeconds() * 1000L) {
                taskManager.updateTokenUsage(taskId, ctx.getTokensUsed());
                taskManager.failTask(taskId, "TIMEOUT",
                        "超过任务时长预算 " + props.getTaskTimeoutSeconds() + "s");
                log.warn("[Executor] task={} 时长预算超限终止", taskId);
                return taskManager.getById(taskId);
            }

            long startWall = System.currentTimeMillis();
            AgentStep step = taskManager.startStep(
                    task.getTenantId(), taskId, idx, agent.type(),
                    buildInputSummary(ctx, agent));

            try {
                AgentResult result = agent.execute(ctx);
                long duration = System.currentTimeMillis() - startWall;

                if (!result.isSuccess()) {
                    taskManager.failStep(step.getId(), result.getErrorMessage());
                    taskManager.failTask(taskId, "STEP_FAILED",
                            agent.type() + ":" + result.getErrorMessage());
                    log.warn("[Executor] task={} 在 {} 步失败: {}",
                            taskId, agent.type(), result.getErrorMessage());
                    return taskManager.getById(taskId);
                }

                // 产物写入 Context（供下游 Agent 引用）+ 持久化
                if (result.getArtifactKey() != null) {
                    ctx.putArtifact(result.getArtifactKey(), result.getArtifact());
                    taskManager.recordArtifact(task.getTenantId(), taskId,
                            step.getId(), result.getArtifactKey(), result.getArtifact());
                }
                taskManager.successStep(step.getId(), result.getSummary(),
                        result.getTokensUsed(), duration);
                // token 预算：累加本步 LLM 消耗，超限终止
                ctx.addTokens(result.getTokensUsed());
                if (ctx.getTokensUsed() > props.getMaxTokensPerTask()) {
                    taskManager.updateTokenUsage(taskId, ctx.getTokensUsed());
                    taskManager.failTask(taskId, "TOKEN_BUDGET_EXCEEDED",
                            "token 累计 " + ctx.getTokensUsed()
                                    + " 超过预算 " + props.getMaxTokensPerTask());
                    log.warn("[Executor] task={} token 预算超限终止", taskId);
                    return taskManager.getById(taskId);
                }
                log.info("[Executor] task={} 步骤{} {} 完成 ({}ms, 累计token={})",
                        taskId, idx, agent.type(), duration, ctx.getTokensUsed());

            } catch (Exception e) {
                log.error("[Executor] task={} Agent执行异常 agent={}", taskId, agent.type(), e);
                taskManager.failStep(step.getId(), e.getMessage());
                taskManager.failTask(taskId, "EXCEPTION", e.getMessage());
                return taskManager.getById(taskId);
            }
        }

        // 全部步骤完成，收尾报告
        Object report = ctx.getArtifact(ArtifactType.REPORT.name());
        String reportText = report != null ? report.toString() : "";
        taskManager.updateTokenUsage(taskId, ctx.getTokensUsed());
        taskManager.completeTask(taskId, reportText);
        log.info("[Executor] task={} 工作流完成 (累计token={})", taskId, ctx.getTokensUsed());
        return taskManager.getById(taskId);
    }

    /**
     * 异步执行工作流：提交到独立线程池，显式传播租户上下文（避免 ThreadLocal 丢失）。
     * <p>用于 Controller 入口——立即返回 taskId，执行在后台进行，前端轮询/SSE 获取进度。
     */
    public void executeAsync(Long taskId, Long tenantId) {
        asyncPool.submit(() -> {
            TenantContext.setTenantId(tenantId);
            try {
                execute(taskId);
            } catch (Exception e) {
                log.error("[Executor] 异步执行失败 task={}", taskId, e);
                try {
                    taskManager.failTask(taskId, "ASYNC_EXCEPTION", e.getMessage());
                } catch (Exception ignore) {
                    // 任务可能已被标记终态，忽略二次失败
                }
            } finally {
                TenantContext.clear();
            }
        });
    }

    private String buildInputSummary(AgentContext ctx, Agent agent) {
        return switch (agent.type()) {
            case PLANNER -> "goal=" + truncate(ctx.getGoal(), 200);
            case KNOWLEDGE -> "按计划检索 (kbId=" + ctx.getKbId() + ")";
            case ANALYSIS -> "基于证据分析目标";
            case REPORT -> "基于分析生成报告";
        };
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }
}
