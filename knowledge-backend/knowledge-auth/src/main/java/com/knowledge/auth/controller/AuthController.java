package com.knowledge.auth.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.knowledge.auth.dto.LoginRequest;
import com.knowledge.auth.dto.LoginResult;
import com.knowledge.auth.dto.PasswordRequest;
import com.knowledge.auth.dto.ProfileRequest;
import com.knowledge.auth.dto.UserInfoVo;
import com.knowledge.auth.service.AuthService;
import com.knowledge.common.annotation.BusinessType;
import com.knowledge.common.annotation.OperLog;
import com.knowledge.common.result.Result;
import com.knowledge.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证接口：登录 / 登出 / 当前用户信息 / 账户资料与密码
 * <p><b>登录限流</b>：{@code /auth/login} 加 {@link SentinelResource}（资源名 {@code api:/auth/login}），
 * 默认 QPS=5 + 匀速排队（controlBehavior=2），防止暴力撞库。超限返回 4290 限流码。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Tag(name = "认证接口")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    @OperLog(title = "用户登录", businessType = BusinessType.LOGIN, recordParam = false)
    @SentinelResource(value = "api:/auth/login", blockHandler = "loginBlockHandler")
    public Result<LoginResult> login(@RequestBody @Valid LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /** 登录限流：返回 4290 限流码（防暴力撞库） */
    public Result<LoginResult> loginBlockHandler(LoginRequest request, BlockException ex) {
        log.warn("[限流] /auth/login 资源=api:/auth/login type={}", ex.getClass().getSimpleName());
        return Result.failed(ResultCode.RATE_LIMITED.getCode(), ResultCode.RATE_LIMITED.getMessage());
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @Operation(summary = "刷新 access token（使用 refresh token 换取新双 token）")
    @PostMapping("/refresh")
    @SentinelResource(value = "api:/auth/refresh", blockHandler = "refreshBlockHandler")
    public Result<LoginResult> refresh(@RequestBody java.util.Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return Result.failed(ResultCode.BAD_REQUEST.getCode(), "refreshToken 不能为空");
        }
        return Result.success(authService.refreshToken(refreshToken));
    }

    /** 刷新接口限流 */
    public Result<LoginResult> refreshBlockHandler(java.util.Map<String, String> body, BlockException ex) {
        log.warn("[限流] /auth/refresh type={}", ex.getClass().getSimpleName());
        return Result.failed(ResultCode.RATE_LIMITED.getCode(), ResultCode.RATE_LIMITED.getMessage());
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/info")
    public Result<UserInfoVo> info() {
        return Result.success(authService.getUserInfo());
    }

    @Operation(summary = "更新账户资料（昵称/头像/邮箱/手机）")
    @PutMapping("/profile")
    @OperLog(title = "账户资料", businessType = BusinessType.UPDATE)
    public Result<UserInfoVo> updateProfile(@RequestBody @Valid ProfileRequest request) {
        return Result.success(authService.updateProfile(request));
    }

    @Operation(summary = "修改密码（校验旧密码）")
    @PutMapping("/password")
    @OperLog(title = "修改密码", businessType = BusinessType.UPDATE, recordParam = false)
    public Result<Void> changePassword(@RequestBody @Valid PasswordRequest request) {
        authService.changePassword(request);
        return Result.success();
    }

    @Operation(summary = "上传头像（返回 URL 路径）")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(authService.updateAvatar(file));
    }
}
