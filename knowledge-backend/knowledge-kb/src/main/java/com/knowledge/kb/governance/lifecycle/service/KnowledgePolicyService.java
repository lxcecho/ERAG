package com.knowledge.kb.governance.lifecycle.service;

import com.knowledge.kb.governance.lifecycle.dto.PolicyRequest;
import com.knowledge.kb.governance.lifecycle.dto.PolicyVo;
import com.knowledge.kb.governance.lifecycle.entity.KnowledgePolicy;

/**
 * 知识治理策略服务：每 KB 一份策略的 CRUD + 读取（带默认回退）。
 * <p>策略驱动生命周期：强制审核 / 自动归档 / 保留期 / 审核角色 / 审核超时。
 *
 * @author: lxcechoo@gmail.com
 */
public interface KnowledgePolicyService {

    /**
     * 按 KB 读取策略；无记录时返回默认策略（requireReview=true，其余 null，不落库）。
     */
    KnowledgePolicy getByKb(Long kbId);

    /** 按 KB 读取策略视图（默认策略 id 为 null） */
    PolicyVo getVoByKb(Long kbId);

    /** 保存或更新策略（按 kbId upsert），记 POLICY_UPDATE 审计 */
    void saveOrUpdate(PolicyRequest request, Long userId);

    /** 删除策略（按 kbId） */
    void deleteByKb(Long kbId, Long userId);
}
