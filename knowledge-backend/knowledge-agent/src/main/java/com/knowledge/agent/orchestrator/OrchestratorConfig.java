package com.knowledge.agent.orchestrator;

import com.knowledge.agent.config.AgentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 编排引擎配置：提供节点硬超时用的执行线程池。
 * <p>
 * 独立于 {@code AgentConfig.toolExecutorPool}（工具超时池）与 {@code AgentExecutor.asyncPool}（engine 异步池），
 * 命名 {@code orchestrator-exec} 便于监控；固定大小 daemon 线程，不阻止 JVM 退出。
 * 节点执行通过 {@code Future.get(timeoutMs)} 实现硬超时，超时 {@code cancel(true)} 中断（best-effort）。
 *
 * @author: lxcechoo@gmail.com
 */
@Configuration
public class OrchestratorConfig {

    /**
     * 编排执行线程池：供 AgentExecutor 提交节点 Agent.execute() 以施加硬超时。
     * <p>与 tool/agent 异步池隔离，避免节点超时阻塞占用其它执行链路。
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService orchestratorPool(AgentProperties props) {
        int size = Math.max(1, props.getOrchestrator().getExecutorPoolSize());
        return new ThreadPoolExecutor(size, size, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "orchestrator-exec");
                    t.setDaemon(true);
                    return t;
                });
    }
}
