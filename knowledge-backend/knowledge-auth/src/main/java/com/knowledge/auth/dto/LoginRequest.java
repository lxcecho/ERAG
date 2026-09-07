package com.knowledge.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class LoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户编码（多租户下必填，如 "demo"/"acme"；平台管理员用 "platform"） */
    private String tenantCode;

    /** 租户ID（数字方式，可选。若传则以 ID 为准，不再按编码查） */
    private Long tenantId;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
