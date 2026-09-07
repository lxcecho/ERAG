package com.knowledge.agent.workflow.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程任务视图（详情/列表用，含节点执行记录）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class WorkflowTaskVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long userId;
    private Long definitionId;
    private String definitionCode;
    private Long kbId;
    private String businessKey;
    private String goal;
    private String status;
    private String currentNode;
    private String result;
    private Integer nodeCount;
    private Integer retryCount;
    private Integer tokenUsage;
    private String errorCode;
    private String errorMsg;
    private LocalDateTime finishedTime;
    private LocalDateTime createTime;

    /** 节点执行记录（列表接口为空，详情接口填充） */
    private List<NodeRunVo> nodeRuns;

    @Data
    public static class NodeRunVo implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private String nodeId;
        private String nodeName;
        private String nodeType;
        private Integer runIndex;
        private Integer attempt;
        private String status;
        private String inputJson;
        private String outputJson;
        private Integer approved;
        private Long approverUserId;
        private String approvalComment;
        private Integer tokenUsage;
        private Long durationMs;
        private String errorMsg;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
    }
}
