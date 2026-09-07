package com.knowledge.agent.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.agent.dto.AgentStartRequest;
import com.knowledge.agent.dto.AgentTaskVo;
import com.knowledge.agent.engine.AgentExecutor;
import com.knowledge.agent.engine.AgentStatus;
import com.knowledge.agent.engine.AgentTaskManager;
import com.knowledge.agent.entity.AgentArtifact;
import com.knowledge.agent.entity.AgentStep;
import com.knowledge.agent.entity.AgentTask;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.result.Result;
import com.knowledge.common.result.ResultCode;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Agent 工作流接口。
 * <p>
 * 提供任务启动（异步）、详情查询、分页列表、SSE 进度流。
 * <p>
 * 权限说明：启动任务前校验 KB viewer 权限（{@link KbPermissionService#checkViewer}），
 * 检索时的文档级权限由 {@code KnowledgeSearchTool} 内部 {@code DocPermissionService} 强制过滤，Agent 无法越权。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Tag(name = "Agent工作流接口", description = "任务型 Agent：启动/详情/分页/SSE 进度")
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentTaskManager taskManager;
    private final AgentExecutor executor;
    private final KbPermissionService kbPermissionService;

    /**
     * 启动 Agent 任务（异步执行，立即返回任务ID）。
     * <p>POST /agent/start
     *
     * @param req 任务请求（kbId + goal）
     * @return 任务ID
     */
    @Operation(summary = "启动 Agent 任务（异步）", description = "校验 KB viewer 权限后创建任务并异步执行，立即返回任务ID")
    @PostMapping("/start")
    @SentinelResource(value = "api:/agent/start", blockHandler = "startBlockHandler")
    public Result<Long> start(@RequestBody @Valid AgentStartRequest req) {
        Long userId = SecurityUtils.currentUserId();
        Long tenantId = TenantContext.requiredTenantId();

        // KB 级权限门禁：无 viewer 权限则抛 BizException（全局处理为 403），防止越权启动
        kbPermissionService.checkViewer(req.getKbId(), userId);

        AgentTask task = taskManager.create(
                tenantId, userId, req.getKbId(), req.getGoal(), req.getSessionId());

        // 异步执行：立即返回 taskId，后台跑工作流（显式传播租户上下文）
        executor.executeAsync(task.getId(), tenantId);

        log.info("[Agent] 用户={} 启动任务={} 目标={}", userId, task.getId(), req.getGoal());
        return Result.success(task.getId());
    }

    /** Agent 任务启动限流：返回 4290 限流码（Agent 任务资源消耗大，需防并发滥用） */
    public Result<Long> startBlockHandler(AgentStartRequest req, BlockException ex) {
        log.warn("[限流] /agent/start 资源=api:/agent/start type={}", ex.getClass().getSimpleName());
        return Result.failed(ResultCode.RATE_LIMITED.getCode(), ResultCode.RATE_LIMITED.getMessage());
    }

    /**
     * 查询任务详情（含步骤与产物）。
     * <p>GET /agent/{id}
     */
    @Operation(summary = "任务详情（含步骤与产物）")
    @GetMapping("/{id}")
    public Result<AgentTaskVo> detail(@Parameter(description = "任务ID") @PathVariable Long id) {
        AgentTask task = taskManager.getById(id);
        return Result.success(toVo(task));
    }

    /**
     * 分页查询当前用户的任务列表。
     * <p>GET /agent/page?current=1&size=10
     */
    @Operation(summary = "分页查询当前用户任务列表")
    @GetMapping("/page")
    public Result<IPage<AgentTaskVo>> page(
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size) {
        Long userId = SecurityUtils.currentUserId();
        Long tenantId = TenantContext.requiredTenantId();

        Page<AgentTask> page = new Page<>(current, size);
        IPage<AgentTask> taskPage = taskManager.pageUserTasks(page, tenantId, userId);

        IPage<AgentTaskVo> voPage = taskPage.convert(this::toVoLite);
        return Result.success(voPage);
    }

    /**
     * SSE 流式推送任务进度（前端 EventSource 订阅）。
     * <p>GET /agent/{id}/stream
     * <p>事件：progress（步骤进度）→ complete（终态结果）。
     * <p>注意：EventSource 无法设置自定义请求头，token 需通过 query 参数传递（见 jwt.allow-query-token-paths）。
     */
    @Operation(summary = "SSE 流式推送任务进度",
            description = "前端 EventSource 订阅，每 1.5s 推送一次 progress 事件；任务进入终态后推送 complete 事件并关闭连接")
    @GetMapping("/{id}/stream")
    public SseEmitter stream(@Parameter(description = "任务ID") @PathVariable Long id) {
        // 超时设为 5 分钟（覆盖最长任务）
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        // 预取任务获取租户上下文（SSE watcher 线程需显式传播，避免 MyBatis-Plus 租户拦截器漏过滤）
        AgentTask head = taskManager.getById(id);
        Long tenantId = head.getTenantId();

        Thread watcher = new Thread(() -> {
            // 显式传播租户上下文到 SSE 线程（TTL 不会自动继承裸 Thread）
            TenantContext.setTenantId(tenantId);
            try {
                while (true) {
                    AgentTask task = taskManager.getById(id);
                    emitter.send(SseEmitter.event()
                            .name("progress")
                            .data(toVo(task)));

                    AgentStatus status = AgentStatus.valueOf(task.getStatus());
                    if (status.isTerminal()) {
                        emitter.send(SseEmitter.event()
                                .name("complete")
                                .data(toVo(task)));
                        emitter.complete();
                        return;
                    }
                    Thread.sleep(1500L);
                }
            } catch (Exception e) {
                log.warn("[Agent] SSE 流异常 task={}: {}", id, e.getMessage());
                emitter.completeWithError(e);
            } finally {
                TenantContext.clear();
            }
        }, "agent-sse-" + id);
        watcher.setDaemon(true);
        watcher.start();

        return emitter;
    }

    // ==================== VO 转换 ====================

    private AgentTaskVo toVo(AgentTask task) {
        AgentTaskVo vo = new AgentTaskVo();
        BeanUtils.copyProperties(task, vo);
        vo.setSteps(toStepVos(taskManager.listSteps(task.getId())));
        vo.setArtifacts(toArtifactVos(taskManager.listArtifacts(task.getId())));
        return vo;
    }

    /** 列表用轻量 VO（不含步骤/产物，避免 N+1） */
    private AgentTaskVo toVoLite(AgentTask task) {
        AgentTaskVo vo = new AgentTaskVo();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }

    private List<AgentTaskVo.StepVo> toStepVos(List<AgentStep> steps) {
        return steps.stream().map(s -> {
            AgentTaskVo.StepVo vo = new AgentTaskVo.StepVo();
            BeanUtils.copyProperties(s, vo);
            return vo;
        }).toList();
    }

    private List<AgentTaskVo.ArtifactVo> toArtifactVos(List<AgentArtifact> artifacts) {
        return artifacts.stream().map(a -> {
            AgentTaskVo.ArtifactVo vo = new AgentTaskVo.ArtifactVo();
            BeanUtils.copyProperties(a, vo);
            return vo;
        }).toList();
    }
}
