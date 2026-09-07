package com.knowledge.ai.chat.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天会话视图对象
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class ChatSessionVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long kbId;
    /** 关联知识库名称（普通对话 kbId=0 时为 null，前端按需展示） */
    private String kbName;
    private Long userId;
    private String title;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
