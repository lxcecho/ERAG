package com.knowledge.agent.memory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 长期记忆实体（摘要 SUMMARY + 长期事实 LONG_TERM 共用单表，memory_type 区分）。
 * <p>对应表 agent_memory。向量记忆(VectorMemory) 不入库本表，独立存 Milvus agent_memory_vec 集合。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("agent_memory")
public class AgentMemory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long userId;

    /** 所属会话ID（SUMMARY 必填；LONG_TERM 可空） */
    private Long sessionId;

    /** SUMMARY / LONG_TERM */
    private String memoryType;

    private String content;

    /** 来源会话ID（LONG_TERM 记录从哪个会话抽取） */
    private Long sourceSessionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
