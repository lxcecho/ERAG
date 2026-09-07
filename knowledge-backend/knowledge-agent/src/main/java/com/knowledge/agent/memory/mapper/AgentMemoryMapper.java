package com.knowledge.agent.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.agent.memory.entity.AgentMemory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 长期记忆 Mapper（SUMMARY + LONG_TERM 共用）
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface AgentMemoryMapper extends BaseMapper<AgentMemory> {
}
