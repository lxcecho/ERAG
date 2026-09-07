package com.knowledge.ai.calllog.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.ai.calllog.dto.AiCallLogQuery;
import com.knowledge.ai.calllog.dto.AiCallStatsQuery;
import com.knowledge.ai.calllog.dto.AiCallStatsVo;
import com.knowledge.ai.calllog.entity.AiCallLog;
import com.knowledge.ai.calllog.service.AiCallLogService;
import com.knowledge.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 调用日志管理接口（运营管理）。
 * <p>提供调用日志分页查询与统计概览（调用次数/Token 消耗/费用排行）。
 *
 * @author: lxcechoo@gmail.com
 */
@Tag(name = "AI运营管理接口")
@RestController
@RequestMapping("/ai-call-log")
@RequiredArgsConstructor
public class AiCallLogController {

    private final AiCallLogService aiCallLogService;

    @Operation(summary = "调用日志分页查询")
    @GetMapping("/page")
    public Result<IPage<AiCallLog>> page(AiCallLogQuery query) {
        return Result.success(aiCallLogService.page(query));
    }

    @Operation(summary = "调用统计（概览+模型/用户/日期排行）")
    @GetMapping("/stats")
    public Result<AiCallStatsVo> stats(AiCallStatsQuery query) {
        return Result.success(aiCallLogService.stats(query));
    }
}
