package com.knowledge.agent.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Agent 层基础设施配置。
 * <p>
 * ChatModel Bean 由 {@code knowledge-ai} 模块的 {@code AiConfig} 统一创建（OpenAI 兼容协议），
 * {@link com.knowledge.agent.engine.AgentLlmCaller} 直接注入 {@code dev.langchain4j.model.chat.ChatModel}。
 * <p>
 * 本类仅保留工具执行专用线程池 {@code toolExecutorPool}（与 Agent 主流程隔离，超时控制用） +
 * {@link RestTemplate}（HttpTool 用，配置连接/读取超时）。
 *
 * @author: lxcechoo@gmail.com
 */
@Configuration
public class AgentConfig {

    /**
     * 工具执行专用线程池（v3-3 ToolExecutor 超时控制用）。
     * <p>固定大小线程池，daemon 线程（不阻止 JVM 退出），与 Agent 主流程隔离，
     * 避免工具超时阻塞占用 Agent 调度线程。
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService toolExecutorPool(AgentProperties props) {
        int size = Math.max(1, props.getTool().getExecutorPoolSize());
        return new ThreadPoolExecutor(size, size, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "tool-exec");
                    t.setDaemon(true);
                    return t;
                });
    }

    /**
     * HttpTool 用的 {@link RestTemplate}（配置连接/读取超时）。
     * <p>SSRF 防护由 HttpTool 内部按 {@code agent.tool.http.allowed-hosts} 白名单校验，
     * 此处仅配置超时；无其它 RestTemplate bean 时按类型注入 HttpTool。
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, AgentProperties props) {
        AgentProperties.Http http = props.getTool().getHttp();
        return builder
                .connectTimeout(Duration.ofMillis(http.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(http.getReadTimeoutMs()))
                .build();
    }
}
