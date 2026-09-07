package com.knowledge.kb.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.annotation.BusinessType;
import com.knowledge.common.annotation.OperLog;
import com.knowledge.common.result.Result;
import com.knowledge.kb.dto.KbCreateRequest;
import com.knowledge.kb.dto.KbPageQuery;
import com.knowledge.kb.dto.KbUpdateRequest;
import com.knowledge.kb.entity.KnowledgeBase;
import com.knowledge.kb.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库管理接口
 * <p>创建 / 修改 / 删除 仅管理员；查询所有认证用户可用。
 *
 * @author: lxcechoo@gmail.com
 */
@Tag(name = "知识库管理接口")
@RestController
@RequestMapping("/kb/bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @Operation(summary = "创建知识库")
    @PostMapping
    @OperLog(title = "知识库管理", businessType = BusinessType.INSERT)
    public Result<Long> create(@RequestBody @Valid KbCreateRequest request) {
        return Result.success(knowledgeBaseService.create(request, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "更新知识库")
    @PutMapping("/{id}")
    @OperLog(title = "知识库管理", businessType = BusinessType.UPDATE)
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid KbUpdateRequest request) {
        knowledgeBaseService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "知识库详情")
    @GetMapping("/{id}")
    public Result<KnowledgeBase> detail(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.getById(id));
    }

    @Operation(summary = "知识库分页列表（仅返回当前用户参与的知识库）")
    @GetMapping
    public Result<IPage<KnowledgeBase>> page(KbPageQuery query) {
        return Result.success(knowledgeBaseService.page(query, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "删除知识库（仅 owner）")
    @DeleteMapping("/{id}")
    @OperLog(title = "知识库管理", businessType = BusinessType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.remove(id, SecurityUtils.currentUserId());
        return Result.success();
    }
}
