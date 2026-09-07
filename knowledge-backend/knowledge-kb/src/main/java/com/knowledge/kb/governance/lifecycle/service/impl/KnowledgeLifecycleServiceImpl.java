package com.knowledge.kb.governance.lifecycle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.governance.entity.DocumentReview;
import com.knowledge.kb.governance.enums.ReviewAction;
import com.knowledge.kb.governance.lifecycle.dto.LifecycleActionRequest;
import com.knowledge.kb.governance.lifecycle.dto.LifecycleVo;
import com.knowledge.kb.governance.lifecycle.entity.KnowledgePolicy;
import com.knowledge.kb.governance.lifecycle.enums.LifecycleAction;
import com.knowledge.kb.governance.lifecycle.enums.LifecycleStatus;
import com.knowledge.kb.governance.lifecycle.service.KnowledgeAuditService;
import com.knowledge.kb.governance.lifecycle.service.KnowledgeLifecycleService;
import com.knowledge.kb.governance.lifecycle.service.KnowledgePolicyService;
import com.knowledge.kb.governance.lifecycle.statemachine.LifecycleBridge;
import com.knowledge.kb.governance.lifecycle.statemachine.LifecycleStateMachine;
import com.knowledge.kb.governance.mapper.DocumentFingerprintMapper;
import com.knowledge.kb.governance.mapper.DocumentQualityMapper;
import com.knowledge.kb.governance.mapper.DocumentReviewMapper;
import com.knowledge.kb.governance.mapper.DocumentVersionMapper;
import com.knowledge.kb.mapper.KbDocAclMapper;
import com.knowledge.kb.mapper.KbDocAuditLogMapper;
import com.knowledge.kb.mapper.KbDocumentMapper;
import com.knowledge.kb.service.KbDocumentService;
import com.knowledge.kb.service.KbPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识生命周期服务实现。
 * <p>状态迁移主流程（{@link #transition}）：
 * <ol>
 *   <li>checkEditor 权限校验（写操作）；</li>
 *   <li>加载文档，解析当前 lifecycle_status；</li>
 *   <li>读取 KB 策略，调用 {@link LifecycleStateMachine#nextStatus} 裁定目标状态；</li>
 *   <li>非法组合抛 BizException；</li>
 *   <li>{@link LifecycleBridge#syncReviewFields} 同步 review_status / reviewerId / reviewedAt / archivedAt；</li>
 *   <li>更新文档；</li>
 *   <li>REVIEW 阶段写一条 {@code document_review} 流水（SUBMIT/APPROVE/REJECT），保证旧审核分页可见；</li>
 *   <li>写治理审计 kb_doc_audit_log。</li>
 * </ol>
 * <p>定时任务 {@link #autoArchive} / {@link #purgeRetained} 由 {@code GovernanceScheduleTask} 调度。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeLifecycleServiceImpl implements KnowledgeLifecycleService {

    private final KbDocumentService kbDocumentService;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbPermissionService kbPermissionService;
    private final KnowledgePolicyService policyService;
    private final KnowledgeAuditService auditService;
    private final LifecycleStateMachine stateMachine;

    /** 保留期硬删除级联清理依赖的 mapper */
    private final DocumentVersionMapper versionMapper;
    private final DocumentFingerprintMapper fingerprintMapper;
    private final DocumentQualityMapper qualityMapper;
    private final DocumentReviewMapper reviewMapper;
    private final KbDocAclMapper aclMapper;
    private final KbDocAuditLogMapper auditLogMapper;

    /** 审计动作 → 旧 document_review.action 映射（保证旧审核分页可见） */
    private static String toReviewAction(LifecycleAction action) {
        return switch (action) {
            case SUBMIT -> ReviewAction.SUBMIT.name();
            case APPROVE -> ReviewAction.APPROVE.name();
            case REJECT -> ReviewAction.REJECT.name();
            default -> null;
        };
    }

    /** 生命周期动作 → 审计动作常量映射 */
    private static String toAuditAction(LifecycleAction action) {
        return switch (action) {
            case SUBMIT -> KnowledgeAuditService.ACTION_LIFECYCLE_SUBMIT;
            case APPROVE -> KnowledgeAuditService.ACTION_LIFECYCLE_APPROVE;
            case REJECT -> KnowledgeAuditService.ACTION_LIFECYCLE_REJECT;
            case PUBLISH -> KnowledgeAuditService.ACTION_PUBLISH;
            case ARCHIVE -> KnowledgeAuditService.ACTION_ARCHIVE;
            case RESTORE -> KnowledgeAuditService.ACTION_RESTORE;
            case UPDATE -> KnowledgeAuditService.ACTION_LIFECYCLE_SUBMIT;
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transition(LifecycleActionRequest request, Long userId) {
        LifecycleAction action = parseAction(request.getAction());
        transition(request.getDocId(), action, userId, request.getComment());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transition(Long docId, LifecycleAction action, Long userId, String comment) {
        KbDocument doc = kbDocumentService.getById(docId);
        if (doc == null) {
            throw new BizException(404, "文档不存在");
        }
        // 写操作统一要求 editor 权限（归档/恢复/发布/审核均视为文档治理写操作）
        kbPermissionService.checkEditor(doc.getKbId(), userId);

        LifecycleStatus current = parseStatus(doc.getLifecycleStatus());
        KnowledgePolicy policy = policyService.getByKb(doc.getKbId());
        LifecycleStatus next = stateMachine.nextStatus(current, action, policy);
        if (next == null) {
            throw new BizException(String.format("非法生命周期流转: %s --%s--> ?", current, action));
        }

        // 桥接同步 review_status / reviewerId / reviewedAt / archivedAt
        LifecycleBridge.syncReviewFields(doc, next, userId);
        doc.setLifecycleStatus(next.name());
        kbDocumentService.updateById(doc);

        // REVIEW 阶段同步写一条 document_review 流水，保证旧审核分页可见
        String reviewAction = toReviewAction(action);
        if (reviewAction != null) {
            writeReviewFlow(doc, reviewAction, userId, comment);
        }

        // 治理审计
        auditService.record(doc.getTenantId(), userId, docId, toAuditAction(action),
                true, current + "->" + next + (comment == null ? "" : ":" + comment), null);

        log.info("[治理-生命周期] doc={} {} -> {} user={}", docId, current, next, userId);
    }

    @Override
    public LifecycleVo getState(Long docId) {
        KbDocument doc = kbDocumentService.getById(docId);
        if (doc == null) {
            throw new BizException(404, "文档不存在");
        }
        LifecycleVo vo = new LifecycleVo();
        vo.setDocId(doc.getId());
        vo.setKbId(doc.getKbId());
        vo.setDocName(doc.getOriginalName());
        vo.setLifecycleStatus(doc.getLifecycleStatus());
        vo.setReviewStatus(doc.getReviewStatus());
        vo.setVersion(doc.getVersion());
        vo.setReviewerId(doc.getReviewerId());
        vo.setReviewedAt(doc.getReviewedAt());
        vo.setArchivedAt(doc.getArchivedAt());
        vo.setEffectiveFrom(doc.getEffectiveFrom());
        vo.setExpireAt(doc.getExpireAt());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoArchive() {
        // 查询所有 PUBLISHED 文档（lifecycleGate 数据集），逐条按 KB 策略判定是否到归档期
        List<KbDocument> published = kbDocumentService.list(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getLifecycleStatus, LifecycleStatus.PUBLISHED.name()));
        int archived = 0;
        LocalDateTime now = LocalDateTime.now();
        for (KbDocument doc : published) {
            try {
                KnowledgePolicy policy = policyService.getByKb(doc.getKbId());
                if (policy == null || policy.getAutoArchiveDays() == null) {
                    continue;
                }
                // 发布时间代理：优先 reviewedAt，其次 updateTime，最后 createTime
                LocalDateTime publishedAt = doc.getReviewedAt() != null ? doc.getReviewedAt()
                        : (doc.getUpdateTime() != null ? doc.getUpdateTime() : doc.getCreateTime());
                if (publishedAt == null) {
                    continue;
                }
                if (publishedAt.plusDays(policy.getAutoArchiveDays()).isBefore(now)) {
                    LifecycleBridge.syncReviewFields(doc, LifecycleStatus.ARCHIVED, 0L);
                    doc.setLifecycleStatus(LifecycleStatus.ARCHIVED.name());
                    kbDocumentService.updateById(doc);
                    auditService.record(doc.getTenantId(), 0L, doc.getId(),
                            KnowledgeAuditService.ACTION_ARCHIVE, true,
                            "自动归档:autoArchiveDays=" + policy.getAutoArchiveDays(), null);
                    archived++;
                }
            } catch (Exception e) {
                log.warn("[治理-自动归档] doc={} 失败: {}", doc.getId(), e.getMessage());
            }
        }
        if (archived > 0) {
            log.info("[治理-自动归档] 完成，归档 {} 篇", archived);
        }
        return archived;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int purgeRetained() {
        // 查询所有 ARCHIVED 文档，按 KB 策略 retentionDays 判定是否到保留期
        List<KbDocument> archived = kbDocumentService.list(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getLifecycleStatus, LifecycleStatus.ARCHIVED.name()));
        int purged = 0;
        LocalDateTime now = LocalDateTime.now();
        for (KbDocument doc : archived) {
            try {
                KnowledgePolicy policy = policyService.getByKb(doc.getKbId());
                if (policy == null || policy.getRetentionDays() == null || doc.getArchivedAt() == null) {
                    continue;
                }
                if (doc.getArchivedAt().plusDays(policy.getRetentionDays()).isBefore(now)) {
                    // 删前写审计（物理删后无法 JOIN 文档名，故先记）
                    auditService.record(doc.getTenantId(), 0L, doc.getId(),
                            KnowledgeAuditService.ACTION_RETENTION_PURGE, true,
                            "保留期硬删除:retentionDays=" + policy.getRetentionDays(), null);
                    // 级联清理相关数据（best-effort：任一失败仅 warn 不回滚已删主表）
                    cascadeDeleteByDocId(doc.getId());
                    // 物理删除主表（绕过软删）
                    kbDocumentMapper.physicalDeleteById(doc.getId());
                    purged++;
                }
            } catch (Exception e) {
                log.warn("[治理-保留期清理] doc={} 失败: {}", doc.getId(), e.getMessage());
            }
        }
        if (purged > 0) {
            log.info("[治理-保留期清理] 完成，硬删除 {} 篇", purged);
        }
        return purged;
    }

    /** 级联删除文档相关数据：版本/指纹/质量/审核流水/ACL/审计日志 */
    private void cascadeDeleteByDocId(Long docId) {
        try {
            versionMapper.delete(new LambdaQueryWrapper<com.knowledge.kb.governance.entity.DocumentVersion>()
                    .eq(com.knowledge.kb.governance.entity.DocumentVersion::getDocId, docId));
        } catch (Exception e) {
            log.warn("[治理-级联清理] document_version doc={} 失败: {}", docId, e.getMessage());
        }
        try {
            fingerprintMapper.delete(new LambdaQueryWrapper<com.knowledge.kb.governance.entity.DocumentFingerprint>()
                    .eq(com.knowledge.kb.governance.entity.DocumentFingerprint::getDocId, docId));
        } catch (Exception e) {
            log.warn("[治理-级联清理] document_fingerprint doc={} 失败: {}", docId, e.getMessage());
        }
        try {
            qualityMapper.delete(new LambdaQueryWrapper<com.knowledge.kb.governance.entity.DocumentQuality>()
                    .eq(com.knowledge.kb.governance.entity.DocumentQuality::getDocId, docId));
        } catch (Exception e) {
            log.warn("[治理-级联清理] document_quality doc={} 失败: {}", docId, e.getMessage());
        }
        try {
            reviewMapper.delete(new LambdaQueryWrapper<DocumentReview>()
                    .eq(DocumentReview::getDocId, docId));
        } catch (Exception e) {
            log.warn("[治理-级联清理] document_review doc={} 失败: {}", docId, e.getMessage());
        }
        try {
            aclMapper.delete(new LambdaQueryWrapper<com.knowledge.kb.entity.KbDocAcl>()
                    .eq(com.knowledge.kb.entity.KbDocAcl::getDocId, docId));
        } catch (Exception e) {
            log.warn("[治理-级联清理] kb_doc_acl doc={} 失败: {}", docId, e.getMessage());
        }
        try {
            auditLogMapper.delete(new LambdaQueryWrapper<com.knowledge.kb.entity.KbDocAuditLog>()
                    .eq(com.knowledge.kb.entity.KbDocAuditLog::getDocId, docId));
        } catch (Exception e) {
            log.warn("[治理-级联清理] kb_doc_audit_log doc={} 失败: {}", docId, e.getMessage());
        }
    }

    /** 写一条 document_review 流水（保证旧审核分页可见，仅 SUBMIT/APPROVE/REJECT 触发） */
    private void writeReviewFlow(KbDocument doc, String reviewAction, Long userId, String comment) {
        DocumentReview record = new DocumentReview();
        record.setTenantId(doc.getTenantId());
        record.setKbId(doc.getKbId());
        record.setDocId(doc.getId());
        record.setReviewerId(userId);
        record.setAction(reviewAction);
        record.setComment(comment);
        reviewMapper.insert(record);
    }

    private LifecycleAction parseAction(String s) {
        if (s == null || s.isBlank()) {
            throw new BizException("生命周期动作不能为空");
        }
        try {
            return LifecycleAction.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new BizException("非法生命周期动作: " + s);
        }
    }

    private LifecycleStatus parseStatus(String s) {
        if (s == null || s.isBlank()) {
            return LifecycleStatus.DRAFT;
        }
        try {
            return LifecycleStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            return LifecycleStatus.DRAFT;
        }
    }
}
