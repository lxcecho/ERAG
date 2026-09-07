package com.knowledge.agent.custom.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.agent.custom.dto.MyTaskVo;
import com.knowledge.agent.custom.service.MyTaskService;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的任务聚合接口（账户中心 Tab 用）。
 * <p>路径挂在 {@code /auth} 下与认证模块一致，实现在 agent 模块以同时访问
 * agent_task（自主式 Agent）与 agent_run（自定义 Agent）两张表。
 * <p>返回合并分页：taskType=AGENT 跳自主式任务详情、CUSTOM_AGENT 跳自定义运行详情。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Tag(name = "我的任务", description = "账户中心：Agent 任务 + 自定义 Agent 运行聚合")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class MyTaskController {

    private final MyTaskService myTaskService;

    @Operation(summary = "我的任务聚合分页（按创建时间倒序）")
    @GetMapping("/my-tasks")
    public Result<IPage<MyTaskVo>> myTasks(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") long pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long pageSize) {
        Long userId = SecurityUtils.currentUserId();
        Long tenantId = TenantContext.requiredTenantId();
        return Result.success(myTaskService.page(pageNum, pageSize, tenantId, userId));
    }
}
