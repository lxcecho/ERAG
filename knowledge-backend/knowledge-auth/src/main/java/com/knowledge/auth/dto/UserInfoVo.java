package com.knowledge.auth.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 当前用户信息（供前端用户状态与权限渲染）
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class UserInfoVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 所属租户ID（前端展示与后续请求 header 回填） */
    private Long tenantId;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    /** 角色 key 列表，如 ["admin"] */
    private List<String> roles;
    /** 权限标识列表，如 ["system:user:list"] */
    private List<String> permissions;
}
