package com.knowledge.agent.custom.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 我的任务聚合 VO（Agent 任务 + 自定义 Agent 运行 统一条目）。
 * <p>taskType：AGENT=自主式 Agent 任务（agent_task）、CUSTOM_AGENT=自定义 Agent 运行（agent_run）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class MyTaskVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务来源类型：AGENT / CUSTOM_AGENT */
    private String taskType;

    /** 任务/运行记录 ID（agent_task.id 或 agent_run.id） */
    private Long taskId;

    /** 自定义 Agent 定义 ID（CUSTOM_AGENT 时） */
    private Long agentId;

    /** 自定义 Agent 名称（CUSTOM_AGENT 时） */
    private String agentName;

    /** 展示标题（goal / question） */
    private String title;

    /** 状态（CREATED/RUNNING/COMPLETED/FAILED/CANCELED 等） */
    private String status;

    /** token 消耗 */
    private Integer tokenUsage;

    /** 失败原因 */
    private String errorMsg;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 完成时间 */
    private LocalDateTime finishedTime;
}
