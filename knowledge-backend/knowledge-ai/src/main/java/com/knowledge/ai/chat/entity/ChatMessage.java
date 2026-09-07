package com.knowledge.ai.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天消息实体
 * <p>role=user 为用户提问，role=assistant 为 RAG 回答；sourcesJson 存检索来源 JSON。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属租户ID（冗余，便于租户级审计） */
    private Long tenantId;

    /** 会话ID */
    private Long sessionId;

    /** 消息角色 user/assistant */
    private String role;

    /** 消息内容 */
    private String content;

    /** 引用来源 JSON（RetrievalResult 列表序列化） */
    private String sourcesJson;

    /** 生成 token 数（预留） */
    private Integer tokens;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
