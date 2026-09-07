package com.knowledge.agent.orchestrator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.agent.orchestrator.entity.AgentNodeRun;
import org.apache.ibatis.annotations.Mapper;

/**
 * 编排节点执行记录 Mapper
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface AgentNodeRunMapper extends BaseMapper<AgentNodeRun> {
}
