package com.knowledge.kb.governance.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档有效期设置请求（生效时间 / 过期时间）。
 * <p>effectiveFrom 为 null 表示立即生效；expireAt 为 null 表示永久有效。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class ValidityRequest {

    /** 生效时间（NULL=立即生效） */
    private LocalDateTime effectiveFrom;

    /** 过期时间（NULL=永久有效） */
    private LocalDateTime expireAt;
}
