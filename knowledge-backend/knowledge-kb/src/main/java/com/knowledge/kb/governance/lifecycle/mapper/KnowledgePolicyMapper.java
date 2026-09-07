package com.knowledge.kb.governance.lifecycle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.kb.governance.lifecycle.entity.KnowledgePolicy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识治理策略 Mapper：按 (tenant_id, kb_id) 唯一查询/upsert。
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface KnowledgePolicyMapper extends BaseMapper<KnowledgePolicy> {
}
