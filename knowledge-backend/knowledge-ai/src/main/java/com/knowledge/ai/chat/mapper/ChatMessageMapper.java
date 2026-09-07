package com.knowledge.ai.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.ai.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息 Mapper
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
