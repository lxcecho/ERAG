package com.knowledge.ai.model.config;

import com.knowledge.ai.model.ModelProvider;
import com.knowledge.ai.model.ModelRouter;
import com.knowledge.ai.model.ModelStrategy;
import com.knowledge.ai.model.ProviderType;
import com.knowledge.ai.model.circuit.CircuitBreakerStateStore;
import com.knowledge.ai.model.circuit.InMemoryCircuitBreakerStore;
import com.knowledge.ai.model.circuit.ProviderCircuitBreaker;
import com.knowledge.ai.model.circuit.RedisCircuitBreakerStore;
import com.knowledge.ai.model.impl.ModelRouterImpl;
import com.knowledge.ai.model.provider.ClaudeProvider;
import com.knowledge.ai.model.provider.DeepSeekProvider;
import com.knowledge.ai.model.provider.GeminiProvider;
import com.knowledge.ai.model.provider.OllamaProvider;
import com.knowledge.ai.model.provider.OpenAiProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * 模型路由中心装配：总开关 {@code ai.model-router.enabled=true} 时生效。
 * <p>
 * 装配内容：
 * <ol>
 *   <li>5 个供应商 {@link ModelProvider}：每个按 {@code providers.<type>.enabled} 互斥装配，未启用者不参与路由</li>
 *   <li>熔断状态存储：有 {@link StringRedisTemplate} 用 Redis（多节点共享），否则用内存（单节点兜底）</li>
 *   <li>{@link ProviderCircuitBreaker} 熔断器（按供应商维度状态机）</li>
 *   <li>{@link ModelRouter} 路由实现</li>
 * </ol>
 * <p>策略实现（{@code strategy} 包）用 {@code @Component} 扫描，注入 {@code List<ModelStrategy>} 自动收集。
 * <p>总开关关闭时本配置类不装配，零影响降级——调用方继续使用原有 ChatModel 直连。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.model-router", name = "enabled", havingValue = "true")
public class ModelRouterConfig {

    private final ModelRouterProperties properties;

    /* ==================== 5 个供应商 ==================== */

    @Bean
    @ConditionalOnProperty(prefix = "ai.model-router.providers.openai", name = "enabled", havingValue = "true")
    public ModelProvider openaiProvider() {
        return new OpenAiProvider(configOf(ProviderType.OPENAI));
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.model-router.providers.deepseek", name = "enabled", havingValue = "true")
    public ModelProvider deepseekProvider() {
        return new DeepSeekProvider(configOf(ProviderType.DEEPSEEK));
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.model-router.providers.claude", name = "enabled", havingValue = "true")
    public ModelProvider claudeProvider() {
        return new ClaudeProvider(configOf(ProviderType.CLAUDE));
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.model-router.providers.gemini", name = "enabled", havingValue = "true")
    public ModelProvider geminiProvider() {
        return new GeminiProvider(configOf(ProviderType.GEMINI));
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.model-router.providers.ollama", name = "enabled", havingValue = "true")
    public ModelProvider ollamaProvider() {
        return new OllamaProvider(configOf(ProviderType.OLLAMA));
    }

    /* ==================== 熔断 + 路由 ==================== */

    @Bean
    public CircuitBreakerStateStore circuitBreakerStateStore(ObjectProvider<StringRedisTemplate> redisProvider) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            log.info("[ModelRouter] 使用 Redis 熔断状态存储（多节点共享）");
            return new RedisCircuitBreakerStore(redis);
        }
        log.info("[ModelRouter] 未检测到 StringRedisTemplate，使用内存熔断状态存储（单节点）");
        return new InMemoryCircuitBreakerStore();
    }

    @Bean
    public ProviderCircuitBreaker providerCircuitBreaker(CircuitBreakerStateStore store) {
        return new ProviderCircuitBreaker(store, properties.getCircuitBreaker());
    }

    @Bean
    public ModelRouter modelRouter(List<ModelProvider> providers,
                                   List<ModelStrategy> strategies,
                                   ProviderCircuitBreaker breaker) {
        log.info("[ModelRouter] 装配完成：可用供应商 {} 个，策略 {} 个，默认策略 {}，failover 重试 {} 次",
                providers.size(), strategies.size(), properties.getDefaultStrategy(), properties.getFailoverRetries());
        return new ModelRouterImpl(providers, strategies, breaker, properties);
    }

    /* ==================== 工具 ==================== */

    private ModelRouterProperties.ProviderConfig configOf(ProviderType type) {
        ModelRouterProperties.ProviderConfig c = properties.getProviders().get(type);
        return c != null ? c : new ModelRouterProperties.ProviderConfig();
    }
}
