package com.knowledge.ai.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowledge.ai.chat.dto.ChatMessageVo;
import com.knowledge.ai.chat.entity.ChatMessage;
import com.knowledge.ai.dto.RetrievalResult;

import java.util.List;

/**
 * 聊天消息服务
 *
 * @author: lxcechoo@gmail.com
 */
public interface ChatMessageService extends IService<ChatMessage> {

    /** 查询会话全部消息（按时间正序，反序列化来源） */
    List<ChatMessageVo> listBySession(Long sessionId);

    /** 保存用户消息 */
    void saveUserMessage(Long sessionId, String content);

    /** 保存助手消息（含检索来源） */
    void saveAssistantMessage(Long sessionId, String content, List<RetrievalResult> sources);

    /** 删除会话下全部消息 */
    void removeBySession(Long sessionId);
}
