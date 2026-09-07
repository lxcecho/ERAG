package com.knowledge.agent.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Workflow 引擎配置属性（读取 application.yml 中 workflow.* 配置）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "workflow")
public class WorkflowProperties {

    /** 单流程 wall-clock 超时（秒） */
    private int taskTimeoutSeconds = 600;

    /** 节点默认失败重试上限（不含首次；节点定义未配置时使用） */
    private int defaultMaxRetries = 0;

    /** 单流程 token 累计预算封顶（超限终止，防资源耗尽） */
    private int maxTokensPerTask = 80000;

    /** 预置流程注册的"系统租户ID"（全局模板，所有租户可引用） */
    private long systemTenantId = 0L;
}
