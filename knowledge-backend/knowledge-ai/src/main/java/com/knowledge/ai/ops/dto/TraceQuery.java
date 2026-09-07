package com.knowledge.ai.ops.dto;

import com.knowledge.kb.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 链路追踪查询条件。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TraceQuery extends PageQuery {

    /** traceId 精确过滤 */
    private String traceId;

    /** span 类型 ROOT/SEARCH/LLM/TOOL/MQ */
    private String spanType;

    /** 起始时间 yyyy-MM-dd HH:mm:ss */
    private String start;

    /** 结束时间 */
    private String end;
}
