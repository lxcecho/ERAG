package com.knowledge.ai.calllog.dto;

import com.knowledge.kb.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 调用日志分页查询条件。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiCallLogQuery extends PageQuery {

    /** 业务模块 */
    private String module;

    /** 调用类型 CHAT/EMBEDDING */
    private String bizType;

    /** 模型名 */
    private String modelName;

    /** 状态 SUCCESS/FAILED */
    private String status;

    /** 用户ID */
    private Long userId;

    /** 起始时间（yyyy-MM-dd HH:mm:ss） */
    private String startTime;

    /** 结束时间（yyyy-MM-dd HH:mm:ss） */
    private String endTime;
}
