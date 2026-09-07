package com.knowledge.ai.calllog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 模型调用日志实体。
 * <p>审计型日志，不做软删（无 deleted 字段）；每次模型调用由 {@code AiCallLogger} 异步写入。
 * <p>租户隔离：{@code tenant_id} 在写入时显式设置，查询由 Mapper 手工注入条件（含平台视角）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("ai_call_log")
public class AiCallLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    /** 用户ID（系统调用如文档入库为空） */
    private Long userId;

    /** 用户名（冗余便于展示） */
    private String username;

    /** 业务模块 rag_chat/agent/workflow/prompt_test/embedding */
    private String module;

    /** 调用类型 CHAT/EMBEDDING/RERANK */
    private String bizType;

    private String modelName;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    /** 耗时（毫秒） */
    private Integer durationMs;

    /** 费用（元） */
    private BigDecimal cost;

    /** SUCCESS/FAILED */
    private String status;

    private String errorMsg;

    private LocalDateTime createTime;
}
