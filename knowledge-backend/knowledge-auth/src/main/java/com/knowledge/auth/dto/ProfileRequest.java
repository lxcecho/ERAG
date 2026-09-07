package com.knowledge.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 账户资料更新请求。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Schema(description = "账户资料更新请求")
public class ProfileRequest {

    @Schema(description = "昵称", example = "管理员")
    @Size(max = 64, message = "昵称最长 64 字符")
    private String nickname;

    @Schema(description = "头像 URL")
    @Size(max = 255, message = "头像地址过长")
    private String avatar;

    @Schema(description = "邮箱", example = "user@example.com")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱最长 128 字符")
    private String email;

    @Schema(description = "手机号", example = "13800138000")
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Size(max = 20, message = "手机号最长 20 字符")
    private String phone;
}
