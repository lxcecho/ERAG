package com.knowledge.agent.workflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 人工审批请求（HUMAN 节点恢复用）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class ApproveRequest {

    /** 是否通过：true 继续 / false 驳回（走 rejectNext 或终止） */
    @NotNull(message = "审批结果(approved)不能为空")
    private Boolean approved;

    /** 审批意见（同时作为 HUMAN 节点输入写入上下文） */
    private String comment;
}
