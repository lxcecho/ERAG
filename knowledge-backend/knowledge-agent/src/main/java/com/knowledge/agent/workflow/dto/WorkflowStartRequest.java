package com.knowledge.agent.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 启动流程请求：definitionId 与 code 二选一。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class WorkflowStartRequest {

    /** 流程定义ID（与 code 二选一） */
    private Long definitionId;

    /** 流程编码（预置流程用，如 policy_analysis；与 definitionId 二选一） */
    private String code;

    /** 知识库ID（检索类流程必填，需 viewer 权限） */
    private Long kbId;

    /** 业务键（外部关联，可空） */
    private String businessKey;

    @NotBlank(message = "流程目标(goal)不能为空")
    private String goal;
}
