package com.knowledge.kb.governance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.annotation.BusinessType;
import com.knowledge.common.annotation.OperLog;
import com.knowledge.common.exception.BizException;
import com.knowledge.common.result.Result;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.governance.dto.DuplicateHandleRequest;
import com.knowledge.kb.governance.dto.DuplicateVo;
import com.knowledge.kb.governance.dto.GovernanceQuery;
import com.knowledge.kb.governance.dto.QualityVo;
import com.knowledge.kb.governance.dto.ReviewRequest;
import com.knowledge.kb.governance.dto.ReviewVo;
import com.knowledge.kb.governance.dto.ValidityRequest;
import com.knowledge.kb.governance.dto.VersionVo;
import com.knowledge.kb.governance.lifecycle.dto.AuditQuery;
import com.knowledge.kb.governance.lifecycle.dto.AuditVo;
import com.knowledge.kb.governance.lifecycle.dto.VersionRollbackRequest;
import com.knowledge.kb.governance.lifecycle.service.KnowledgeAuditService;
import com.knowledge.kb.governance.lifecycle.service.KnowledgeVersionService;
import com.knowledge.kb.governance.service.KnowledgeGovernanceService;
import com.knowledge.kb.service.KbDocumentService;
import com.knowledge.kb.service.KbPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识治理接口：文档重复检测 / 版本管理（含回滚）/ 有效期 / 审核状态 / 质量评分 / 审计流水。
 * <p>【权限】读操作需 KB viewer 及以上；写操作（审核/有效期/去重处理/重评/版本回滚）需 editor 及以上。
 * <p>生命周期迁移与策略管理见 {@code LifecycleController} / {@code PolicyController}。
 *
 * @author: lxcechoo@gmail.com
 */
@Tag(name = "知识治理接口")
@RestController
@RequestMapping("/governance")
@RequiredArgsConstructor
public class GovernanceController {

    private final KnowledgeGovernanceService governanceService;
    private final KnowledgeVersionService versionService;
    private final KnowledgeAuditService auditService;
    private final KbDocumentService kbDocumentService;
    private final KbPermissionService kbPermissionService;

    /* ==================== 1. 文档重复检测 ==================== */

    @Operation(summary = "重复文档分页列表")
    @GetMapping("/duplicates")
    public Result<IPage<DuplicateVo>> duplicatePage(GovernanceQuery query) {
        checkRead(query);
        return Result.success(governanceService.duplicatePage(query));
    }

    @Operation(summary = "处理重复关系（确认/忽略）")
    @PutMapping("/duplicates/handle")
    @OperLog(title = "知识治理-去重", businessType = BusinessType.UPDATE, recordParam = false)
    public Result<Void> handleDuplicate(@Valid @RequestBody DuplicateHandleRequest request) {
        Long userId = SecurityUtils.currentUserId();
        governanceService.handleDuplicate(request, userId);
        return Result.success();
    }

    /* ==================== 2. 文档版本管理 ==================== */

    @Operation(summary = "文档版本历史")
    @GetMapping("/versions")
    public Result<IPage<VersionVo>> versionPage(GovernanceQuery query) {
        checkRead(query);
        return Result.success(versionService.versionPage(query));
    }

    @Operation(summary = "版本回滚（恢复至指定历史版本，版本号自增）")
    @PostMapping("/versions/rollback")
    @OperLog(title = "知识治理-版本回滚", businessType = BusinessType.UPDATE, recordParam = false)
    public Result<Void> rollback(@Valid @RequestBody VersionRollbackRequest request) {
        Long userId = SecurityUtils.currentUserId();
        requireEditor(request.getDocId(), userId);
        versionService.rollback(request, userId);
        return Result.success();
    }

    /* ==================== 3. 文档有效期 ==================== */

    @Operation(summary = "设置文档有效期")
    @PutMapping("/validity/{docId}")
    @OperLog(title = "知识治理-有效期", businessType = BusinessType.UPDATE, recordParam = false)
    public Result<Void> setValidity(@PathVariable Long docId, @RequestBody ValidityRequest request) {
        Long userId = SecurityUtils.currentUserId();
        requireEditor(docId, userId);
        governanceService.setValidity(docId, request, userId);
        return Result.success();
    }

    /* ==================== 4. 文档审核状态 ==================== */

    @Operation(summary = "审核记录流水")
    @GetMapping("/reviews")
    public Result<IPage<ReviewVo>> reviewPage(GovernanceQuery query) {
        checkRead(query);
        return Result.success(governanceService.reviewPage(query));
    }

    @Operation(summary = "提交/通过/驳回审核")
    @PostMapping("/reviews")
    @OperLog(title = "知识治理-审核", businessType = BusinessType.UPDATE, recordParam = false)
    public Result<Void> review(@Valid @RequestBody ReviewRequest request) {
        Long userId = SecurityUtils.currentUserId();
        requireEditor(request.getDocId(), userId);
        governanceService.review(request, userId);
        return Result.success();
    }

    /* ==================== 5. 知识质量评分 ==================== */

    @Operation(summary = "质量评分分页列表")
    @GetMapping("/quality")
    public Result<IPage<QualityVo>> qualityPage(GovernanceQuery query) {
        checkRead(query);
        return Result.success(governanceService.qualityPage(query));
    }

    @Operation(summary = "手动重评文档质量")
    @PostMapping("/quality/{docId}/rescore")
    @OperLog(title = "知识治理-评分", businessType = BusinessType.UPDATE, recordParam = false)
    public Result<QualityVo> rescore(@PathVariable Long docId) {
        Long userId = SecurityUtils.currentUserId();
        requireEditor(docId, userId);
        return Result.success(governanceService.rescore(docId));
    }

    /* ==================== 6. 知识审计流水 ==================== */

    @Operation(summary = "治理审计流水分页（生命周期/版本/策略/归档/保留期/访问）")
    @GetMapping("/audits")
    public Result<IPage<AuditVo>> auditPage(AuditQuery query) {
        if (query != null && query.getKbId() != null) {
            kbPermissionService.checkViewer(query.getKbId(), SecurityUtils.currentUserId());
        }
        return Result.success(auditService.auditPage(query));
    }

    /* ==================== 权限校验 ==================== */

    /** 读操作：指定 kbId 时校验 viewer 权限 */
    private void checkRead(GovernanceQuery query) {
        if (query != null && query.getKbId() != null) {
            kbPermissionService.checkViewer(query.getKbId(), SecurityUtils.currentUserId());
        }
    }

    /** 写操作：加载文档并校验 editor 权限 */
    private KbDocument requireEditor(Long docId, Long userId) {
        KbDocument doc = kbDocumentService.getById(docId);
        if (doc == null) {
            throw new BizException(404, "文档不存在");
        }
        kbPermissionService.checkEditor(doc.getKbId(), userId);
        return doc;
    }
}
