package com.knowledge.kb.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.annotation.BusinessType;
import com.knowledge.common.annotation.OperLog;
import com.knowledge.common.result.Result;
import com.knowledge.kb.dto.KbMemberAddRequest;
import com.knowledge.kb.dto.KbMemberVo;
import com.knowledge.kb.service.KbMemberService;
import com.knowledge.kb.service.KbPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库成员管理接口（仅 owner 可操作）
 *
 * @author: lxcechoo@gmail.com
 */
@Tag(name = "知识库成员管理接口")
@RestController
@RequestMapping("/kb/bases/{kbId}/members")
@RequiredArgsConstructor
public class KbMemberController {

    private final KbMemberService kbMemberService;
    private final KbPermissionService kbPermissionService;

    @Operation(summary = "成员分页列表")
    @GetMapping
    public Result<IPage<KbMemberVo>> page(@PathVariable Long kbId,
                                           @RequestParam(defaultValue = "1") Integer pageNo,
                                           @RequestParam(defaultValue = "10") Integer pageSize) {
        kbPermissionService.checkOwner(kbId, SecurityUtils.currentUserId());
        return Result.success(kbMemberService.page(kbId, pageNo, pageSize));
    }

    @Operation(summary = "添加成员")
    @PostMapping
    @OperLog(title = "知识库成员", businessType = BusinessType.INSERT)
    public Result<Void> add(@PathVariable Long kbId, @RequestBody @Valid KbMemberAddRequest request) {
        kbPermissionService.checkOwner(kbId, SecurityUtils.currentUserId());
        kbMemberService.addMember(kbId, request.getUserId(), request.getRole());
        return Result.success();
    }

    @Operation(summary = "修改成员角色")
    @PutMapping("/{memberId}")
    @OperLog(title = "知识库成员", businessType = BusinessType.UPDATE)
    public Result<Void> updateRole(@PathVariable Long kbId, @PathVariable Long memberId,
                                   @RequestParam String role) {
        kbPermissionService.checkOwner(kbId, SecurityUtils.currentUserId());
        kbMemberService.updateRole(kbId, memberId, role);
        return Result.success();
    }

    @Operation(summary = "移除成员")
    @DeleteMapping("/{memberId}")
    @OperLog(title = "知识库成员", businessType = BusinessType.DELETE)
    public Result<Void> remove(@PathVariable Long kbId, @PathVariable Long memberId) {
        kbPermissionService.checkOwner(kbId, SecurityUtils.currentUserId());
        kbMemberService.removeMember(kbId, memberId);
        return Result.success();
    }
}
