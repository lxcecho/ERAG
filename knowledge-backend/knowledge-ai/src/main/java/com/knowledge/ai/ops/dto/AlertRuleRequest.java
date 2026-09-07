package com.knowledge.ai.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 告警规则 CRUD 请求体（POST/PUT /ops/alert-rules）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class AlertRuleRequest {

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    private String name;

    /** 资源，对齐 AlertService.source：milvus:search / es:search / mq:parse / llm:chat */
    @NotBlank(message = "资源不能为空")
    private String resource;

    /** 指标 calls / error_rate / latency_p95 / token_usage */
    @NotBlank(message = "指标不能为空")
    private String metric;

    /** 比较符 GT/LT/GTE/LTE */
    @NotBlank(message = "比较符不能为空")
    private String operator;

    /** 阈值 */
    @NotNull(message = "阈值不能为空")
    private BigDecimal threshold;

    /** 统计窗口（分钟），默认 5 */
    private Integer windowMinutes = 5;

    /** 触发级别 INFO/WARN/CRITICAL，默认 WARN */
    private String level = "WARN";

    /** 1启用 0停用，默认启用 */
    private Integer enabled = 1;
}
