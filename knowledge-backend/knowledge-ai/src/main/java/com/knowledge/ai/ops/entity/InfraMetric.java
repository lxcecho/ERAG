package com.knowledge.ai.ops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础设施指标实体（Milvus/ES/MQ 调用埋点）。
 * <p>审计型日志，不做软删（无 deleted 字段），append-only；每次外部调用由 MetricsCollector 异步写入。
 * <p>租户隔离：{@code tenant_id} 写入时显式设置，查询由 Mapper 手工注入条件（同 ai_call_log 模式）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("infra_metric")
public class InfraMetric implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    /** 资源 milvus:search / es:search / mq:parse */
    private String resource;

    /** 动作 search / consume / store */
    private String action;

    /** 耗时（毫秒） */
    private Integer durationMs;

    /** 1成功 0失败 */
    private Integer success;

    private String errorMsg;

    /** 附加元数据 JSON */
    private String metadata;

    private LocalDateTime createTime;
}
