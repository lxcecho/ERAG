package com.knowledge.kb.governance.lifecycle.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.common.context.TenantContext;
import com.knowledge.kb.entity.KbDocAuditLog;
import com.knowledge.kb.governance.lifecycle.dto.AuditQuery;
import com.knowledge.kb.governance.lifecycle.dto.AuditVo;
import com.knowledge.kb.governance.lifecycle.mapper.KnowledgeAuditMapper;
import com.knowledge.kb.governance.lifecycle.service.KnowledgeAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 知识审计门面实现：复用 kb_doc_audit_log 表，扩展治理动作词表。
 * <p>写入同步落库（治理动作量小，无需异步）；查询走联表分页（按 kbId 经 JOIN kb_document 过滤）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeAuditServiceImpl implements KnowledgeAuditService {

    private final KnowledgeAuditMapper auditMapper;

    @Override
    public void record(Long tenantId, Long userId, Long docId, String action, boolean pass,
                       String passReason, String denyReason) {
        try {
            KbDocAuditLog log = new KbDocAuditLog();
            log.setTenantId(tenantId);
            log.setUserId(userId);
            log.setDocId(docId);
            log.setAction(action);
            log.setResult(pass ? RESULT_ALLOW : RESULT_DENY);
            log.setPassReason(pass ? passReason : null);
            log.setDenyReason(pass ? null : denyReason);
            auditMapper.insert(log);
        } catch (Exception e) {
            // 审计失败不阻断主流程（best-effort）
            KnowledgeAuditServiceImpl.log.warn("[治理-审计] 写入失败 doc={} action={} err={}", docId, action, e.getMessage());
        }
    }

    @Override
    public IPage<AuditVo> auditPage(AuditQuery query) {
        // kb_doc_audit_log 不走行级租户拦截器，手动注入 tenant_id
        query.setTenantId(TenantContext.requiredTenantId());
        Page<AuditVo> page = query.toPage();
        return auditMapper.selectAuditPage(page, query);
    }
}
