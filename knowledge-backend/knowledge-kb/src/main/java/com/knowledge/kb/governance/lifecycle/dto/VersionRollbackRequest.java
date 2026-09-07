package com.knowledge.kb.governance.lifecycle.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 版本回滚请求：将文档恢复至指定历史版本。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class VersionRollbackRequest {

    @NotNull(message = "文档ID不能为空")
    private Long docId;

    /** 目标版本号 */
    @NotNull(message = "目标版本号不能为空")
    @Positive(message = "目标版本号须为正数")
    private Integer version;
}
