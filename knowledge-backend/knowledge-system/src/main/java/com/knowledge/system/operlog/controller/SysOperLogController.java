package com.knowledge.system.operlog.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.common.result.Result;
import com.knowledge.system.operlog.dto.SysOperLogQuery;
import com.knowledge.system.operlog.entity.SysOperLog;
import com.knowledge.system.operlog.service.SysOperLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志查询接口
 *
 * @author: lxcechoo@gmail.com
 */
@Tag(name = "操作日志接口")
@RestController
@RequestMapping("/system/oper-log")
@RequiredArgsConstructor
public class SysOperLogController {

    private final SysOperLogService sysOperLogService;

    @Operation(summary = "操作日志分页查询")
    @GetMapping
    public Result<IPage<SysOperLog>> page(SysOperLogQuery query) {
        return Result.success(sysOperLogService.page(query));
    }
}
