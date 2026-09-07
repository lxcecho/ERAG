package com.knowledge.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识库更新请求
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class KbUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 128, message = "知识库名称最长128个字符")
    private String name;

    @Size(max = 512, message = "描述最长512个字符")
    private String description;

    /** 状态：0正常 1停用 */
    private Integer status;
}
