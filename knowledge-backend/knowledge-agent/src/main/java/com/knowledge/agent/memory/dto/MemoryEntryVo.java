package com.knowledge.agent.memory.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆条目 API 响应（SUMMARY/LONG_TERM 通用）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class MemoryEntryVo {

    private Long id;

    private Long userId;

    private Long sessionId;

    /** SUMMARY / LONG_TERM */
    private String memoryType;

    private String content;

    private Long sourceSessionId;

    private LocalDateTime createTime;
}
