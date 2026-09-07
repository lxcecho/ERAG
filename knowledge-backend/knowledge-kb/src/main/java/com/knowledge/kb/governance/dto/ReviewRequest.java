package com.knowledge.kb.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文档审核请求（APPROVE/REJECT/SUBMIT）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class ReviewRequest {

    @NotNull(message = "文档ID不能为空")
    private Long docId;

    /** APPROVE / REJECT / SUBMIT */
    @NotBlank(message = "审核动作不能为空")
    private String action;

    /** 审核意见 */
    private String comment;
}
