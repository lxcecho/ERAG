package com.knowledge.ai.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.ai.chat.entity.ChatMessage;
import com.knowledge.ai.chat.mapper.ChatMessageMapper;
import com.knowledge.ai.chat.service.ChatContextService;
import com.knowledge.ai.config.AiProperties;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聊天上下文服务实现
 * <p>取最近 historyWindowSize 轮（=2N 条消息）倒序查询后反转为正序，
 * 映射为 LangChain4j 消息序列，拼在系统提示之后、当前问题之前。
 * <p>注意：实体 {@link ChatMessage} 与 LangChain4j 的
 * {@code dev.langchain4j.data.message.ChatMessage} 同名，故后者以全限定名引用以消歧。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatContextServiceImpl implements ChatContextService {

    private final ChatMessageMapper chatMessageMapper;
    private final AiProperties aiProperties;

    @Override
    public List<dev.langchain4j.data.message.ChatMessage> loadHistory(Long sessionId) {
        int window = aiProperties.getRag().getHistoryWindowSize();
        int limit = window * 2;
        // 倒序取最近 limit 条，再反转为正序
        List<ChatMessage> records = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getCreateTime)
                        .last("LIMIT " + limit));
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.reverse(records);
        List<dev.langchain4j.data.message.ChatMessage> history = new ArrayList<>(records.size());
        for (ChatMessage m : records) {
            if ("user".equals(m.getRole())) {
                history.add(UserMessage.from(m.getContent()));
            } else if ("assistant".equals(m.getRole())) {
                history.add(AiMessage.from(m.getContent()));
            }
        }
        return history;
    }
}
