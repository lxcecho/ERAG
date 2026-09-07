package com.knowledge.ai.controller;

import com.knowledge.ai.dto.DashboardStatsVo;
import com.knowledge.ai.service.DashboardStatsService;
import com.knowledge.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台统计接口：知识库数 / 文档数 / 切片数 / 今日对话数。
 *
 * @author: lxcechoo@gmail.com
 */
@Tag(name = "工作台统计接口")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardStatsService statsService;

    @Operation(summary = "工作台四项统计")
    @GetMapping("/stats")
    public Result<DashboardStatsVo> stats() {
        return Result.success(statsService.stats());
    }
}
