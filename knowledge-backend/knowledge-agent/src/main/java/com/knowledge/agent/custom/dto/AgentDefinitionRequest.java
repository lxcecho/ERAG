package com.knowledge.agent.custom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 自定义 Agent 定义创建/编辑请求。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Schema(description = "自定义 Agent 定义请求")
public class AgentDefinitionRequest {

    /** 编辑时必填 */
    @Schema(description = "定义ID（编辑时必填）")
    private Long id;

    @Schema(description = "Agent 名称", example = "日志根因分析助手", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Agent 名称不能为空")
    @Size(max = 64, message = "名称最长 64 字符")
    private String name;

    @Schema(description = "功能描述")
    @Size(max = 512, message = "描述最长 512 字符")
    private String description;

    @Schema(description = "图标/头像 URL")
    private String avatar;

    @Schema(description = "自定义系统提示词（支持 {context}/{question} 占位符）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "系统提示词不能为空")
    private String systemPrompt;

    /**
     * 数据源模式：缺省 kb（运行时由对话框交互决定：拖文件=log，检索开关开=kb，关=plain）
     */
    @Schema(description = "数据源模式 kb/content/log，缺省 kb；运行时由对话框 inputType 覆盖", example = "kb")
    private String sourceMode;

    @Schema(description = "默认绑定知识库ID（可选；对话框可随时切换其他知识库）")
    private Long kbId;

    /** 执行模型：single 单步流式 / multi 多步骤流程 */
    @Schema(description = "执行模型 single/multi", example = "single", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "执行模型不能为空")
    private String execMode;

    @Schema(description = "多步骤流程定义 JSON（execMode=multi 时必填）")
    private String steps;
}
