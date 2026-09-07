package com.knowledge.agent.custom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 多步流程单步运行状态（SSE progress 推送用）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Schema(description = "多步流程步骤状态")
public class AgentStepRunVo {

    /** 步骤名称 */
    private String stepName;

    /** 产物 key（下一步引用） */
    private String outputKey;

    /** 状态：PENDING/RUNNING/SUCCESS/FAILED */
    private String status;

    /** 该步输出文本 */
    private String output;
}
