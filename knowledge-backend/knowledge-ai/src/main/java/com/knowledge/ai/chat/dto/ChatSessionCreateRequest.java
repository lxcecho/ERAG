package com.knowledge.ai.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 聊天会话创建请求
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class ChatSessionCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "知识库ID不能为空")
    private Long kbId;

    @Size(max = 128, message = "会话标题最长128个字符")
    private String title;
}
