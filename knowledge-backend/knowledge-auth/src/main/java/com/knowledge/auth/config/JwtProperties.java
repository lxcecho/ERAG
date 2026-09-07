package com.knowledge.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JWT 配置属性（读取 application.yml 中 jwt.* 配置）
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 签名密钥（HS256，需 >= 32 字节） */
    private String secret;

    /** access token 有效期（秒），默认 2 小时 */
    private long accessTokenExpire = 7200L;

    /** refresh token 有效期（秒），默认 7 天 */
    private long refreshTokenExpire = 604800L;

    /** 请求头名称 */
    private String header = "Authorization";

    /** token 前缀 */
    private String prefix = "Bearer ";

    /**
     * 允许从 query 参数读取 token 的路径白名单。
     * <p>SSE 端点使用 EventSource，浏览器无法设置自定义请求头，故需支持 query 参数传 token。
     */
    private List<String> allowQueryTokenPaths;
}
