package com.knowledge.ai.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.List;

/**
 * 大语言模型服务：封装对话生成能力。
 * <p>设计原因：隔离 LangChain4j 的 ChatModel / StreamingChatModel 细节，
 * 业务层只面向"输入提示词、输出回答"的契约，便于后续替换为多模态 / 其它厂商实现。
 *
 * @author: lxcechoo@gmail.com
 */
public interface LLMService {

    /**
     * 单轮对话。
     *
     * @param prompt 用户提示词
     * @return 模型回答
     */
    String chat(String prompt);

    /**
     * 带系统提示词的对话（用于 RAG：系统提示约束行为，用户消息为问题）。
     *
     * @param systemPrompt 系统提示词（含检索到的上下文）
     * @param userMessage  用户问题
     * @return 模型回答
     */
    String chat(String systemPrompt, String userMessage);

    /**
     * 多轮对话：系统提示 + 历史消息 + 当前问题。
     * <p>历史消息由 {@link com.knowledge.ai.chat.service.ChatContextService} 从持久化记录加载，
     * 使 LLM 能结合上下文进行指代消解与追问。
     *
     * @param systemPrompt 系统提示词
     * @param history      历史消息（UserMessage / AiMessage 交替，正序）
     * @param userMessage  当前用户问题
     * @return 模型回答
     */
    String chat(String systemPrompt, List<ChatMessage> history, String userMessage);

    /**
     * 流式多轮对话：逐 token 通过回调推送，供 SSE 端点实时输出。
     *
     * @param systemPrompt 系统提示词
     * @param history      历史消息
     * @param userMessage  当前用户问题
     * @param handler      流式响应回调（onPartialResponse / onCompleteResponse / onError）
     */
    void streamChat(String systemPrompt, List<ChatMessage> history, String userMessage,
                    StreamingChatResponseHandler handler);
}
