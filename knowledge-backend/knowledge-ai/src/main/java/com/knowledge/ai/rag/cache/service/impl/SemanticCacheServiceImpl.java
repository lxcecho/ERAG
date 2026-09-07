package com.knowledge.ai.rag.cache.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.rag.cache.dto.CacheHit;
import com.knowledge.ai.rag.cache.entity.SemanticCache;
import com.knowledge.ai.rag.cache.mapper.SemanticCacheMapper;
import com.knowledge.ai.rag.cache.service.SemanticCacheService;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 语义缓存服务实现。
 * <p>lookup：取最近 N 条未过期缓存，逐一计算余弦相似度，取最高分 >= threshold 的命中。
 * <p>save：将问题向量序列化为逗号分隔字符串，回答与来源 JSON 一同落库。
 * <p>所有异常 best-effort：lookup 失败返回 empty，save 失败仅日志告警，不阻断主流程。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheServiceImpl implements SemanticCacheService {

    private final SemanticCacheMapper cacheMapper;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<CacheHit> lookup(Long tenantId, Long kbId, Embedding queryEmbedding) {
        var config = aiProperties.getRag().getSemanticCache();
        if (!config.isEnabled()) {
            return Optional.empty();
        }

        try {
            LocalDateTime expiry = LocalDateTime.now().minusHours(config.getTtlHours());

            // 第一层：MD5 精确匹配（O(1) 查询，无需向量计算）
            // 注：lookup 调用方传入的是 embedding 而非原始问题，MD5 匹配在 RagServiceImpl 层做更合适；
            // 此处仍保留向量相似度作为兜底，MD5 精确匹配在 save 时写入 questionMd5，
            // RagServiceImpl 可在调用 lookup 前先做 questionMd5 精确查询（见下方 isExactHit）。

            // 第二层：向量余弦相似度匹配
            LambdaQueryWrapper<SemanticCache> wrapper = new LambdaQueryWrapper<SemanticCache>()
                    .eq(SemanticCache::getTenantId, tenantId)
                    .eq(SemanticCache::getKbId, kbId)
                    .ge(SemanticCache::getCreateTime, expiry)
                    .orderByDesc(SemanticCache::getCreateTime)
                    .last("LIMIT " + config.getCandidateLimit());

            List<SemanticCache> candidates = cacheMapper.selectList(wrapper);
            if (candidates.isEmpty()) {
                return Optional.empty();
            }

            float[] queryVec = queryEmbedding.vector();
            CacheHit bestHit = null;
            double bestScore = config.getThreshold();

            for (SemanticCache candidate : candidates) {
                float[] candidateVec = stringToVector(candidate.getVectorText());
                if (candidateVec.length != queryVec.length) {
                    continue;
                }
                double similarity = cosineSimilarity(queryVec, candidateVec);
                if (similarity >= bestScore) {
                    bestScore = similarity;
                    List<RetrievalResult> sources = deserializeSources(candidate.getSourcesJson());
                    bestHit = new CacheHit(candidate.getAnswer(), sources, similarity);
                }
            }

            if (bestHit != null) {
                log.info("[语义缓存] 命中 tenant={} kb={} similarity={} threshold={}",
                        tenantId, kbId, String.format("%.4f", bestHit.similarity()), config.getThreshold());
            }
            return Optional.ofNullable(bestHit);
        } catch (Exception e) {
            log.warn("[语义缓存] 查找异常 tenant={} kb={}: {}", tenantId, kbId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 第一层：MD5 精确匹配（无需向量计算，O(1) 数据库查询）。
     * <p>供 RagServiceImpl 在 embedding 之前调用，命中则跳过 embedding + 向量检索 + LLM。
     *
     * @param tenantId 租户ID
     * @param kbId     知识库ID
     * @param question 原始问题（未 embedding）
     * @return 命中时返回 CacheHit，未命中返回 empty
     */
    @Override
    public Optional<CacheHit> exactLookup(Long tenantId, Long kbId, String question) {
        var config = aiProperties.getRag().getSemanticCache();
        if (!config.isEnabled()) {
            return Optional.empty();
        }
        try {
            String md5 = md5(question);
            LocalDateTime expiry = LocalDateTime.now().minusHours(config.getTtlHours());
            LambdaQueryWrapper<SemanticCache> wrapper = new LambdaQueryWrapper<SemanticCache>()
                    .eq(SemanticCache::getTenantId, tenantId)
                    .eq(SemanticCache::getKbId, kbId)
                    .eq(SemanticCache::getQuestionMd5, md5)
                    .ge(SemanticCache::getCreateTime, expiry)
                    .orderByDesc(SemanticCache::getCreateTime)
                    .last("LIMIT 1");
            SemanticCache hit = cacheMapper.selectOne(wrapper);
            if (hit != null) {
                List<RetrievalResult> sources = deserializeSources(hit.getSourcesJson());
                log.info("[语义缓存] MD5 精确命中 tenant={} kb={} questionLen={}", tenantId, kbId, question.length());
                return Optional.of(new CacheHit(hit.getAnswer(), sources, 1.0));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[语义缓存] MD5 查找异常 tenant={} kb={}: {}", tenantId, kbId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void save(Long tenantId, Long kbId, String question, Embedding embedding,
                     String answer, List<RetrievalResult> sources) {
        var config = aiProperties.getRag().getSemanticCache();
        if (!config.isEnabled()) {
            return;
        }

        try {
            SemanticCache cache = new SemanticCache();
            cache.setTenantId(tenantId);
            cache.setKbId(kbId);
            cache.setQuestion(question);
            cache.setQuestionMd5(md5(question));
            cache.setVectorText(vectorToString(embedding.vector()));
            cache.setAnswer(answer);
            cache.setSourcesJson(serializeSources(sources));
            cacheMapper.insert(cache);
            log.debug("[语义缓存] 写入 tenant={} kb={} questionLen={}", tenantId, kbId, question.length());
        } catch (Exception e) {
            log.warn("[语义缓存] 写入异常 tenant={} kb={}: {}", tenantId, kbId, e.getMessage());
        }
    }

    // ---- 向量序列化/反序列化 ----

    /** 计算字符串 MD5（用于精确匹配层） */
    private static String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            // MD5 是 JDK 标准算法，不应失败；降级返回原文长度作为弱指纹
            return String.valueOf(text.length());
        }
    }

    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 9);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    private float[] stringToVector(String text) {
        if (text == null || text.isBlank()) return new float[0];
        String[] parts = text.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }

    /** 余弦相似度：dot(a,b) / (|a| * |b|) */
    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ---- JSON 序列化 ----

    private String serializeSources(List<RetrievalResult> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception e) {
            log.warn("[语义缓存] sources 序列化失败: {}", e.getMessage());
            return "[]";
        }
    }

    private List<RetrievalResult> deserializeSources(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<RetrievalResult>>() {});
        } catch (Exception e) {
            log.warn("[语义缓存] sources 反序列化失败: {}", e.getMessage());
            return List.of();
        }
    }
}
