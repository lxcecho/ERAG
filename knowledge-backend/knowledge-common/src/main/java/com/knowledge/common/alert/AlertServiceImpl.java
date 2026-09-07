package com.knowledge.common.alert;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 告警服务实现：结构化 {@code [ALERT]} 日志 + 可选 Webhook 推送 + 冷却去重。
 * <p>
 * 设计要点：
 * <ul>
 *   <li><b>结构化日志</b>：统一前缀 {@code [ALERT][级别]}，便于 ELK/Loki 等日志平台检索聚合；
 *       CRITICAL→error、WARN→warn、INFO→info。</li>
 *   <li><b>冷却去重</b>：用 Caffeine TTL 缓存 {@code source|title} 键，cooldownSeconds 内重复告警直接丢弃，
 *       避免熔断期或异常风暴导致告警轰炸。</li>
 *   <li><b>异步 Webhook</b>：通过注入的 {@code operLogExecutor} 线程池直接提交任务（避免 @Async 自调用失效问题），
 *       HTTP 推送失败仅 warn 不阻断主流程。</li>
 *   <li><b>级别过滤</b>：低于 minLevel 的告警直接返回，不记录不推送。</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
public class AlertServiceImpl implements AlertService {

    private final AlertProperties props;
    private final Executor webhookExecutor;

    /** 冷却缓存：key = source|title，value = 上次告警时间戳（仅用 TTL 去重，value 无业务含义） */
    private final Cache<String, Long> cooldownCache;

    /** Webhook HTTP 客户端（懒构建，仅 webhookEnabled 时使用） */
    private final RestClient webhookClient;

    public AlertServiceImpl(AlertProperties props,
                            @Qualifier("operLogExecutor") Executor webhookExecutor) {
        this.props = props;
        this.webhookExecutor = webhookExecutor;
        this.cooldownCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(1, props.getCooldownSeconds())))
                .maximumSize(10_000)
                .build();
        // RestClient 轻量构建，即使不启用也无所谓（不发请求不占连接）
        this.webhookClient = RestClient.builder()
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public void alert(AlertLevel level, String source, String title, String detail, Throwable t) {
        // 1. 总开关 / 级别过滤
        if (!props.isEnabled() || !level.atLeast(props.getMinLevel())) {
            return;
        }

        // 2. 冷却去重：同源同标题在冷却窗口内只告警一次
        String dedupKey = source + "|" + title;
        if (cooldownCache.getIfPresent(dedupKey) != null) {
            return;
        }
        cooldownCache.put(dedupKey, System.currentTimeMillis());

        // 3. 结构化日志（统一 [ALERT] 前缀便于检索）
        String logMsg = "[ALERT][{}] source={}, title={}, detail={}";
        String safeDetail = detail == null ? "" : detail;
        switch (level) {
            case CRITICAL -> log.error(logMsg, level, source, title, safeDetail, t);
            case WARN -> log.warn(logMsg, level, source, title, safeDetail, t);
            default -> log.info(logMsg, level, source, title, safeDetail, t);
        }

        // 4. 可选 Webhook 推送（异步，失败不阻断）
        if (props.isWebhookEnabled() && props.getWebhookUrl() != null && !props.getWebhookUrl().isBlank()) {
            // 直接提交到线程池，避免 @Async 自调用绕过 AOP 代理的问题
            webhookExecutor.execute(() -> pushWebhook(level, source, title, safeDetail));
        }
    }

    /**
     * 推送 Webhook（Feishu/DingTalk 兼容文本消息格式）。
     * <p>在 operLogExecutor 线程池执行，任何异常仅 warn 记录，不影响主流程。
     */
    private void pushWebhook(AlertLevel level, String source, String title, String detail) {
        try {
            String text = String.format("【ERAG 告警】[%s] %s\n来源: %s\n详情: %s\n时间: %s",
                    level, title, source, detail, LocalDateTime.now());
            Map<String, Object> payload = new HashMap<>();
            payload.put("msg_type", "text");
            Map<String, String> content = new HashMap<>();
            content.put("text", text);
            payload.put("content", content);

            webhookClient.post()
                    .uri(props.getWebhookUrl())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("[告警Webhook] 推送成功 source={}, title={}", source, title);
        } catch (Exception e) {
            log.warn("[告警Webhook] 推送失败 source={}, title={}, err={}", source, title, e.getMessage());
        }
    }
}
