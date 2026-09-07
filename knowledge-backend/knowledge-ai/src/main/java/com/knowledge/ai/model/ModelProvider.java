package com.knowledge.ai.model;

import java.math.BigDecimal;

/**
 * 模型供应商抽象：统一封装一个底层大模型供应商的调用能力与计费元数据。
 * <p>
 * 设计目的：将「调用模型」与「路由决策」解耦。路由层 ({@link ModelStrategy}) 仅依赖本接口的
 * 计费元数据（{@link #inputPrice()}/{@link #outputPrice()}/{@link #maxTokens()}）做选型，
 * 不感知底层是 OpenAI / DeepSeek / Claude / Gemini / Ollama，符合依赖倒置。
 * <p>
 * 每个供应商一个实现（见 {@code provider} 子包），均基于 OpenAI 兼容协议，新增供应商只需实现本接口。
 *
 * @author: lxcechoo@gmail.com
 */
public interface ModelProvider {

    /** 供应商类型 */
    ProviderType type();

    /** 默认模型名 */
    String modelName();

    /**
     * 是否可用：配置齐全（enabled + apiKey 非空）且底层客户端就绪。
     * <p>路由器仅从可用的供应商中选型，不可用者不参与路由。
     */
    boolean available();

    /** 输入单价（元/1K tokens），供成本路由估算 */
    BigDecimal inputPrice();

    /** 输出单价（元/1K tokens） */
    BigDecimal outputPrice();

    /** 单次最大生成 token 数（上下文容量提示，供 token 路由） */
    int maxTokens();

    /**
     * 执行模型调用。
     *
     * @param req 统一请求
     * @return 统一响应（含文本 / token / 费用 / 耗时）
     */
    ModelResponse chat(ModelRequest req);
}
