package com.knowledge.auth.service;

import com.knowledge.auth.dto.LoginRequest;
import com.knowledge.auth.dto.LoginResult;
import com.knowledge.auth.dto.PasswordRequest;
import com.knowledge.auth.dto.ProfileRequest;
import com.knowledge.auth.dto.UserInfoVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证服务接口
 *
 * @author: lxcechoo@gmail.com
 */
public interface AuthService {

    /** 登录：校验账密并签发 JWT */
    LoginResult login(LoginRequest request);

    /** 获取当前登录用户信息（含角色、权限） */
    UserInfoVo getUserInfo();

    /** 更新当前用户资料（昵称/头像/邮箱/手机），返回最新用户信息 */
    UserInfoVo updateProfile(ProfileRequest request);

    /** 修改密码：校验旧密码后更新新密码 */
    void changePassword(PasswordRequest request);

    /** 上传头像，返回可访问 URL 路径 */
    String updateAvatar(MultipartFile file);

    /** 登出 */
    void logout();

    /**
     * 刷新 access token：使用 refresh token 换取新的 access token + 新的 refresh token。
     * <p>refresh token 一次性使用（旋转），旧 refresh token 立即失效。
     *
     * @param refreshToken refresh token
     * @return 新的 LoginResult（含新 access token + 新 refresh token）
     */
    LoginResult refreshToken(String refreshToken);
}
