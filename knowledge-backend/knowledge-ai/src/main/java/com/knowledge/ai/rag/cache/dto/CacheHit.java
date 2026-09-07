package com.knowledge.ai.rag.cache.dto;

import com.knowledge.ai.dto.RetrievalResult;

import java.util.List;

/**
 * 语义缓存命中结果。
 *
 * @param answer     缓存的回答
 * @param sources    缓存的引用来源
 * @param similarity 命中相似度（0~1）
 *
 * @author: lxcechoo@gmail.com
 */
public record CacheHit(String answer, List<RetrievalResult> sources, double similarity) {
}
