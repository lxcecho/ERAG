package com.knowledge.ai.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

/**
 * 向量化服务：将文本映射为向量。
 * <p>设计原因：把"文本→向量"独立成单一职责服务，供入库（ingest）与检索（ask）两侧复用，
 * 保证查询向量与文档向量来自同一模型，相似度才有意义。
 *
 * @author: lxcechoo@gmail.com
 */
public interface EmbeddingService {

    /** 单文本向量化 */
    Embedding embed(String text);

    /** 批量向量化（用于文档切片入库，减少请求轮次） */
    List<Embedding> embedAll(List<TextSegment> segments);
}
