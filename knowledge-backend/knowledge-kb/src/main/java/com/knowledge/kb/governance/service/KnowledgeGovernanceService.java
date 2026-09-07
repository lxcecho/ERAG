package com.knowledge.kb.governance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.kb.governance.dto.DuplicateHandleRequest;
import com.knowledge.kb.governance.dto.DuplicateVo;
import com.knowledge.kb.governance.dto.GovernanceQuery;
import com.knowledge.kb.governance.dto.QualityVo;
import com.knowledge.kb.governance.dto.ReviewRequest;
import com.knowledge.kb.governance.dto.ReviewVo;
import com.knowledge.kb.governance.dto.ValidityRequest;
import com.knowledge.kb.governance.dto.VersionVo;

import java.util.Collection;
import java.util.Set;

/**
 * 知识治理服务接口。
 * <p>统一承载五大治理能力：文档重复检测 / 版本管理 / 有效期 / 审核状态 / 质量评分。
 * <p>调用方两类：
 * <ul>
 *   <li>{@link com.knowledge.kb.governance.listener.GovernanceEventListener}：文档解析完成后异步触发指纹计算 + 去重检测 + 质量评分。</li>
 *   <li>{@link com.knowledge.kb.governance.controller.GovernanceController}：人工查询 / 审核 / 版本回滚 / 有效期设置。</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
public interface KnowledgeGovernanceService {

    /* ==================== 1. 文档重复检测 ==================== */

    /** 重复关系分页（携带两文档名） */
    IPage<DuplicateVo> duplicatePage(GovernanceQuery query);

    /** 处理重复关系（CONFIRMED 确认 / IGNORED 忽略） */
    void handleDuplicate(DuplicateHandleRequest request, Long userId);

    /* ==================== 2. 文档版本管理 ==================== */

    /** 文档版本历史 */
    IPage<VersionVo> versionPage(GovernanceQuery query);

    /** 记录文档新版本（上传/更新时由业务层调用，归档当前版本快照） */
    void recordVersion(Long docId, String changeLog, Long userId);

    /* ==================== 3. 文档有效期 ==================== */

    /** 设置文档有效期（生效/过期时间） */
    void setValidity(Long docId, ValidityRequest request, Long userId);

    /* ==================== 4. 文档审核状态 ==================== */

    /** 审核记录流水 */
    IPage<ReviewVo> reviewPage(GovernanceQuery query);

    /** 提交审核 / 审核通过 / 审核驳回（状态机 + 审计流水） */
    void review(ReviewRequest request, Long reviewerId);

    /* ==================== 5. 知识质量评分 ==================== */

    /** 质量评分分页 */
    IPage<QualityVo> qualityPage(GovernanceQuery query);

    /** 手动重评单个文档（规则评分） */
    QualityVo rescore(Long docId);

    /* ==================== 治理埋点（事件触发） ==================== */

    /**
     * 文档解析完成后的治理埋点：计算指纹 → 检测重复 → 质量评分。
     * <p>由 GovernanceEventListener 在 DocumentParsedEvent 后异步调用。
     *
     * @param tenantId  租户ID
     * @param kbId       知识库ID
     * @param docId      文档ID
     * @param text       解析后纯文本
     * @param chunkCount 切片数量
     * @param md5        文件 MD5
     */
    void onDocumentParsed(Long tenantId, Long kbId, Long docId, String text, int chunkCount, String md5);

    /* ==================== 检索过滤门禁 ==================== */

    /**
     * 治理检索门禁：从给定文档ID集合中剔除「未审核 / 已过期」文档。
     * <p>由 RAG 检索层调用，依据 kb.governance.review-gate / expire-gate 开关决定是否过滤。
     *
     * @param docIds 待校验文档ID集合
     * @return 通过治理门禁的文档ID集合（开关关闭时原样返回）
     */
    Set<Long> filterGovernanceValid(Collection<Long> docIds);

    /* ==================== 定时任务 ==================== */

    /** 扫描并标记已过期文档（expire_at < now 的 APPROVED 文档置为 REJECTED） */
    int scanExpiredDocuments();
}
