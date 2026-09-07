package com.knowledge.common.config;

import com.alibaba.ttl.threadpool.TtlExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置。
 * <p>为耗时的异步任务（文档解析、向量化入库、操作日志）提供独立线程池，与 Tomcat 主线程池隔离。
 * <p>【多租户关键】所有线程池必须用 TTL（TransmittableThreadLocal）包装：
 * 保证 @Async 提交任务时自动快照 TenantContext TTL 值，即使线程池复用也不会串租户。
 * <p>拒绝策略采用 CallerRunsPolicy：队列满时由调用线程执行，天然限流 + 不丢任务。
 *
 * @author: lxcechoo@gmail.com
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean("docAsyncExecutor")
    public Executor docAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("doc-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        // TTL 包装：保证 TenantContext 等 TransmittableThreadLocal 在 submit/execute 时正确传递
        return TtlExecutors.getTtlExecutor(executor.getThreadPoolExecutor());
    }

    /**
     * 操作日志专用线程池。
     * <p>与 docAsyncExecutor 隔离，避免日志风暴阻塞文档解析；队列较大以承载突发写入。
     */
    @Bean("operLogExecutor")
    public Executor operLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("oper-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return TtlExecutors.getTtlExecutor(executor.getThreadPoolExecutor());
    }

    /**
     * AI 调用日志专用线程池。
     * <p>每次模型调用都会异步写入一条 ai_call_log，调用频率高，独立线程池隔离避免影响业务。
     * 审计日志允许丢弃（CallerRunsPolicy 在队列满时回退同步写，不丢但可能拖慢调用方）。
     */
    @Bean("aiCallLogExecutor")
    public Executor aiCallLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ai-call-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return TtlExecutors.getTtlExecutor(executor.getThreadPoolExecutor());
    }
}
