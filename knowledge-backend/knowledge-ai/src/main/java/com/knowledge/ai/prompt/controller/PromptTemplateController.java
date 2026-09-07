package com.knowledge.ai.prompt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.ai.prompt.dto.PromptTemplateQuery;
import com.knowledge.ai.prompt.dto.PromptTemplateRequest;
import com.knowledge.ai.prompt.dto.PromptTemplateVO;
import com.knowledge.ai.prompt.dto.PromptTestRequest;
import com.knowledge.ai.prompt.dto.PromptTestResult;
import com.knowledge.ai.prompt.service.PromptTemplateService;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.annotation.BusinessType;
import com.knowledge.common.annotation.OperLog;
import com.knowledge.common.result.Result;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Prompt 模板管理接口（版本化）。
 * <p>提供模板分页、版本列表、创建、编辑、发布、回滚、删除、测试能力。
 *
 * @author: lxcechoo@gmail.com
 */
@Tag(name = "Prompt模板管理接口")
@RestController
@RequestMapping("/prompt")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @Operation(summary = "模板分页查询（每个 code 最新版本）")
    @GetMapping
    public Result<IPage<PromptTemplateVO>> page(PromptTemplateQuery query) {
        return Result.success(promptTemplateService.page(query));
    }

    @Operation(summary = "版本详情")
    @GetMapping("/{id}")
    public Result<PromptTemplateVO> detail(@PathVariable Long id) {
        return Result.success(promptTemplateService.detail(id));
    }

    @Operation(summary = "某 promptCode 的全部版本列表")
    @GetMapping("/versions/{promptCode}")
    public Result<List<PromptTemplateVO>> versions(@PathVariable String promptCode) {
        return Result.success(promptTemplateService.listVersions(promptCode));
    }

    @Operation(summary = "创建新 Prompt（新 code，v1 草稿）")
    @PostMapping
    @OperLog(title = "Prompt模板", businessType = BusinessType.INSERT)
    public Result<Long> create(@RequestBody @Valid PromptTemplateRequest request) {
        return Result.success(promptTemplateService.create(request, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "编辑模板（DRAFT原地更新；PUBLISHED/ARCHIVED派生新版本）")
    @PutMapping
    @OperLog(title = "Prompt模板", businessType = BusinessType.UPDATE)
    public Result<Long> edit(@RequestBody @Valid PromptTemplateRequest request) {
        return Result.success(promptTemplateService.edit(request, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "发布版本（同 code 同租户旧发布版归档）")
    @PutMapping("/{id}/publish")
    @OperLog(title = "Prompt模板", businessType = BusinessType.UPDATE)
    public Result<Void> publish(@PathVariable Long id) {
        promptTemplateService.publish(id);
        return Result.success();
    }

    @Operation(summary = "回滚至历史版本（派生新发布版本）")
    @PutMapping("/{id}/rollback")
    @OperLog(title = "Prompt模板", businessType = BusinessType.UPDATE)
    public Result<Void> rollback(@PathVariable Long id) {
        promptTemplateService.rollback(id, SecurityUtils.currentUserId());
        return Result.success();
    }

    @Operation(summary = "删除版本（软删）")
    @DeleteMapping("/{id}")
    @OperLog(title = "Prompt模板", businessType = BusinessType.DELETE)
    public Result<Void> remove(@PathVariable Long id) {
        promptTemplateService.remove(id);
        return Result.success();
    }

    @Operation(summary = "测试模板（渲染变量 + 调用 LLM 预览）")
    @PostMapping("/test")
    public Result<PromptTestResult> test(@RequestBody @Valid PromptTestRequest request) {
        return Result.success(promptTemplateService.test(request));
    }
}
