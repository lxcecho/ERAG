package com.knowledge.kb.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库成员视图（联查用户信息）
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class KbMemberVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long kbId;
    private Long userId;
    /** 角色 owner/editor/viewer */
    private String role;
    private String username;
    private String nickname;
    private LocalDateTime createTime;
}
