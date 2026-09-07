package com.knowledge.ai.chat.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.ai.chat.dto.ChatMessageVo;
import com.knowledge.ai.chat.entity.ChatMessage;
import com.knowledge.ai.chat.mapper.ChatMessageMapper;
import com.knowledge.ai.chat.service.ChatMessageService;
import com.knowledge.ai.dto.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 聊天消息服务实现
 * <p>sourcesJson 字段以 JSON 存储/读取检索来源，避免单独建表。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
        implements ChatMessageService {

    @Override
    public List<ChatMessageVo> listBySession(Long sessionId) {
        List<ChatMessage> messages = list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreateTime));
        return messages.stream().map(this::toVo).toList();
    }

    @Override
    public void saveUserMessage(Long sessionId, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("user");
        msg.setContent(content);
        save(msg);
    }

    @Override
    public void saveAssistantMessage(Long sessionId, String content, List<RetrievalResult> sources) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setSourcesJson(sources == null || sources.isEmpty() ? null : JSONUtil.toJsonStr(sources));
        save(msg);
    }

    @Override
    public void removeBySession(Long sessionId) {
        remove(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
    }

    /** 实体转 VO，反序列化 sourcesJson */
    private ChatMessageVo toVo(ChatMessage msg) {
        ChatMessageVo vo = new ChatMessageVo();
        vo.setId(msg.getId());
        vo.setSessionId(msg.getSessionId());
        vo.setRole(msg.getRole());
        vo.setContent(msg.getContent());
        vo.setCreateTime(msg.getCreateTime());
        if (msg.getSourcesJson() != null && !msg.getSourcesJson().isBlank()) {
            try {
                vo.setSources(JSONUtil.toList(msg.getSourcesJson(), RetrievalResult.class));
            } catch (Exception e) {
                log.warn("消息来源反序列化失败: {}", e.getMessage());
                vo.setSources(Collections.emptyList());
            }
        }
        return vo;
    }
}
