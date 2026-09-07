package com.knowledge.agent.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Workflow 流程任务实体（一次流程执行 = 1 行，运行实例）。
 * <p>状态保存核心：{@link #contextJson} 持久化节点间变量，{@link #currentNode} 记录断点，
 * 进程崩溃/重启后可从断点恢复执行。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("workflow_task")
public class WorkflowTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long userId;

    private Long definitionId;

    /** 知识库ID（检索范围，可空——非检索类流程不需要） */
    private Long kbId;

    /** 业务键（外部系统关联，可空） */
    private String businessKey;

    /** 流程目标/输入主题 */
    private String goal;

    /** CREATED/RUNNING/WAITING_HUMAN/COMPLETED/FAILED/CANCELED */
    private String status;

    /** 当前节点ID（断点/恢复点） */
    private String currentNode;

    /** 上下文变量 JSON（节点产物累计） */
    private String contextJson;

    /** 流程最终结果（END 节点产物） */
    private String result;

    private Integer nodeCount;

    private Integer retryCount;

    private Integer tokenUsage;

    private String errorCode;

    private String errorMsg;

    private LocalDateTime finishedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
