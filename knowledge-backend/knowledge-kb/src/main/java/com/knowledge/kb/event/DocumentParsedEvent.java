package com.knowledge.kb.event;

/**
 * 文档解析完成事件。
 * <p>由 knowledge-ai 的 RagServiceImpl 在文档解析 + 切片 + 入库成功后发布，
 * 携带解析后的纯文本与切片数；由 knowledge-kb 的 GovernanceEventListener 异步消费，
 * 触发知识治理流程：计算 SimHash 指纹 → 检测重复 → 规则质量评分。
 * <p>跨模块事件方向：ai 模块依赖 kb 模块，事件类定义在 kb，ai 发布、kb 监听，
 * 复用同一 Spring ApplicationContext 的事件总线，保持业务库与治理数据的一致性。
 *
 * @param tenantId  租户ID（异步线程显式身份，避免依赖上下文）
 * @param kbId       知识库ID
 * @param documentId 文档ID
 * @param text       解析后纯文本（用于 SimHash / 质量评分）
 * @param chunkCount 切片数量
 * @param md5        文件 MD5（精确去重用，与 kb_document.md5 一致）
 *
 * @author: lxcechoo@gmail.com
 */
public record DocumentParsedEvent(Long tenantId, Long kbId, Long documentId,
                                  String text, int chunkCount, String md5) {
}
