package com.knowledge.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.agent.entity.AgentMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 消息 Mapper
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {
}
