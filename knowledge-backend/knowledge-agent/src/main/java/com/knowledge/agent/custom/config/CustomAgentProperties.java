package com.knowledge.agent.custom.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 自定义 Agent 运行配置。
 * <p>均有默认值，无需改 application.yml 即可运行；调参可加 custom-agent.* 配置段。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "custom-agent")
public class CustomAgentProperties {

    /** 知识库模式下检索 Top-K */
    private int kbTopK = 5;

    /** 上传日志/文档运行时全文注入上限（字符数），超出截断（不再切片向量化入库） */
    private int logContextLimit = 60000;

    /** 自定义内容（content 模式）最大注入字符数，超出截断 */
    private int contentLimit = 8000;

    /** 单文件上传大小上限（字节），默认 100MB（Spring multipart 上限 100MB 同步） */
    private long maxUploadBytes = 100L * 1024 * 1024;

    /** 上传日志/文档存储根目录（相对 user.dir 或绝对路径），默认 ./data/agent-uploads */
    private String dataPath = "./data/agent-uploads";

    /** 多步流程步骤数上限 */
    private int maxSteps = 5;

    /** 多步流程异步执行线程池大小 */
    private int executorPoolSize = 4;
}
