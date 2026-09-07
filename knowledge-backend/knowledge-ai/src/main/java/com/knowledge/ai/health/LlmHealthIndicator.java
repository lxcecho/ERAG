package com.knowledge.ai.health;

import dev.langchain4j.model.chat.ChatModel;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * LLM 服务健康检查
 * <p><b>不主动调用 LLM</b>（避免健康检查烧 token），改为读取 {@link AiCallHealthTracker} 中 "llm" 资源的
 * 滑动窗口错误率被动判定：错误率高 → DEGRADED，否则 UP。
 * <p>同时校验 {@link ChatModel} Bean 是否就绪（配置缺失时 DOWN）。
 *
 * @author: lxcechoo@gmail.com
 */
@Component("llmHealthIndicator")
public class LlmHealthIndicator implements HealthIndicator {

    private static final String RESOURCE = "llm";

    private final AiCallHealthTracker tracker;
    private final ChatModel chatModel;

    public LlmHealthIndicator(AiCallHealthTracker tracker, ChatModel chatModel) {
        this.tracker = tracker;
        this.chatModel = chatModel;
    }

    @Override
    public Health health() {
        AiCallHealthTracker.HealthStats stats = tracker.getStats(RESOURCE);

        Health.Builder builder = stats.healthy() ? Health.up() : Health.down();
        builder.withDetail("success", stats.success())
                .withDetail("failure", stats.failure())
                .withDetail("total", stats.total())
                .withDetail("errorRate", String.format("%.2f%%", stats.errorRate() * 100))
                .withDetail("chatModelReady", chatModel != null)
                .withDetail("note", "passive tracking (no active LLM ping to save tokens)");
        return builder.build();
    }
}
