package com.knowledge.agent.ops.controller;

import com.knowledge.ai.ops.alert.AlertRuleService;
import com.knowledge.ai.ops.dto.AlertRuleRequest;
import com.knowledge.ai.ops.entity.AlertRule;
import com.knowledge.common.annotation.OperLog;
import com.knowledge.common.annotation.BusinessType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 告警规则管理接口（@RequestMapping /ops/alert-rules）。
 * <p>写操作加 {@link OperLog} 审计；启停走 PUT /{id}/enabled。
 *
 * @author: lxcechoo@gmail.com
 */
@Tag(name = "告警规则管理接口")
@RestController
@RequestMapping("/ops/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    @Operation(summary = "规则列表")
    @GetMapping
    public Result<List<AlertRule>> list() {
        return Result.success(alertRuleService.list());
    }

    @Operation(summary = "新建规则")
    @OperLog(title = "告警规则", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<AlertRule> create(@Valid @RequestBody AlertRuleRequest req) {
        return Result.success(alertRuleService.create(req));
    }

    @Operation(summary = "更新规则")
    @OperLog(title = "告警规则", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public Result<AlertRule> update(@PathVariable Long id, @Valid @RequestBody AlertRuleRequest req) {
        return Result.success(alertRuleService.update(id, req));
    }

    @Operation(summary = "删除规则")
    @OperLog(title = "告警规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        alertRuleService.delete(id);
        return Result.success();
    }

    @Operation(summary = "启停切换")
    @OperLog(title = "告警规则", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/enabled")
    public Result<Void> toggleEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        alertRuleService.toggleEnabled(id, enabled);
        return Result.success();
    }
}
