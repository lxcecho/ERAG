package com.knowledge.auth.jwt;

import com.knowledge.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌提供者：生成、解析、校验
 * 使用 HS256 对称签名，无状态。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties props;
    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 生成 token（兼容旧签名，默认 tenantId=null，会在登录链路中覆盖） */
    public String generate(Long userId, String username) {
        return generate(userId, username, null);
    }

    /** 生成 access token，subject 为用户名，携带 userId + tenantId（多租户场景使用） */
    public String generate(Long userId, String username, Long tenantId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + props.getAccessTokenExpire() * 1000L);
        var builder = Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiry);
        if (tenantId != null) {
            builder.claim("tenantId", tenantId);
        }
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    /**
     * 生成 refresh token（长期有效，仅用于刷新 access token）。
     * <p>refresh token 携带 userId + tenantId + type=refresh，有效期由 jwt.refresh-token-expire 控制。
     * 与 access token 使用相同签名密钥，但通过 type claim 区分用途。
     */
    public String generateRefreshToken(Long userId, String username, Long tenantId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + props.getRefreshTokenExpire() * 1000L);
        var builder = Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry);
        if (tenantId != null) {
            builder.claim("tenantId", tenantId);
        }
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    /**
     * 校验 token 是否为 refresh token。
     * @return true 表示是合法的 refresh token
     */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = parse(token);
            return "refresh".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    /** 解析 token，返回 Claims（失败抛异常） */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 token 提取用户名 */
    public String getUsername(String token) {
        return parse(token).getSubject();
    }

    /** 从 token 提取 userId */
    public Long getUserId(String token) {
        Object val = parse(token).get("userId");
        return val == null ? null : Long.valueOf(val.toString());
    }

    /** 从 token 提取 tenantId（多租户模式关键 claim） */
    public Long getTenantId(String token) {
        Object val = parse(token).get("tenantId");
        return val == null ? null : Long.valueOf(val.toString());
    }

    /** 校验 token 是否有效（签名 + 过期时间） */
    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            return false;
        }
    }
}
