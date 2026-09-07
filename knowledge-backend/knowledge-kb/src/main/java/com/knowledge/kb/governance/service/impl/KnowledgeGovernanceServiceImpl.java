package com.knowledge.kb.governance.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.governance.config.GovernanceProperties;
import com.knowledge.kb.governance.dto.DuplicateHandleRequest;
import com.knowledge.kb.governance.dto.DuplicateVo;
import com.knowledge.kb.governance.dto.GovernanceQuery;
import com.knowledge.kb.governance.dto.QualityVo;
import com.knowledge.kb.governance.dto.ReviewRequest;
import com.knowledge.kb.governance.dto.ReviewVo;
import com.knowledge.kb.governance.dto.ValidityRequest;
import com.knowledge.kb.governance.dto.VersionVo;
import com.knowledge.kb.governance.engine.QualityResult;
import com.knowledge.kb.governance.engine.QualityScorer;
import com.knowledge.kb.governance.engine.SimHashCalculator;
import com.knowledge.kb.governance.entity.DocumentDuplicate;
import com.knowledge.kb.governance.entity.DocumentFingerprint;
import com.knowledge.kb.governance.entity.DocumentQuality;
import com.knowledge.kb.governance.entity.DocumentReview;
import com.knowledge.kb.governance.entity.DocumentVersion;
import com.knowledge.kb.governance.enums.DuplicateStatus;
import com.knowledge.kb.governance.enums.DuplicateType;
import com.knowledge.kb.governance.enums.ReviewAction;
import com.knowledge.kb.governance.enums.ReviewStatus;
import com.knowledge.kb.governance.mapper.DocumentDuplicateMapper;
import com.knowledge.kb.governance.mapper.DocumentFingerprintMapper;
import com.knowledge.kb.governance.mapper.DocumentQualityMapper;
import com.knowledge.kb.governance.mapper.DocumentReviewMapper;
import com.knowledge.kb.governance.mapper.DocumentVersionMapper;
import com.knowledge.kb.governance.service.KnowledgeGovernanceService;
import com.knowledge.kb.service.KbDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识治理服务实现。
 * <p>统一承载：文档重复检测 / 版本管理 / 有效期 / 审核状态 / 质量评分。
 * <p>埋点入口 {@link #onDocumentParsed} 由 GovernanceEventListener 异步调用，
 * 完成指纹计算 → 去重检测 → 质量评分三步，任一子步骤失败仅 warn 不影响主流程（best-effort）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGovernanceServiceImpl implements KnowledgeGovernanceService {

    private final DocumentFingerprintMapper fingerprintMapper;
    private final DocumentDuplicateMapper duplicateMapper;
    private final DocumentVersionMapper versionMapper;
    private final DocumentReviewMapper reviewMapper;
    private final DocumentQualityMapper qualityMapper;
    private final KbDocumentService kbDocumentService;
    private final SimHashCalculator simHashCalculator;
    private final QualityScorer qualityScorer;
    private final GovernanceProperties properties;

    /* ==================== 1. 文档重复检测 ==================== */

    @Override
    public IPage<DuplicateVo> duplicatePage(GovernanceQuery query) {
        return duplicateMapper.selectDuplicatePage(query.toPage(), query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleDuplicate(DuplicateHandleRequest request, Long userId) {
        DocumentDuplicate dup = duplicateMapper.selectById(request.getId());
        if (dup == null) {
            throw new BizException("重复记录不存在");
        }
        DuplicateStatus status;
        try {
            status = DuplicateStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BizException("非法处理状态: " + request.getStatus());
        }
        dup.setStatus(status.name());
        duplicateMapper.updateById(dup);
        log.info("[治理-去重] 处理重复记录 id={} status={} user={}", request.getId(), status, userId);
    }

    /* ==================== 2. 文档版本管理 ==================== */

    @Override
    public IPage<VersionVo> versionPage(GovernanceQuery query) {
        Page<DocumentVersion> page = query.toPage();
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentVersion> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentVersion>()
                        .eq(query.getDocId() != null, DocumentVersion::getDocId, query.getDocId())
                        .eq(query.getKbId() != null, DocumentVersion::getKbId, query.getKbId())
                        .orderByDesc(DocumentVersion::getVersion);
        IPage<DocumentVersion> result = versionMapper.selectPage(page, wrapper);
        return result.convert(this::toVersionVo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordVersion(Long docId, String changeLog, Long userId) {
        KbDocument doc = kbDocumentService.getById(docId);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        DocumentVersion version = new DocumentVersion();
        version.setTenantId(doc.getTenantId());
        version.setKbId(doc.getKbId());
        version.setDocId(docId);
        version.setVersion(doc.getVersion() == null ? 1 : doc.getVersion());
        version.setStoredName(doc.getStoredName());
        version.setFilePath(doc.getFilePath());
        version.setFileSize(doc.getFileSize());
        version.setMd5(doc.getMd5());
        version.setChangeLog(changeLog);
        version.setCreatorId(userId);
        versionMapper.insert(version);
        log.info("[治理-版本] 归档版本 doc={} version={}", docId, version.getVersion());
    }

    private VersionVo toVersionVo(DocumentVersion v) {
        VersionVo vo = new VersionVo();
        BeanUtils.copyProperties(v, vo);
        return vo;
    }

    /* ==================== 3. 文档有效期 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setValidity(Long docId, ValidityRequest request, Long userId) {
        KbDocument doc = kbDocumentService.getById(docId);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        // 生效时间不得晚于过期时间
        if (request.getEffectiveFrom() != null && request.getExpireAt() != null
                && request.getEffectiveFrom().isAfter(request.getExpireAt())) {
            throw new BizException("生效时间不能晚于过期时间");
        }
        doc.setEffectiveFrom(request.getEffectiveFrom());
        doc.setExpireAt(request.getExpireAt());
        kbDocumentService.updateById(doc);
        log.info("[治理-有效期] 设置文档有效期 doc={} from={} expire={} user={}",
                docId, request.getEffectiveFrom(), request.getExpireAt(), userId);
    }

    /* ==================== 4. 文档审核状态 ==================== */

    @Override
    public IPage<ReviewVo> reviewPage(GovernanceQuery query) {
        Page<DocumentReview> page = query.toPage();
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentReview> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentReview>()
                        .eq(query.getDocId() != null, DocumentReview::getDocId, query.getDocId())
                        .eq(query.getKbId() != null, DocumentReview::getKbId, query.getKbId())
                        .orderByDesc(DocumentReview::getCreateTime);
        IPage<DocumentReview> result = reviewMapper.selectPage(page, wrapper);
        return result.convert(this::toReviewVo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(ReviewRequest request, Long reviewerId) {
        KbDocument doc = kbDocumentService.getById(request.getDocId());
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        ReviewAction action;
        try {
            action = ReviewAction.valueOf(request.getAction());
        } catch (IllegalArgumentException e) {
            throw new BizException("非法审核动作: " + request.getAction());
        }

        ReviewStatus current = parseStatus(doc.getReviewStatus());
        ReviewStatus next = transition(current, action);
        if (next == null) {
            throw new BizException(String.format("非法状态流转: %s → %s", current, action));
        }

        // 更新文档审核状态
        doc.setReviewStatus(next.name());
        if (action == ReviewAction.APPROVE || action == ReviewAction.REJECT) {
            doc.setReviewerId(reviewerId);
            doc.setReviewedAt(LocalDateTime.now());
        }
        kbDocumentService.updateById(doc);

        // 审计流水
        DocumentReview record = new DocumentReview();
        record.setTenantId(doc.getTenantId());
        record.setKbId(doc.getKbId());
        record.setDocId(doc.getId());
        record.setReviewerId(reviewerId);
        record.setAction(action.name());
        record.setComment(request.getComment());
        reviewMapper.insert(record);

        log.info("[治理-审核] doc={} {} → {} reviewer={}", doc.getId(), current, next, reviewerId);
    }

    /** 审核状态机：返回流转后的状态，非法流转返回 null */
    private ReviewStatus transition(ReviewStatus current, ReviewAction action) {
        // SUBMIT：任意态可重新提交审核（回到 PENDING）
        if (action == ReviewAction.SUBMIT) return ReviewStatus.PENDING;
        // APPROVE / REJECT：仅 PENDING 态可审核
        if (current != ReviewStatus.PENDING) return null;
        return action == ReviewAction.APPROVE ? ReviewStatus.APPROVED : ReviewStatus.REJECTED;
    }

    private ReviewStatus parseStatus(String s) {
        if (s == null || s.isBlank()) return ReviewStatus.PENDING;
        try {
            return ReviewStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            return ReviewStatus.PENDING;
        }
    }

    private ReviewVo toReviewVo(DocumentReview r) {
        ReviewVo vo = new ReviewVo();
        BeanUtils.copyProperties(r, vo);
        return vo;
    }

    /* ==================== 5. 知识质量评分 ==================== */

    @Override
    public IPage<QualityVo> qualityPage(GovernanceQuery query) {
        return qualityMapper.selectQualityPage(query.toPage(), query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityVo rescore(Long docId) {
        KbDocument doc = kbDocumentService.getById(docId);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        // 取已存储的指纹统计量（contentLength / tokenCount）
        DocumentFingerprint fp = fingerprintMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentFingerprint>()
                        .eq(DocumentFingerprint::getDocId, docId)
                        .last("LIMIT 1"));
        int contentLength = fp != null && fp.getContentLength() != null ? fp.getContentLength() : 0;
        int chunkCount = doc.getChunkCount() != null ? doc.getChunkCount() : 0;

        QualityResult result = qualityScorer.scoreByStats(contentLength, chunkCount, doc.getCreateTime());
        saveQuality(doc, result);
        return toQualityVo(doc, result);
    }

    private void saveQuality(KbDocument doc, QualityResult result) {
        DocumentQuality quality = new DocumentQuality();
        quality.setTenantId(doc.getTenantId());
        quality.setKbId(doc.getKbId());
        quality.setDocId(doc.getId());
        quality.setScore(result.score());
        quality.setCompleteness(result.completeness());
        quality.setFreshness(result.freshness());
        quality.setStructure(result.structure());
        quality.setCoverage(result.coverage());
        quality.setSummary(result.summary());
        quality.setEvaluator(result.evaluator());
        qualityMapper.insert(quality);

        // 回写主表质量评分
        doc.setQualityScore(result.score());
        kbDocumentService.updateById(doc);
    }

    private QualityVo toQualityVo(KbDocument doc, QualityResult result) {
        QualityVo vo = new QualityVo();
        vo.setKbId(doc.getKbId());
        vo.setDocId(doc.getId());
        vo.setDocName(doc.getOriginalName());
        vo.setScore(result.score());
        vo.setCompleteness(result.completeness());
        vo.setFreshness(result.freshness());
        vo.setStructure(result.structure());
        vo.setCoverage(result.coverage());
        vo.setSummary(result.summary());
        vo.setEvaluator(result.evaluator());
        return vo;
    }

    /* ==================== 治理埋点（事件触发） ==================== */

    @Override
    public void onDocumentParsed(Long tenantId, Long kbId, Long docId, String text, int chunkCount, String md5) {
        if (!properties.isEnabled()) {
            return;
        }
        log.info("[治理-埋点] 开始治理流程 doc={} kb={} chunkCount={}", docId, kbId, chunkCount);
        try {
            // 1. 计算并落库指纹
            long simhash = simHashCalculator.simHash(text);
            saveFingerprint(tenantId, kbId, docId, md5, simhash, text, chunkCount);

            // 2. 检测重复（best-effort：异常不阻断后续评分）
            try {
                detectDuplicates(tenantId, kbId, docId, md5, simhash);
            } catch (Exception e) {
                log.warn("[治理-去重] 检测失败 doc={}: {}", docId, e.getMessage());
            }

            // 3. 质量评分（best-effort）
            try {
                KbDocument doc = kbDocumentService.getById(docId);
                if (doc != null) {
                    QualityResult result = qualityScorer.score(text, chunkCount, doc.getCreateTime());
                    saveQuality(doc, result);
                    log.info("[治理-评分] doc={} score={}", docId, result.score());
                }
            } catch (Exception e) {
                log.warn("[治理-评分] 评分失败 doc={}: {}", docId, e.getMessage());
            }
        } catch (Exception e) {
            log.error("[治理-埋点] 治理流程异常 doc={}", docId, e);
        }
    }

    private void saveFingerprint(Long tenantId, Long kbId, Long docId, String md5,
                                 long simhash, String text, int chunkCount) {
        // 同文档已有指纹则更新（重传/重解析场景）
        DocumentFingerprint existing = fingerprintMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentFingerprint>()
                        .eq(DocumentFingerprint::getDocId, docId)
                        .last("LIMIT 1"));
        DocumentFingerprint fp = existing != null ? existing : new DocumentFingerprint();
        fp.setTenantId(tenantId);
        fp.setKbId(kbId);
        fp.setDocId(docId);
        fp.setMd5(md5);
        fp.setSimhash(simhash);
        fp.setContentLength(text == null ? 0 : text.length());
        fp.setTokenCount(chunkCount);
        if (existing != null) {
            fingerprintMapper.updateById(fp);
        } else {
            fingerprintMapper.insert(fp);
        }
    }

    /**
     * 重复检测：MD5 精确重复 + SimHash 近似重复。
     * <p>scope：duplicateScopeKbOnly=true 仅同 KB 比对，否则全租户比对。
     * <p>对每个命中对，保证 doc_id1 < doc_id2 并去重（已存在则跳过）。
     */
    private void detectDuplicates(Long tenantId, Long kbId, Long docId, String md5, long simhash) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentFingerprint> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentFingerprint>()
                        .ne(DocumentFingerprint::getDocId, docId)
                        .eq(properties.isDuplicateScopeKbOnly(), DocumentFingerprint::getKbId, kbId)
                        .eq(!properties.isDuplicateScopeKbOnly(), DocumentFingerprint::getTenantId, tenantId);
        List<DocumentFingerprint> candidates = fingerprintMapper.selectList(wrapper);

        List<DocumentDuplicate> toInsert = new ArrayList<>();
        // 精确重复：MD5 相同
        for (DocumentFingerprint c : candidates) {
            if (md5 != null && !md5.isBlank() && md5.equals(c.getMd5())) {
                addDuplicateIfAbsent(toInsert, tenantId, kbId, docId, c.getDocId(),
                        new BigDecimal("1.000"), DuplicateType.EXACT);
            }
        }
        // 近似重复：SimHash Hamming 距离 ≤ 阈值
        for (DocumentFingerprint c : candidates) {
            if (c.getSimhash() == null) continue;
            int distance = simHashCalculator.hammingDistance(simhash, c.getSimhash());
            if (distance <= properties.getSimhashThreshold()) {
                double sim = simHashCalculator.similarity(simhash, c.getSimhash());
                addDuplicateIfAbsent(toInsert, tenantId, kbId, docId, c.getDocId(),
                        BigDecimal.valueOf(sim).setScale(3, RoundingMode.HALF_UP), DuplicateType.NEAR);
            }
        }
        if (!toInsert.isEmpty()) {
            toInsert.forEach(duplicateMapper::insert);
            log.info("[治理-去重] doc={} 检出 {} 对重复", docId, toInsert.size());
        }
    }

    /** 构造重复对（doc_id1 < doc_id2），并跳过已存在的对 */
    private void addDuplicateIfAbsent(List<DocumentDuplicate> toInsert, Long tenantId, Long kbId,
                                      Long docA, Long docB, BigDecimal similarity, DuplicateType type) {
        long min = Math.min(docA, docB);
        long max = Math.max(docA, docB);
        Long count = duplicateMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentDuplicate>()
                        .eq(DocumentDuplicate::getDocId1, min)
                        .eq(DocumentDuplicate::getDocId2, max));
        if (count != null && count > 0) {
            return;
        }
        DocumentDuplicate dup = new DocumentDuplicate();
        dup.setTenantId(tenantId);
        dup.setKbId(kbId);
        dup.setDocId1(min);
        dup.setDocId2(max);
        dup.setSimilarity(similarity);
        dup.setDupType(type.name());
        dup.setStatus(DuplicateStatus.PENDING.name());
        toInsert.add(dup);
    }

    /* ==================== 检索过滤门禁 ==================== */

    @Override
    public Set<Long> filterGovernanceValid(Collection<Long> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return new HashSet<>();
        }
        // 三门禁全关闭：原样返回
        if (!properties.isReviewGate() && !properties.isExpireGate() && !properties.isLifecycleGate()) {
            return new HashSet<>(docIds);
        }
        List<KbDocument> docs = kbDocumentService.listByIds(docIds);
        LocalDateTime now = LocalDateTime.now();
        return docs.stream()
                .filter(doc -> {
                    // 生命周期门禁：仅 PUBLISHED 通过（顶层治理状态，排除 DRAFT/REVIEW/ARCHIVED）
                    if (properties.isLifecycleGate()
                            && !isPublished(doc.getLifecycleStatus())) {
                        return false;
                    }
                    // 审核门禁：仅 APPROVED 通过
                    if (properties.isReviewGate()
                            && parseStatus(doc.getReviewStatus()) != ReviewStatus.APPROVED) {
                        return false;
                    }
                    // 有效期门禁：过期文档排除（expire_at < now）
                    if (properties.isExpireGate()
                            && doc.getExpireAt() != null && doc.getExpireAt().isBefore(now)) {
                        return false;
                    }
                    // 生效时间未到也排除
                    if (properties.isExpireGate()
                            && doc.getEffectiveFrom() != null && doc.getEffectiveFrom().isAfter(now)) {
                        return false;
                    }
                    return true;
                })
                .map(KbDocument::getId)
                .collect(Collectors.toSet());
    }

    /** 生命周期状态是否为 PUBLISHED（空值/非法值视为非发布态，门禁开启时被排除） */
    private boolean isPublished(String lifecycleStatus) {
        return lifecycleStatus != null
                && "PUBLISHED".equals(lifecycleStatus);
    }

    /* ==================== 定时任务 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanExpiredDocuments() {
        // 查询已过期但仍是 APPROVED 的文档（避免重复处理）
        List<KbDocument> expired = kbDocumentService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getReviewStatus, ReviewStatus.APPROVED.name())
                        .isNotNull(KbDocument::getExpireAt)
                        .lt(KbDocument::getExpireAt, LocalDateTime.now()));
        for (KbDocument doc : expired) {
            doc.setReviewStatus(ReviewStatus.REJECTED.name());
            kbDocumentService.updateById(doc);
            // 写入审计流水
            DocumentReview record = new DocumentReview();
            record.setTenantId(doc.getTenantId());
            record.setKbId(doc.getKbId());
            record.setDocId(doc.getId());
            record.setReviewerId(0L);
            record.setAction(ReviewAction.REJECT.name());
            record.setComment("系统定时任务：文档已过期自动驳回");
            reviewMapper.insert(record);
        }
        if (!expired.isEmpty()) {
            log.info("[治理-定时] 扫描过期文档完成，自动驳回 {} 篇", expired.size());
        }
        return expired.size();
    }
}
