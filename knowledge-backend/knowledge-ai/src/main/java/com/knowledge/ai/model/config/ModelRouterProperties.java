package com.knowledge.ai.model.config;

import com.knowledge.ai.model.ProviderType;
import com.knowledge.ai.model.RouteStrategyType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 模型路由中心配置属性（读取 application.yml 中 {@code ai.model-router.*}）。
 * <p>
 * 总开关 {@code enabled=false}（默认）时整个 ModelRouter 不装配，零影响降级；
 * 启用后按 {@code providers.<type>.enabled} 逐个装配供应商，{@code tenant-preference} 配置租户偏好，
 * {@code circuit-breaker} 配置熔断参数，{@code failover-retries} 配置故障转移次数。
 *
 * <pre>
 * ai:
 *   model-router:
 *     enabled: true
 *     default-strategy: tenant
 *     failover-retries: 1
 *     providers:
 *       openai:
 *         enabled: true
 *         api-key: ${OPENAI_API_KEY:}
 *         model-name: gpt-4o-mini
 *         input-price: 0.00015
 *         output-price: 0.0006
 *       deepseek:
 *         enabled: true
 *         api-key: ${DEEPSEEK_API_KEY:}
 *       ollama:
 *         enabled: true
 *         base-url: http://localhost:11434/v1
 *         api-key: ollama           # Ollama 不校验 key，任意非空即可
 *     tenant-preference:
 *       1: deepseek
 *       2: openai
 *     circuit-breaker:
 *       enabled: true
 *       failure-threshold: 5
 *       open-seconds: 30
 * </pre>
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.model-router")
public class ModelRouterProperties {

    /** 总开关：false 时 ModelRouter 不装配，调用方应回退到原有 ChatModel 直连 */
    private boolean enabled = false;

    /** 默认路由策略（ModelContext 未指定策略时使用） */
    private RouteStrategyType defaultStrategy = RouteStrategyType.TENANT;

    /** 故障转移重试次数：选中供应商失败后，最多再尝试 N 个候选供应商 */
    private int failoverRetries = 1;

    /** 供应商配置表（key 为供应商类型） */
    private Map<ProviderType, ProviderConfig> providers = new EnumMap<>(ProviderType.class);

    /** 租户 → 偏好供应商映射（按租户路由使用） */
    private Map<Long, ProviderType> tenantPreference = new HashMap<>();

    /** 熔断器配置 */
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    /**
     * 单个供应商配置。
     */
    @Data
    public static class ProviderConfig {

        /** 是否启用该供应商（false 则不参与路由） */
        private boolean enabled = false;

        /** OpenAI 兼容 baseUrl（空则用 {@link ProviderType#getDefaultBaseUrl()}） */
        private String baseUrl;

        /** API Key（Ollama 本地可不填，但 available() 要求非空，故填任意占位） */
        private String apiKey;

        /** 模型名（空则用 {@link ProviderType#getDefaultModel()}） */
        private String modelName;

        /** 采样温度 */
        private double temperature = 0.7;

        /** 单次最大生成 token（上下文容量提示，供 token 路由） */
        private int maxTokens = 2048;

        /** 输入单价（元/1K tokens），供成本路由估算 */
        private double inputPrice = 0.002;

        /** 输出单价（元/1K tokens） */
        private double outputPrice = 0.006;

        /** 调用超时（秒） */
        private int timeoutSeconds = 60;
    }

    /**
     * 熔断器配置：按供应商维度统计失败，达阈值熔断，冷却后半开试探。
     */
    @Data
    public static class CircuitBreaker {

        /** 是否启用熔断（false 时 allowRequest 恒 true，不记录成败） */
        private boolean enabled = true;

        /** 触发熔断的失败次数阈值（统计窗口内） */
        private int failureThreshold = 5;

        /** 失败计数统计窗口（秒） */
        private int windowSeconds = 60;

        /** 熔断打开持续时间（秒），过后转半开 */
        private int openSeconds = 30;

        /** 半开状态允许的试探请求数（限制并发试探） */
        private int halfOpenPermits = 1;
    }
}
