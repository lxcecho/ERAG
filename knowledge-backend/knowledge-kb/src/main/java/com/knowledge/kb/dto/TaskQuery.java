package com.knowledge.kb.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 解析任务分页查询条件（支持状态过滤）
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskQuery extends PageQuery {

    /** 知识库ID（可选） */
    private Long kbId;

    /** 任务状态 0待处理 1处理中 2成功 3失败（可选） */
    private Integer status;
}
