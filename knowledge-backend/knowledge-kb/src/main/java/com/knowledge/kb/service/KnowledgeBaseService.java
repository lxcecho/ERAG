package com.knowledge.kb.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.knowledge.kb.dto.KbCreateRequest;
import com.knowledge.kb.dto.KbPageQuery;
import com.knowledge.kb.dto.KbUpdateRequest;
import com.knowledge.kb.entity.KnowledgeBase;

/**
 * 知识库服务接口
 *
 * @author: lxcechoo@gmail.com
 */
public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    /** 创建知识库，返回主键（同时写入 owner 成员记录） */
    Long create(KbCreateRequest request, Long ownerId);

    /** 更新知识库 */
    void update(Long id, KbUpdateRequest request);

    /** 分页查询（仅返回当前用户有成员关系的知识库） */
    IPage<KnowledgeBase> page(KbPageQuery query, Long userId);

    /** 删除知识库（owner 校验 + 级联清理成员关系） */
    void remove(Long id, Long userId);

    /** 文档数量 +1 */
    void incrDocCount(Long kbId);

    /** 文档数量 -1 */
    void decrDocCount(Long kbId);
}
