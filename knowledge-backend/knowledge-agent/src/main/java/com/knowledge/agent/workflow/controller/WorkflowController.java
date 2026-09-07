package com.knowledge.agent.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.agent.workflow.dto.ApproveRequest;
import com.knowledge.agent.workflow.dto.WorkflowStartRequest;
import com.knowledge.agent.workflow.dto.WorkflowTaskVo;
import com.knowledge.agent.workflow.engine.WorkflowDefinitionService;
import com.knowledge.agent.workflow.engine.WorkflowExecutor;
import com.knowledge.agent.workflow.engine.WorkflowTaskManager;
import com.knowledge.agent.workflow.entity.WorkflowDefinition;
import com.knowledge.agent.workflow.entity.WorkflowNodeRun;
import com.knowledge.agent.workflow.entity.WorkflowTask;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import com.knowledge.common.result.Result;
import com.knowledge.kb.service.KbPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Workflow 可控流程接口。
 * <p>
 * 提供流程启动（异步）、详情、分页、人工审批恢复、失败重试、取消、流程定义查询。
 * <p>
 * 权限：启动检索类流程（含 kbId）前校验 KB viewer 权限；HUMAN 节点审批人 = 当前登录用户。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Tag(name = "Workflow可控流程接口", description = "声明式流程：启动/审批/重试/取消/详情")
@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowDefinitionService definitionService;
    private final WorkflowTaskManager taskManager;
    private final WorkflowExecutor executor;
    private final KbPermissionService kbPermissionService;
    private final com.knowledge.agent.workflow.config.WorkflowProperties props;

    /**
     * 启动流程（异步，立即返回任务ID）
     */
    @Operation(summary = "启动流程（异步）", description = "definitionId 与 code 二选一；含 kbId 时校验 viewer 权限")
    @PostMapping("/start")
    public Result<Long> start(@RequestBody @Valid WorkflowStartRequest req) {
        Long userId = SecurityUtils.currentUserId();
        Long tenantId = TenantContext.requiredTenantId();

        WorkflowDefinition def = resolveDefinition(req, tenantId);
        if (req.getKbId() != null) {
            kbPermissionService.checkViewer(req.getKbId(), userId);
        }

        WorkflowTask task = taskManager.create(tenantId, userId, def.getId(),
                req.getKbId(), req.getGoal(), req.getBusinessKey());

        executor.executeAsync(task.getId(), tenantId);
        log.info("[Workflow] 用户={} 启动任务={} 流程={} 目标={}",
                userId, task.getId(), def.getCode(), req.getGoal());
        return Result.success(task.getId());
    }

    /**
     * 任务详情（含节点执行记录）
     */
    @Operation(summary = "流程任务详情（含节点执行记录）")
    @GetMapping("/{id}")
    public Result<WorkflowTaskVo> detail(@Parameter(description = "任务ID") @PathVariable Long id) {
        WorkflowTask task = taskManager.getById(id);
        WorkflowDefinition def = definitionService.getById(task.getDefinitionId());
        WorkflowTaskVo vo = toVo(task);
        vo.setDefinitionCode(def.getCode());
        vo.setNodeRuns(toNodeRunVos(taskManager.listNodeRuns(id)));
        return Result.success(vo);
    }

    /**
     * 分页查询当前用户任务列表
     */
    @Operation(summary = "分页查询当前用户流程任务")
    @GetMapping("/page")
    public Result<IPage<WorkflowTaskVo>> page(
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "状态筛选（CREATED/RUNNING/WAITING_HUMAN/COMPLETED/FAILED/CANCELED）")
            @RequestParam(required = false) String status) {
        Long userId = SecurityUtils.currentUserId();
        Long tenantId = TenantContext.requiredTenantId();

        Page<WorkflowTask> page = new Page<>(current, size);
        IPage<WorkflowTask> taskPage = taskManager.pageUserTasks(page, tenantId, userId, status);
        return Result.success(taskPage.convert(this::toVo));
    }

    /**
     * 人工审批（HUMAN 节点恢复）
     */
    @Operation(summary = "人工审批（恢复 WAITING_HUMAN 任务）",
            description = "approved=true 继续后续节点；false 驳回走 rejectNext 或终止")
    @PostMapping("/{id}/approve")
    public Result<Long> approve(@PathVariable Long id, @RequestBody @Valid ApproveRequest req) {
        WorkflowTask task = taskManager.getById(id);
        if (!"WAITING_HUMAN".equals(task.getStatus())) {
            throw new BizException("任务非等待审批态: status=" + task.getStatus());
        }
        Long approverUserId = SecurityUtils.currentUserId();
        executor.resumeAsync(id, task.getTenantId(),
                req.getApproved(), approverUserId, req.getComment());
        log.info("[Workflow] 任务={} 审批人={} 通过={}", id, approverUserId, req.getApproved());
        return Result.success(id);
    }

    /**
     * 重试失败节点
     */
    @Operation(summary = "重试失败节点", description = "仅 FAILED 任务可重试，从最近失败节点重新执行")
    @PostMapping("/{id}/retry")
    public Result<Long> retry(@PathVariable Long id) {
        WorkflowTask task = taskManager.getById(id);
        if (!"FAILED".equals(task.getStatus())) {
            throw new BizException("仅 FAILED 任务可重试: status=" + task.getStatus());
        }
        executor.retryAsync(id, task.getTenantId());
        log.info("[Workflow] 任务={} 用户触发重试", id);
        return Result.success(id);
    }

    /**
     * 取消流程
     */
    @Operation(summary = "取消流程")
    @PostMapping("/{id}/cancel")
    public Result<Long> cancel(@PathVariable Long id,
                               @RequestParam(required = false) String reason) {
        executor.cancel(id, reason != null ? reason : "用户取消");
        return Result.success(id);
    }

    /**
     * 流程定义列表（可选当前租户 + 系统预置）
     */
    @Operation(summary = "流程定义列表")
    @GetMapping("/definitions")
    public Result<List<WorkflowDefinition>> definitions() {
        Long tenantId = TenantContext.requiredTenantId();
        List<WorkflowDefinition> tenantDefs = definitionService.listByTenant(tenantId);
        // 合并系统预置（tenantId=0）
        List<WorkflowDefinition> systemDefs = definitionService.listByTenant(props.getSystemTenantId());
        tenantDefs.addAll(systemDefs);
        return Result.success(tenantDefs);
    }

    // ==================== VO 转换 ====================

    private WorkflowDefinition resolveDefinition(WorkflowStartRequest req, Long tenantId) {
        if (req.getDefinitionId() != null) {
            return definitionService.getById(req.getDefinitionId());
        }
        if (req.getCode() != null && !req.getCode().isBlank()) {
            // code 优先查当前租户，未命中则查系统预置
            WorkflowDefinition def = definitionService.findEnabledByCode(tenantId, req.getCode());
            if (def == null) {
                def = definitionService.findEnabledByCode(props.getSystemTenantId(), req.getCode());
            }
            if (def == null) {
                throw new BizException("未找到启用的流程定义: code=" + req.getCode());
            }
            return def;
        }
        throw new BizException("definitionId 与 code 必须二选一");
    }

    private WorkflowTaskVo toVo(WorkflowTask task) {
        WorkflowTaskVo vo = new WorkflowTaskVo();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }

    private List<WorkflowTaskVo.NodeRunVo> toNodeRunVos(List<WorkflowNodeRun> runs) {
        return runs.stream().map(r -> {
            WorkflowTaskVo.NodeRunVo vo = new WorkflowTaskVo.NodeRunVo();
            BeanUtils.copyProperties(r, vo);
            return vo;
        }).toList();
    }
}
