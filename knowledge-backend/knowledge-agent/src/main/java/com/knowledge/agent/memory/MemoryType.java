package com.knowledge.agent.memory;

/**
 * 长期记忆类型（agent_memory.memory_type）。
 * <ul>
 *   <li>{@link #SUMMARY}：会话整体摘要，作用域 session，由 LLM 压缩会话要点生成；</li>
 *   <li>{@link #LONG_TERM}：用户跨会话事实/偏好，作用域 user，由 LLM 从会话抽取，索引至向量库供语义召回。</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
public enum MemoryType {

    SUMMARY,
    LONG_TERM
}
