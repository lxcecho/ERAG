package com.knowledge.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 添加知识库成员请求
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class KbMemberAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "角色不能为空")
    private String role;
}
