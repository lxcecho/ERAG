package com.knowledge.kb.event;

/**
 * 文档删除事件。
 * <p>由 knowledge-kb 在文档删除时发布，由 knowledge-ai 监听以清理 Milvus 中对应向量，
 * 保持业务库与向量库的一致性。
 *
 * @param documentId 文档ID
 * @param kbId       知识库ID
 *
 * @author: lxcechoo@gmail.com
 */
public record DocumentDeletedEvent(Long documentId, Long kbId) {
}
