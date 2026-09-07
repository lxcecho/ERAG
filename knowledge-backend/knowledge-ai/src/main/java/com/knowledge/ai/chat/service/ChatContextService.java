package com.knowledge.ai.chat.service;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 聊天上下文服务：从持久化消息构建多轮对话历史，供 LLM 多轮推理使用。
 *
 * @author: lxcechoo@gmail.com
 */
public interface ChatContextService {

    /**
     * 加载会话最近 N 轮历史（不包含当前问题），按时间正序返回。
     *
     * @param sessionId 会话ID
     * @return LangChain4j ChatMessage 列表（UserMessage / AiMessage 交替）
     */
    List<ChatMessage> loadHistory(Long sessionId);
}
