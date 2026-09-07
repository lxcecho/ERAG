package com.knowledge.agent.orchestrator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.agent.orchestrator.entity.AgentCompensation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 编排补偿记录 Mapper
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface AgentCompensationMapper extends BaseMapper<AgentCompensation> {
}
