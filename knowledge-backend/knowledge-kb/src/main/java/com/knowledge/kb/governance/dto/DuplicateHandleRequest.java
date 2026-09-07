package com.knowledge.kb.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 重复关系处理请求（CONFIRMED 确认重复 / IGNORED 忽略误报）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class DuplicateHandleRequest {

    @NotNull(message = "重复记录ID不能为空")
    private Long id;

    /** CONFIRMED / IGNORED */
    @NotBlank(message = "处理状态不能为空")
    private String status;
}
