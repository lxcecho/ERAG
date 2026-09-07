package com.knowledge.kb.governance.lifecycle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 生命周期迁移请求。
 * <p>action 取值：SUBMIT / PUBLISH / APPROVE / REJECT / ARCHIVE / RESTORE / UPDATE。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class LifecycleActionRequest {

    @NotNull(message = "文档ID不能为空")
    private Long docId;

    /** 生命周期动作（LifecycleAction 枚举名） */
    @NotBlank(message = "生命周期动作不能为空")
    private String action;

    /** 备注（审核意见 / 归档原因等） */
    private String comment;
}
