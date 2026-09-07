package com.knowledge.kb.governance.lifecycle.controller;

import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.annotation.BusinessType;
import com.knowledge.common.annotation.OperLog;
import com.knowledge.common.exception.BizException;
import com.knowledge.common.result.Result;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.governance.lifecycle.dto.LifecycleActionRequest;
import com.knowledge.kb.governance.lifecycle.dto.LifecycleVo;
import com.knowledge.kb.governance.lifecycle.service.KnowledgeLifecycleService;
import com.knowledge.kb.service.KbDocumentService;
import com.knowledge.kb.service.KbPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识生命周期接口：查询文档生命周期状态 / 执行生命周期迁移。
 * <p>【权限】读操作需 viewer 及以上；迁移写操作（SUBMIT/PUBLISH/APPROVE/REJECT/ARCHIVE/RESTORE/UPDATE）需 editor 及以上。
 *
 * @author: lxcechoo@gmail.com
 */
@Tag(name = "知识治理-生命周期接口")
@RestController
@RequestMapping("/governance/lifecycle")
@RequiredArgsConstructor
public class LifecycleController {

    private final KnowledgeLifecycleService lifecycleService;
    private final KbDocumentService kbDocumentService;
    private final KbPermissionService kbPermissionService;

    @Operation(summary = "查询文档生命周期状态")
    @GetMapping("/{docId}")
    public Result<LifecycleVo> getState(@PathVariable Long docId) {
        Long userId = SecurityUtils.currentUserId();
        requireViewer(docId, userId);
        return Result.success(lifecycleService.getState(docId));
    }

    @Operation(summary = "执行生命周期迁移（SUBMIT/PUBLISH/APPROVE/REJECT/ARCHIVE/RESTORE/UPDATE）")
    @PostMapping
    @OperLog(title = "知识治理-生命周期", businessType = BusinessType.UPDATE, recordParam = false)
    public Result<Void> transition(@Valid @RequestBody LifecycleActionRequest request) {
        Long userId = SecurityUtils.currentUserId();
        requireEditor(request.getDocId(), userId);
        lifecycleService.transition(request, userId);
        return Result.success();
    }

    /* ==================== 权限校验 ==================== */

    private void requireViewer(Long docId, Long userId) {
        KbDocument doc = kbDocumentService.getById(docId);
        if (doc == null) {
            throw new BizException(404, "文档不存在");
        }
        kbPermissionService.checkViewer(doc.getKbId(), userId);
    }

    private KbDocument requireEditor(Long docId, Long userId) {
        KbDocument doc = kbDocumentService.getById(docId);
        if (doc == null) {
            throw new BizException(404, "文档不存在");
        }
        kbPermissionService.checkEditor(doc.getKbId(), userId);
        return doc;
    }
}
