package com.knowledge.agent.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 任务详情视图（含步骤与产物）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class AgentTaskVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long kbId;
    private String goal;
    private String status;
    private String result;
    private Integer stepCount;
    private Integer tokenUsage;
    private String errorCode;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime finishedTime;

    private List<StepVo> steps;
    private List<ArtifactVo> artifacts;

    @Data
    public static class StepVo implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer stepIndex;
        private String agentType;
        private String status;
        private String outputSummary;
        private Long durationMs;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private String errorMsg;
    }

    @Data
    public static class ArtifactVo implements Serializable {
        private static final long serialVersionUID = 1L;
        private String artifactType;
        private String payload;
    }
}
