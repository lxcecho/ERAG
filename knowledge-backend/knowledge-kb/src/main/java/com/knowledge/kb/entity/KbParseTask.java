package com.knowledge.kb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档解析任务实体
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("kb_parse_task")
public class KbParseTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属租户ID */
    private Long tenantId;

    /** 文档ID */
    private Long documentId;

    /** 知识库ID */
    private Long kbId;

    /** 任务状态：0待处理 1处理中 2成功 3失败 */
    private Integer status;

    /** 已重试次数（MQ 消费失败累计，达 maxRetries 后进死信队列） */
    private Integer retryCount;

    /** 最大重试次数（默认 3，与 spring.rabbitmq.listener.simple.retry.max-attempts 对齐） */
    private Integer maxRetries;

    /** 失败原因 */
    private String errorMsg;

    /** 开始处理时间 */
    private LocalDateTime startTime;

    /** 结束处理时间 */
    private LocalDateTime endTime;

    /** 创建人ID */
    private Long creatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
