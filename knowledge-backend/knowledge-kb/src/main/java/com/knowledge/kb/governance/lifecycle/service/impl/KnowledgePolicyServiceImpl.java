package com.knowledge.kb.governance.lifecycle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.governance.lifecycle.dto.PolicyRequest;
import com.knowledge.kb.governance.lifecycle.dto.PolicyVo;
import com.knowledge.kb.governance.lifecycle.entity.KnowledgePolicy;
import com.knowledge.kb.governance.lifecycle.mapper.KnowledgePolicyMapper;
import com.knowledge.kb.governance.lifecycle.service.KnowledgeAuditService;
import com.knowledge.kb.governance.lifecycle.service.KnowledgePolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识治理策略服务实现。
 * <p>getByKb 无记录时返回内存默认策略（requireReview=true，不落库），保证未配置 KB 仍可走默认治理。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePolicyServiceImpl implements KnowledgePolicyService {

    private final KnowledgePolicyMapper policyMapper;
    private final KnowledgeAuditService auditService;

    @Override
    public KnowledgePolicy getByKb(Long kbId) {
        if (kbId == null) {
            return defaultPolicy(null);
        }
        KnowledgePolicy policy = policyMapper.selectOne(new LambdaQueryWrapper<KnowledgePolicy>()
                .eq(KnowledgePolicy::getKbId, kbId)
                .last("LIMIT 1"));
        return policy != null ? policy : defaultPolicy(kbId);
    }

    @Override
    public PolicyVo getVoByKb(Long kbId) {
        return toVo(getByKb(kbId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdate(PolicyRequest request, Long userId) {
        Long tenantId = TenantContext.requiredTenantId();
        KnowledgePolicy existing = policyMapper.selectOne(new LambdaQueryWrapper<KnowledgePolicy>()
                .eq(KnowledgePolicy::getKbId, request.getKbId())
                .last("LIMIT 1"));
        KnowledgePolicy policy = existing != null ? existing : new KnowledgePolicy();
        if (existing == null) {
            policy.setTenantId(tenantId);
            policy.setKbId(request.getKbId());
        }
        policy.setRequireReview(request.getRequireReview());
        policy.setAutoArchiveDays(request.getAutoArchiveDays());
        policy.setRetentionDays(request.getRetentionDays());
        policy.setApproverRoles(request.getApproverRoles());
        policy.setReviewExpireHours(request.getReviewExpireHours());
        if (existing != null) {
            policyMapper.updateById(policy);
        } else {
            policyMapper.insert(policy);
        }
        auditService.record(tenantId, userId, null, KnowledgeAuditService.ACTION_POLICY_UPDATE,
                true, "kb=" + request.getKbId(), null);
        log.info("[治理-策略] 保存策略 kb={} requireReview={} autoArchiveDays={} retentionDays={} user={}",
                request.getKbId(), request.getRequireReview(), request.getAutoArchiveDays(),
                request.getRetentionDays(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByKb(Long kbId, Long userId) {
        Long tenantId = TenantContext.requiredTenantId();
        int rows = policyMapper.delete(new LambdaQueryWrapper<KnowledgePolicy>()
                .eq(KnowledgePolicy::getKbId, kbId));
        if (rows == 0) {
            throw new BizException("策略不存在");
        }
        auditService.record(tenantId, userId, null, KnowledgeAuditService.ACTION_POLICY_UPDATE,
                true, "删除 kb=" + kbId, null);
        log.info("[治理-策略] 删除策略 kb={} user={}", kbId, userId);
    }

    /** 内存默认策略：requireReview=true，其余 null（不落库，调用方按需取值） */
    private KnowledgePolicy defaultPolicy(Long kbId) {
        KnowledgePolicy p = new KnowledgePolicy();
        p.setKbId(kbId);
        p.setRequireReview(Boolean.TRUE);
        p.setAutoArchiveDays(null);
        p.setRetentionDays(null);
        return p;
    }

    private PolicyVo toVo(KnowledgePolicy p) {
        PolicyVo vo = new PolicyVo();
        BeanUtils.copyProperties(p, vo);
        return vo;
    }
}
