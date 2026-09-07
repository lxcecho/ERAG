package com.knowledge.ai.rag.cache.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.ai.rag.cache.entity.SemanticCache;
import org.apache.ibatis.annotations.Mapper;

/**
 * 语义缓存 Mapper
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface SemanticCacheMapper extends BaseMapper<SemanticCache> {
}
