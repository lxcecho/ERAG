package com.knowledge.agent.orchestrator;

import com.knowledge.agent.engine.AgentTaskManager;
import com.knowledge.agent.entity.AgentTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent 调度器（编排引擎门面）：注册 + 调度。
 * <p>
 * 单向依赖 {@link AgentExecutor}（执行器内部从 Spring 注入的 {@code List<Agent>} 自动构建默认图，避免循环依赖）。
 * 职责：
 * <ul>
 *   <li>注册：默认图由执行器 @PostConstruct 构建（Planner→Knowledge→Analysis→Report 顺序串接）；
 *       自定义图经 {@link #registerGraph} 注册，供 {@link #execute(Long, String)} 按编码选用。</li>
 *   <li>调度：{@link #submit} 异步触发编排，{@link #execute} 同步执行（测试用），{@link #cancel} 取消。</li>
 * </ul>
 * 对外作为编排引擎统一入口，屏蔽内部执行器/任务管理器细节。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentScheduler {

    private final AgentExecutor executor;
    private final AgentTaskManager taskManager;

    // ==================== 任务创建（便捷方法） ====================

    /** 创建编排任务（status=CREATED，待 submit/execute 触发执行） */
    public AgentTask createTask(Long tenantId, Long userId, Long kbId, String goal, Long sessionId) {
        return taskManager.create(tenantId, userId, kbId, goal, sessionId);
    }

    // ==================== 调度 ====================

    /** 异步提交默认图编排（Controller 入口用，立即返回 taskId） */
    public void submit(Long taskId, Long tenantId) {
        executor.executeAsync(taskId, tenantId);
        log.info("[Scheduler] task={} 已提交异步编排", taskId);
    }

    /** 同步执行默认图（测试/同步场景） */
    public AgentTask execute(Long taskId) {
        return executor.execute(taskId);
    }

    /** 同步执行指定编码的自定义图 */
    public AgentTask execute(Long taskId, String graphCode) {
        AgentGraph graph = executor.getGraph(graphCode);
        if (graph == null) {
            throw new IllegalArgumentException("未注册的编排图: " + graphCode);
        }
        return executor.execute(taskId, graph);
    }

    /** 取消编排 */
    public void cancel(Long taskId, String reason) {
        executor.cancel(taskId, reason);
    }

    // ==================== 注册 ====================

    /** 注册自定义编排图 */
    public void registerGraph(String code, AgentGraph graph) {
        executor.registerGraph(code, graph);
    }

    /** 默认编排图 */
    public AgentGraph defaultGraph() {
        return executor.defaultGraph();
    }

    /** 查询已注册的自定义图 */
    public AgentGraph getGraph(String code) {
        return executor.getGraph(code);
    }
}
