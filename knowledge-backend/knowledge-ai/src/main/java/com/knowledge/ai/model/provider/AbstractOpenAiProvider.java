package com.knowledge.ai.model.provider;

import com.knowledge.ai.model.ModelProvider;
import com.knowledge.ai.model.ModelRequest;
import com.knowledge.ai.model.ModelResponse;
import com.knowledge.ai.model.ProviderType;
import com.knowledge.ai.model.config.ModelRouterProperties;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 OpenAI 兼容协议的供应商公共基类。
 * <p>
 * 统一用 LangChain4j {@link OpenAiChatModel} 作为底层客户端，5 个供应商（OpenAI/DeepSeek/Claude/Gemini/Ollama）
 * 共用同一套调用逻辑，仅 {@link ProviderType}（携带默认 baseUrl/模型名）与配置不同——
 * OpenAI/DeepSeek/Ollama 原生支持 OpenAI 兼容协议，Gemini 走官方 OpenAI 兼容端点，
 * Claude 经兼容网关接入（如需原生 Anthropic 协议可新增独立 Provider，不影响本抽象）。
 * <p>
 * <b>懒创建</b>：{@link ChatModel} 在首次调用时构建（volatile + double-check），避免启动期因配置缺失/网络校验失败而启动失败；
 * 配置未启用或 apiKey 缺失时 {@link #available()} 返回 false，不参与路由，也不会创建客户端。
 * <p>
 * <b>异常语义</b>：{@link #chat(ModelRequest)} 不捕获异常，底层调用失败时异常向上抛，
 * 由 {@link com.knowledge.ai.model.impl.ModelRouterImpl} 捕获后记录熔断 failure 并触发 failover。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
public abstract class AbstractOpenAiProvider implements ModelProvider {

    private final ProviderType type;
    protected final ModelRouterProperties.ProviderConfig config;

    /** 懒创建的底层客户端（首次调用构建） */
    private volatile ChatModel chatModel;

    protected AbstractOpenAiProvider(ProviderType type, ModelRouterProperties.ProviderConfig config) {
        this.type = type;
        this.config = config != null ? config : new ModelRouterProperties.ProviderConfig();
    }

    @Override
    public ProviderType type() {
        return type;
    }

    @Override
    public String modelName() {
        return isBlank(config.getModelName()) ? type.getDefaultModel() : config.getModelName();
    }

    @Override
    public boolean available() {
        return config.isEnabled() && !isBlank(resolveApiKey());
    }

    @Override
    public BigDecimal inputPrice() {
        return BigDecimal.valueOf(config.getInputPrice());
    }

    @Override
    public BigDecimal outputPrice() {
        return BigDecimal.valueOf(config.getOutputPrice());
    }

    @Override
    public int maxTokens() {
        return config.getMaxTokens();
    }

    @Override
    public ModelResponse chat(ModelRequest req) {
        long start = System.currentTimeMillis();
        ChatModel model = getOrCreateModel();
        List<ChatMessage> messages = new ArrayList<>(2);
        if (req.systemPrompt() != null && !req.systemPrompt().isBlank()) {
            messages.add(SystemMessage.from(req.systemPrompt()));
        }
        messages.add(UserMessage.from(req.userPrompt()));
        ChatResponse resp = model.chat(messages);
        String text = resp.aiMessage().text();
        TokenUsage usage = resp.tokenUsage();
        int prompt = usage == null || usage.inputTokenCount() == null ? 0 : usage.inputTokenCount();
        int completion = usage == null || usage.outputTokenCount() == null ? 0 : usage.outputTokenCount();
        long durationMs = System.currentTimeMillis() - start;
        BigDecimal cost = estimateCost(prompt, completion);
        return new ModelResponse(
                text == null ? "" : text,
                prompt, completion, prompt + completion,
                modelName(), type, cost, durationMs);
    }

    /* ==================== 内部工具 ==================== */

    private ChatModel getOrCreateModel() {
        ChatModel local = chatModel;
        if (local == null) {
            synchronized (this) {
                local = chatModel;
                if (local == null) {
                    local = OpenAiChatModel.builder()
                            .baseUrl(resolveBaseUrl())
                            .apiKey(resolveApiKey())
                            .modelName(modelName())
                            .temperature(config.getTemperature())
                            .maxTokens(config.getMaxTokens())
                            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                            .build();
                    chatModel = local;
                    log.info("[ModelRouter] 供应商 {} 客户端已创建 model={} baseUrl={}",
                            type, modelName(), resolveBaseUrl());
                }
            }
        }
        return local;
    }

    /** baseUrl 解析：配置优先，缺失用供应商默认 */
    protected String resolveBaseUrl() {
        return isBlank(config.getBaseUrl()) ? type.getDefaultBaseUrl() : config.getBaseUrl();
    }

    /** apiKey 解析：子类可覆盖（如 Ollama 占位 key） */
    protected String resolveApiKey() {
        return config.getApiKey();
    }

    /** 按本供应商单价估算费用：prompt/1000*input + completion/1000*output */
    private BigDecimal estimateCost(int prompt, int completion) {
        BigDecimal in = inputPrice().multiply(BigDecimal.valueOf(prompt))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        BigDecimal out = outputPrice().multiply(BigDecimal.valueOf(completion))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        return in.add(out);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
