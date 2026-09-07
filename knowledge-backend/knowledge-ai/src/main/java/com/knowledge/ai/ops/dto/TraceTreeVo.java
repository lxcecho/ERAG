package com.knowledge.ai.ops.dto;

import com.knowledge.ai.ops.entity.OpsTrace;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 链路追踪 span 树节点（GET /ops/traces/{traceId}）。
 * <p>由 {@link OpsTrace} 列表按 {@code parentSpanId} 构建为树，前端 el-tree 递归渲染。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class TraceTreeVo {

    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String spanName;
    private String spanType;
    private LocalDateTime startTime;
    private Integer durationMs;
    private String status;
    private String attributesJson;
    private List<TraceTreeVo> children = new ArrayList<>();
}
