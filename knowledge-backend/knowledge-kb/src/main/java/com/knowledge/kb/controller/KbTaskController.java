package com.knowledge.kb.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.common.annotation.BusinessType;
import com.knowledge.common.annotation.OperLog;
import com.knowledge.common.result.Result;
import com.knowledge.kb.dto.ParseTaskVo;
import com.knowledge.kb.dto.TaskDetailVo;
import com.knowledge.kb.dto.TaskQuery;
import com.knowledge.kb.service.KbParseTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档解析任务管理接口
 *
 * @author: lxcechoo@gmail.com
 */
@Tag(name = "解析任务接口")
@RestController
@RequestMapping("/kb/task")
@RequiredArgsConstructor
public class KbTaskController {

    private final KbParseTaskService kbParseTaskService;

    @Operation(summary = "解析任务分页查询（支持状态过滤）")
    @GetMapping
    public Result<IPage<ParseTaskVo>> page(TaskQuery query) {
        return Result.success(kbParseTaskService.page(query));
    }

    @Operation(summary = "解析任务详情")
    @GetMapping("/{id}")
    public Result<TaskDetailVo> detail(@PathVariable Long id) {
        return Result.success(kbParseTaskService.getDetail(id));
    }

    @Operation(summary = "重试失败任务")
    @PostMapping("/{id}/retry")
    @OperLog(title = "解析任务", businessType = BusinessType.OTHER)
    public Result<Void> retry(@PathVariable Long id) {
        kbParseTaskService.retry(id);
        return Result.success();
    }
}
