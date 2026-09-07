package com.knowledge.agent.custom.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.agent.custom.dto.AgentDefinitionRequest;
import com.knowledge.agent.custom.dto.AgentDefinitionVo;
import com.knowledge.agent.custom.dto.AgentRunVo;
import com.knowledge.agent.custom.dto.ChatStartRequest;
import com.knowledge.agent.custom.dto.UploadResultVo;
import com.knowledge.agent.custom.service.AgentDefinitionService;
import com.knowledge.agent.custom.service.AgentRunService;
import com.knowledge.agent.custom.service.LogUploadService;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.result.Result;
import com.knowledge.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 自定义 Agent 接口。
 * <p>
 * 提供定义管理（创建/编辑/发布/删除/分页/详情）、日志上传、单步流式对话（SSE）、
 * 多步异步执行（SSE 进度）与运行记录查询。
 * <p>
 * 权限：定义管理仅创建者本人；运行仅创建者可运行自己的定义；
 * 知识库模式检索前校验 KB viewer 权限，文档级权限由 {@code DocPermissionService} 强制过滤。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Tag(name = "自定义Agent接口", description = "自定义 Agent：定义管理/日志上传/对话/多步流程/运行记录")
@RestController
@RequestMapping("/custom-agent")
@RequiredArgsConstructor
public class CustomAgentController {

    private final AgentDefinitionService definitionService;
    private final AgentRunService runService;
    private final LogUploadService logUploadService;

    /* ==================== 定义管理 ==================== */

    @Operation(summary = "创建自定义 Agent 定义")
    @PostMapping
    public Result<Long> create(@RequestBody @Valid AgentDefinitionRequest req) {
        return Result.success(definitionService.create(req, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "编辑自定义 Agent 定义（PUBLISHED 修改后回落 DRAFT）")
    @PutMapping("/{id}")
    public Result<Long> edit(@PathVariable Long id, @RequestBody @Valid AgentDefinitionRequest req) {
        req.setId(id);
        return Result.success(definitionService.edit(req, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "发布定义")
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        definitionService.publish(id, SecurityUtils.currentUserId());
        return Result.success();
    }

    @Operation(summary = "删除定义（软删）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        definitionService.delete(id, SecurityUtils.currentUserId());
        return Result.success();
    }

    @Operation(summary = "分页查询定义（自己的全部 + 租户内已发布）")
    @GetMapping("/page")
    public Result<IPage<AgentDefinitionVo>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size) {
        return Result.success(definitionService.page(current, size, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "定义详情")
    @GetMapping("/{id}")
    public Result<AgentDefinitionVo> detail(@PathVariable Long id) {
        return Result.success(definitionService.detail(id, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "上传日志/文档文件（返回 fileRef 供运行使用）")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadResultVo> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(logUploadService.upload(file));
    }

    /* ==================== 运行 ==================== */

    @Operation(summary = "单步流式对话（SSE：status→token×N→done）")
    @PostMapping("/{id}/chat")
    @SentinelResource(value = "api:/custom-agent/chat", blockHandler = "chatBlockHandler")
    public SseEmitter chat(@PathVariable Long id, @RequestBody @Valid ChatStartRequest req) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        runService.startChat(id, req, SecurityUtils.currentUserId(),
                TenantContext.requiredTenantId(), emitter);
        return emitter;
    }

    @Operation(summary = "多步异步执行（立即返回 runId，SSE 订阅进度）")
    @PostMapping("/{id}/run")
    @SentinelResource(value = "api:/custom-agent/run", blockHandler = "runBlockHandler")
    public Result<Long> run(@PathVariable Long id, @RequestBody @Valid ChatStartRequest req) {
        return Result.success(runService.startRun(id, req, SecurityUtils.currentUserId(),
                TenantContext.requiredTenantId()));
    }

    @Operation(summary = "多步执行 SSE 进度流（progress→complete）")
    @GetMapping("/runs/{runId}/stream")
    public SseEmitter stream(@PathVariable Long runId) {
        AgentRunVo head = runService.detail(runId, SecurityUtils.currentUserId());
        return runService.streamRun(runId, head.getTenantId());
    }

    @Operation(summary = "分页查询当前用户运行记录")
    @GetMapping("/runs/page")
    public Result<IPage<AgentRunVo>> runsPage(
            @Parameter(description = "关联 Agent 定义ID（按 Agent 过滤历史）") @RequestParam(required = false) Long agentId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size) {
        return Result.success(runService.page(current, size, SecurityUtils.currentUserId(), agentId));
    }

    @Operation(summary = "运行记录详情")
    @GetMapping("/runs/{runId}")
    public Result<AgentRunVo> runDetail(@PathVariable Long runId) {
        return Result.success(runService.detail(runId, SecurityUtils.currentUserId()));
    }

    /* ==================== 限流降级 ==================== */

    public SseEmitter chatBlockHandler(Long id, ChatStartRequest req, BlockException ex) {
        log.warn("[限流] /custom-agent/chat type={}", ex.getClass().getSimpleName());
        SseEmitter emitter = new SseEmitter();
        try {
            emitter.send(SseEmitter.event().name("error").data("请求过于频繁，请稍后再试"));
        } catch (Exception ignored) {
        }
        emitter.complete();
        return emitter;
    }

    public Result<Long> runBlockHandler(Long id, ChatStartRequest req, BlockException ex) {
        log.warn("[限流] /custom-agent/run type={}", ex.getClass().getSimpleName());
        return Result.failed(ResultCode.RATE_LIMITED.getCode(), ResultCode.RATE_LIMITED.getMessage());
    }
}
