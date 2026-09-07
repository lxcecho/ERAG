package com.knowledge.ai.ops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 告警规则实体（可配置阈值）。
 * <p>{@code resource} 对齐 {@code AlertService.alert()} 的 source 命名（如 milvus:search），
 * 评估时零转换直接传入。有软删供 CRUD。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("alert_rule")
public class AlertRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private String name;

    /** 资源，对齐 AlertService.source：milvus:search / llm:chat / mq:parse */
    private String resource;

    /** 指标 calls / error_rate / latency_p95 / token_usage */
    private String metric;

    /** 比较符 GT/LT/GTE/LTE */
    private String operator;

    private BigDecimal threshold;

    /** 统计窗口（分钟） */
    private Integer windowMinutes;

    /** 触发级别 INFO/WARN/CRITICAL */
    private String level;

    /** 1启用 0停用 */
    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
