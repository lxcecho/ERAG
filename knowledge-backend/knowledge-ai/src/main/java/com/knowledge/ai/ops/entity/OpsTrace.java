package com.knowledge.ai.ops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分布式追踪 span 实体。
 * <p>一次请求 = 1 个 trace_id + N 个 span（父子树）。审计型日志，不做软删。
 * <p>ROOT span 的 parent_span_id 为空，trace_id 与 span_id 同值（新链路）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("ops_trace")
public class OpsTrace implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    /** 追踪ID（同次请求一致） */
    private String traceId;

    /** 当前 span ID */
    private String spanId;

    /** 父 span ID（ROOT 为空） */
    private String parentSpanId;

    /** span 名 rag.ask / milvus.search / llm.chat */
    private String spanName;

    /** ROOT/SEARCH/LLM/TOOL/MQ */
    private String spanType;

    private LocalDateTime startTime;

    private Integer durationMs;

    /** OK/ERROR */
    private String status;

    /** 属性 JSON（kbId/topK/tokenUsage 等） */
    private String attributesJson;

    private LocalDateTime createTime;
}
