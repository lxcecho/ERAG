package com.knowledge.kb.governance.lifecycle.controller;

import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.annotation.BusinessType;
import com.knowledge.common.annotation.OperLog;
import com.knowledge.common.result.Result;
import com.knowledge.kb.governance.lifecycle.dto.PolicyRequest;
import com.knowledge.kb.governance.lifecycle.dto.PolicyVo;
import com.knowledge.kb.governance.lifecycle.service.KnowledgePolicyService;
import com.knowledge.kb.service.KbPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识治理策略接口：每 KB 一份策略（强制审核 / 自动归档天数 / 保留期 / 审核角色 / 审核超时）。
 * <p>【权限】读策略需 viewer 及以上；保存/删除策略需 owner（策略影响整个 KB 治理基线）。
 *
 * @author: lxcechoo@gmail.com
 */
@Tag(name = "知识治理-策略接口")
@RestController
@RequestMapping("/governance/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final KnowledgePolicyService policyService;
    private final KbPermissionService kbPermissionService;

    @Operation(summary = "查询知识库治理策略（无配置返回默认策略 requireReview=true）")
    @GetMapping("/{kbId}")
    public Result<PolicyVo> get(@PathVariable Long kbId) {
        kbPermissionService.checkViewer(kbId, SecurityUtils.currentUserId());
        return Result.success(policyService.getVoByKb(kbId));
    }

    @Operation(summary = "保存或更新知识库治理策略")
    @PutMapping
    @OperLog(title = "知识治理-策略", businessType = BusinessType.UPDATE, recordParam = false)
    public Result<Void> saveOrUpdate(@Valid @RequestBody PolicyRequest request) {
        Long userId = SecurityUtils.currentUserId();
        kbPermissionService.checkOwner(request.getKbId(), userId);
        policyService.saveOrUpdate(request, userId);
        return Result.success();
    }

    @Operation(summary = "删除知识库治理策略（恢复为默认策略）")
    @DeleteMapping("/{kbId}")
    @OperLog(title = "知识治理-策略", businessType = BusinessType.DELETE, recordParam = false)
    public Result<Void> delete(@PathVariable Long kbId) {
        Long userId = SecurityUtils.currentUserId();
        kbPermissionService.checkOwner(kbId, userId);
        policyService.deleteByKb(kbId, userId);
        return Result.success();
    }
}
