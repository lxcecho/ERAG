package com.knowledge.auth.service.impl;

import com.knowledge.auth.config.JwtProperties;
import com.knowledge.auth.dto.LoginRequest;
import com.knowledge.auth.dto.LoginResult;
import com.knowledge.auth.dto.PasswordRequest;
import com.knowledge.auth.dto.ProfileRequest;
import com.knowledge.auth.dto.UserInfoVo;
import com.knowledge.auth.jwt.JwtTokenProvider;
import com.knowledge.auth.security.SecurityUserDetails;
import com.knowledge.auth.service.AuthService;
import com.knowledge.auth.service.AvatarStorageService;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import com.knowledge.common.tenant.TenantProperties;
import com.knowledge.system.entity.User;
import com.knowledge.system.service.SysTenantService;
import com.knowledge.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Redis key 前缀：已吊销的 refresh token */
    private static final String REFRESH_TOKEN_REVOKED_PREFIX = "auth:refresh:revoked:";
    /** Redis key 前缀：refresh token 与 userId 的映射（用于旋转检测） */
    private static final String REFRESH_TOKEN_MAPPING_PREFIX = "auth:refresh:map:";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProps;
    private final UserService userService;
    private final SysTenantService sysTenantService;
    private final TenantProperties tenantProps;
    private final PasswordEncoder passwordEncoder;
    private final AvatarStorageService avatarStorageService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public LoginResult login(LoginRequest request) {
        // 多租户登录关键步骤：
        // 1) 先解析出 tenantId（按 request.tenantId / request.tenantCode / defaultTenantCode 顺序），
        //    并注入 TenantContext；
        // 2) 此时 AuthenticationManager → UserDetailsService.loadUserByUsername → userService.getByUsername
        //    → 内部根据 TenantContext.getTenantId() 走 (tenantId, username) 精准命中唯一键 uk_tenant_username；
        // 3) 签发 JWT 时把 tenantId 写入 claim，后续请求直接从 JWT 还原上下文，不再查 DB。
        Long tenantId = sysTenantService.resolveTenantId(
                request.getTenantId(), request.getTenantCode(), tenantProps.getDefaultTenantCode());
        TenantContext.setTenantId(tenantId);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            SecurityUserDetails details = (SecurityUserDetails) authentication.getPrincipal();
            // 兜底：若账号实际 tenantId 与登录解析出的 tenantId 不一致（例如用户手动乱填 tenantCode），
            // 直接拒绝登录，防止跨租户撞库成功
            if (details.getTenantId() != null && !details.getTenantId().equals(tenantId)) {
                throw new BizException(401, "该账号不属于当前租户");
            }
            Long jwtTid = details.getTenantId() != null ? details.getTenantId() : tenantId;
            String token = jwtTokenProvider.generate(details.getUserId(), details.getUsername(), jwtTid);
            String refreshToken = jwtTokenProvider.generateRefreshToken(details.getUserId(), details.getUsername(), jwtTid);

            // 存储 refresh token hash 到 Redis（用于旋转检测）
            String mappingKey = REFRESH_TOKEN_MAPPING_PREFIX + details.getUserId();
            String tokenHash = Integer.toHexString(refreshToken.hashCode());
            redisTemplate.opsForValue().set(mappingKey, tokenHash,
                    jwtProps.getRefreshTokenExpire(), TimeUnit.SECONDS);

            return new LoginResult(token, refreshToken);
        } finally {
            // 登录接口本身是 ignore-path，Filter 层不会 set TenantContext，自然也不会 clear，
            // 所以这里手动清理，避免在 Filter 链路外漏出 tid（极端情况：Spring Security 抛异常绕过 Filter.finally）
            TenantContext.clear();
        }
    }

    @Override
    public UserInfoVo getUserInfo() {
        SecurityUserDetails details = currentUser();
        UserInfoVo vo = new UserInfoVo();
        vo.setId(details.getUserId());
        vo.setTenantId(details.getTenantId());
        vo.setUsername(details.getUsername());
        vo.setRoles(details.getRoles());
        vo.setPermissions(details.getPermissions());

        // 补充昵称、头像（权限信息已在 SecurityContext，昵称头像从 DB 取最新）
        User user = userService.getById(details.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setEmail(user.getEmail());
            vo.setPhone(user.getPhone());
        }
        return vo;
    }

    @Override
    public UserInfoVo updateProfile(ProfileRequest request) {
        SecurityUserDetails details = currentUser();
        User user = userService.getById(details.getUserId());
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        String oldAvatar = user.getAvatar();
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        userService.updateById(user);
        // 头像被替换时清理旧文件（best-effort）
        if (request.getAvatar() != null && !request.getAvatar().equals(oldAvatar)) {
            avatarStorageService.deleteQuietly(oldAvatar);
        }
        log.info("[账户] 用户={} 更新资料 nickname={} email={} phone={}",
                details.getUserId(), user.getNickname(), user.getEmail(), user.getPhone());
        return toUserInfoVo(user, details);
    }

    @Override
    public void changePassword(PasswordRequest request) {
        SecurityUserDetails details = currentUser();
        User user = userService.getById(details.getUserId());
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException("旧密码不正确");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BizException("新密码不能与旧密码相同");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userService.updateById(user);
        log.info("[账户] 用户={} 修改密码成功", details.getUserId());
    }

    @Override
    public String updateAvatar(MultipartFile file) {
        SecurityUserDetails details = currentUser();
        String avatarUrl = avatarStorageService.upload(file);
        User user = userService.getById(details.getUserId());
        if (user != null) {
            String oldAvatar = user.getAvatar();
            user.setAvatar(avatarUrl);
            userService.updateById(user);
            avatarStorageService.deleteQuietly(oldAvatar);
        }
        log.info("[账户] 用户={} 上传头像 {}", details.getUserId(), avatarUrl);
        return avatarUrl;
    }

    /** 组装 UserInfoVo（补齐角色/权限） */
    private UserInfoVo toUserInfoVo(User user, SecurityUserDetails details) {
        UserInfoVo vo = new UserInfoVo();
        vo.setId(user.getId());
        vo.setTenantId(user.getTenantId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setRoles(details.getRoles());
        vo.setPermissions(details.getPermissions());
        return vo;
    }

    @Override
    public void logout() {
        // 将当前用户的 refresh token 映射清除（如果存在），实现服务端登出
        SecurityUserDetails details = currentUserOrNull();
        if (details != null) {
            String mappingKey = REFRESH_TOKEN_MAPPING_PREFIX + details.getUserId();
            redisTemplate.delete(mappingKey);
        }
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Override
    public LoginResult refreshToken(String refreshToken) {
        // 1. 基础校验：签名 + 过期 + type=refresh
        if (!jwtTokenProvider.validate(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BizException(401, "refresh token 无效或已过期");
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String username = jwtTokenProvider.getUsername(refreshToken);
        Long tenantId = jwtTokenProvider.getTenantId(refreshToken);

        // 2. 旋转检测：Redis 中存储的是当前有效的 refresh token hash，
        //    若传入的 token 与存储不匹配，说明旧 token 被重放（可能泄露），吊销全部 token
        String mappingKey = REFRESH_TOKEN_MAPPING_PREFIX + userId;
        String storedHash = redisTemplate.opsForValue().get(mappingKey);
        String currentHash = Integer.toHexString(refreshToken.hashCode());
        if (storedHash != null && !storedHash.equals(currentHash)) {
            // 检测到 token 重放，清除映射强制重新登录
            redisTemplate.delete(mappingKey);
            log.warn("[安全] 检测到 refresh token 重放攻击 userId={}，已吊销全部 token", userId);
            throw new BizException(401, "检测到异常登录，请重新登录");
        }

        // 3. 签发新的双 token
        String newAccessToken = jwtTokenProvider.generate(userId, username, tenantId);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId, username, tenantId);

        // 4. 更新 Redis 中的 refresh token 映射（旋转）
        String newHash = Integer.toHexString(newRefreshToken.hashCode());
        redisTemplate.opsForValue().set(mappingKey, newHash,
                jwtTokenProvider.parse(newRefreshToken).getExpiration().getTime() - System.currentTimeMillis(),
                TimeUnit.MILLISECONDS);

        log.info("[认证] 用户={} 刷新 token 成功", userId);
        return new LoginResult(newAccessToken, newRefreshToken);
    }

    /** 从 SecurityContext 取当前登录用户（不存在则抛异常） */
    private SecurityUserDetails currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityUserDetails details)) {
            throw new IllegalStateException("无法获取当前登录用户");
        }
        return details;
    }

    /** 从 SecurityContext 取当前登录用户（不存在返回 null，用于登出场景） */
    private SecurityUserDetails currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUserDetails details) {
            return details;
        }
        return null;
    }
}
