package com.knowledge.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录响应：返回 access token + refresh token（双 Token 机制）
 * <p>access token 短期有效（默认 2h），用于 API 鉴权；
 * refresh token 长期有效（默认 7d），仅用于静默刷新 access token，避免用户频繁重新登录。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** access token（短期，用于 API 鉴权） */
    private String token;

    /** refresh token（长期，仅用于刷新 access token） */
    private String refreshToken;

    /** 兼容旧构造：仅传 token 时 refreshToken 为 null */
    public LoginResult(String token) {
        this.token = token;
    }
}
