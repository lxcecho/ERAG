package com.knowledge.ai.chat.dto;

import com.knowledge.ai.dto.RetrievalResult;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天消息视图对象
 * <p>sourcesJson 反序列化为 sources 列表，便于前端直接渲染引用来源。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class ChatMessageVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private List<RetrievalResult> sources;
    private LocalDateTime createTime;
}
