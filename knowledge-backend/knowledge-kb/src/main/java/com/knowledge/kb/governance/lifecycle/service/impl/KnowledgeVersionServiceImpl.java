package com.knowledge.kb.governance.lifecycle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.governance.dto.GovernanceQuery;
import com.knowledge.kb.governance.dto.VersionVo;
import com.knowledge.kb.governance.entity.DocumentVersion;
import com.knowledge.kb.governance.lifecycle.dto.VersionRollbackRequest;
import com.knowledge.kb.governance.lifecycle.entity.KnowledgePolicy;
import com.knowledge.kb.governance.lifecycle.enums.LifecycleAction;
import com.knowledge.kb.governance.lifecycle.enums.LifecycleStatus;
import com.knowledge.kb.governance.lifecycle.service.KnowledgeAuditService;
import com.knowledge.kb.governance.lifecycle.service.KnowledgeLifecycleService;
import com.knowledge.kb.governance.lifecycle.service.KnowledgePolicyService;
import com.knowledge.kb.governance.lifecycle.service.KnowledgeVersionService;
import com.knowledge.kb.governance.mapper.DocumentVersionMapper;
import com.knowledge.kb.governance.service.KnowledgeGovernanceService;
import com.knowledge.kb.service.KbDocumentService;
import com.knowledge.kb.service.KbPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识版本门面实现：透传既有 {@link KnowledgeGovernanceService} 版本能力，叠加 {@link #rollback}。
 * <p>回滚设计为「向前回滚」：不删除历史版本，而是以新版本号写入目标快照内容，保证版本链可追溯。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeVersionServiceImpl implements KnowledgeVersionService {

    private final KnowledgeGovernanceService governanceService;
    private final KbDocumentService kbDocumentService;
    private final KbPermissionService kbPermissionService;
    private final DocumentVersionMapper versionMapper;
    private final KnowledgePolicyService policyService;
    private final KnowledgeLifecycleService lifecycleService;
    private final KnowledgeAuditService auditService;

    @Override
    public IPage<VersionVo> versionPage(GovernanceQuery query) {
        return governanceService.versionPage(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordVersion(Long docId, String changeLog, Long userId) {
        governanceService.recordVersion(docId, changeLog, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(VersionRollbackRequest request, Long userId) {
        Long docId = request.getDocId();
        KbDocument doc = kbDocumentService.getById(docId);
        if (doc == null) {
            throw new BizException(404, "文档不存在");
        }
        kbPermissionService.checkEditor(doc.getKbId(), userId);

        // 1. 校验目标版本存在
        DocumentVersion target = versionMapper.selectOne(new LambdaQueryWrapper<DocumentVersion>()
                .eq(DocumentVersion::getDocId, docId)
                .eq(DocumentVersion::getVersion, request.getVersion())
                .last("LIMIT 1"));
        if (target == null) {
            throw new BizException("目标版本不存在: " + request.getVersion());
        }

        // 2. 新版本号 = 当前版本 + 1
        int currentVersion = doc.getVersion() == null ? 1 : doc.getVersion();
        int newVersion = currentVersion + 1;

        // 3. 目标快照写回主表，version 自增
        doc.setStoredName(target.getStoredName());
        doc.setFilePath(target.getFilePath());
        doc.setFileSize(target.getFileSize());
        doc.setMd5(target.getMd5());
        doc.setVersion(newVersion);
        kbDocumentService.updateById(doc);

        // 4. 插入新 DocumentVersion（版本号 = newVersion，避开 uk_doc_version 重复）
        DocumentVersion snapshot = new DocumentVersion();
        snapshot.setTenantId(doc.getTenantId());
        snapshot.setKbId(doc.getKbId());
        snapshot.setDocId(docId);
        snapshot.setVersion(newVersion);
        snapshot.setStoredName(target.getStoredName());
        snapshot.setFilePath(target.getFilePath());
        snapshot.setFileSize(target.getFileSize());
        snapshot.setMd5(target.getMd5());
        snapshot.setChangeLog("回滚至版本 " + request.getVersion());
        snapshot.setCreatorId(userId);
        versionMapper.insert(snapshot);

        // 5. 若 PUBLISHED 且 requireReview=true → 触发 UPDATE 重审
        KnowledgePolicy policy = policyService.getByKb(doc.getKbId());
        boolean requireReview = policy == null || policy.getRequireReview() == null || policy.getRequireReview();
        LifecycleStatus current = parseStatus(doc.getLifecycleStatus());
        if (current == LifecycleStatus.PUBLISHED && requireReview) {
            // 触发生命周期 UPDATE 迁移至 REVIEW（不重复写 VERSION_ROLLBACK 审计，UPDATE 自带审计）
            lifecycleService.transition(docId, LifecycleAction.UPDATE, userId, "版本回滚触发重审");
        }

        // 6. 记 VERSION_ROLLBACK 审计
        auditService.record(doc.getTenantId(), userId, docId,
                KnowledgeAuditService.ACTION_VERSION_ROLLBACK, true,
                "from v" + currentVersion + " to v" + request.getVersion(), null);

        log.info("[治理-版本回滚] doc={} from v{} to v{}(via v{}) user={}",
                docId, currentVersion, newVersion, request.getVersion(), userId);
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
