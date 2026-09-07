package com.knowledge.agent.memory.controller;

import com.knowledge.agent.memory.MemoryService;
import com.knowledge.agent.memory.dto.MemoryContext;
import com.knowledge.agent.memory.dto.MemoryEntryVo;
import com.knowledge.agent.memory.dto.MemoryRecall;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 长期记忆接口。
 * <p>
 * 提供手动触发摘要/事实抽取、当前用户记忆列表、记忆上下文预览、向量语义召回演示、记忆删除。
 * <p>
 * 风格对齐 {@code WorkflowController}：{@link Result} 统一返回 + Swagger 注解 + {@link SecurityUtils} 取当前用户。
 * 所有接口基于当前登录用户与租户上下文，不存在跨用户/跨租户越权访问。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Tag(name = "Agent长期记忆接口", description = "摘要/长期事实/向量召回/记忆管理")
@RestController
@RequestMapping("/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    /** 手动触发会话摘要生成 */
    @Operation(summary = "触发会话摘要生成", description = "用 LLM 压缩会话消息为摘要并持久化")
    @PostMapping("/sessions/{id}/summarize")
    public Result<String> summarize(@Parameter(description = "会话ID") @PathVariable Long id) {
        Long userId = SecurityUtils.currentUserId();
        Long tenantId = TenantContext.requiredTenantId();
        String summary = memoryService.summarize(id, userId, tenantId);
        return Result.success(summary);
    }

    /** 手动触发长期事实抽取 */
    @Operation(summary = "触发长期事实抽取", description = "从会话抽取用户长期事实并索引向量")
    @PostMapping("/sessions/{id}/extract")
    public Result<List<String>> extract(@Parameter(description = "会话ID") @PathVariable Long id) {
        Long userId = SecurityUtils.currentUserId();
        Long tenantId = TenantContext.requiredTenantId();
        List<String> facts = memoryService.extractFacts(id, userId, tenantId);
        return Result.success(facts);
    }

    /** 查询当前用户记忆列表（可选类型过滤 SUMMARY/LONG_TERM） */
    @Operation(summary = "查询当前用户记忆列表")
    @GetMapping("/me")
    public Result<List<MemoryEntryVo>> myMemories(
            @Parameter(description = "记忆类型过滤 SUMMARY/LONG_TERM，不传则全部")
            @RequestParam(required = false) String type) {
        Long userId = SecurityUtils.currentUserId();
        return Result.success(memoryService.listMemories(userId, type));
    }

    /** 预览装配的记忆上下文（演示/调试用） */
    @Operation(summary = "预览记忆上下文", description = "装配当前会话+用户的 4 类记忆，演示注入 Planner 的背景文本")
    @GetMapping("/sessions/{id}/context")
    public Result<MemoryContext> context(@Parameter(description = "会话ID") @PathVariable Long id,
                                         @Parameter(description = "当前查询/目标，用于向量召回")
                                         @RequestParam(required = false) String query) {
        Long userId = SecurityUtils.currentUserId();
        Long tenantId = TenantContext.requiredTenantId();
        return Result.success(memoryService.loadContext(id, userId, tenantId, query));
    }

    /** 向量语义召回演示 */
    @Operation(summary = "向量语义召回", description = "按查询语义召回当前用户的历史记忆")
    @GetMapping("/recalls")
    public Result<List<MemoryRecall>> recalls(
            @Parameter(description = "查询文本") @RequestParam String query,
            @Parameter(description = "召回条数，默认取配置") @RequestParam(required = false) Integer topK) {
        Long userId = SecurityUtils.currentUserId();
        int k = topK == null ? -1 : topK;
        return Result.success(memoryService.recall(query, userId, k));
    }

    /** 删除记忆条目（DB 逻辑删除 + 向量清理 best-effort） */
    @Operation(summary = "删除记忆条目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "记忆ID") @PathVariable Long id) {
        memoryService.deleteMemory(id);
        return Result.success();
    }
}
