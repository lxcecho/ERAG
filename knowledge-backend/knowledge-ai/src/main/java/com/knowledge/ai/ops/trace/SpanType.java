package com.knowledge.ai.ops.trace;

/**
 * 链路追踪 span 类型。
 * <p>用于 {@link OpsTrace}#spanType 字段，前端按类型着色与筛选。
 * <ul>
 *   <li>{@link #ROOT}：链路根 span（如 rag.ask / agent.run），parent_span_id 为空</li>
 *   <li>{@link #SEARCH}：检索 span（milvus.search / es.search）</li>
 *   <li>{@link #LLM}：模型调用 span（llm.chat / llm.stream）</li>
 *   <li>{@link #TOOL}：工具调用 span（agent.tool）</li>
 *   <li>{@link #MQ}：消息消费 span（mq.consume）</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
public enum SpanType {

    ROOT,
    SEARCH,
    LLM,
    TOOL,
    MQ,
    /** RAG 流水线阶段（查询改写/扩展/压缩/融合等编排步骤） */
    PIPELINE
}
