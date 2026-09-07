package com.knowledge.kb.event;

/**
 * 文档解析任务事件。
 * <p>由 knowledge-kb 在解析任务被触发时发布，由 knowledge-ai 监听消费，
 * 实现 kb 与 ai 模块间的解耦（kb 不依赖 ai，避免模块循环依赖）。
 *
 * @param taskId     解析任务ID
 * @param documentId 文档ID
 * @param kbId       知识库ID
 *
 * @author: lxcechoo@gmail.com
 */
public record DocumentParseTaskEvent(Long taskId, Long documentId, Long kbId) {
}
