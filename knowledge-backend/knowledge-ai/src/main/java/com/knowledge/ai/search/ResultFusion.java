package com.knowledge.ai.search;

import com.knowledge.ai.dto.RetrievalResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索结果融合：RRF（Reciprocal Rank Fusion，倒数排名融合）。
 * <p>设计原因：向量路（余弦相似度）与词法路（BM25）量纲不同，直接加权需归一化且敏感；
 * RRF 只用每路的排名 rank，公式 score = Σ 1/(k + rank)，天然消除量纲差异，
 * 且双路都命中的 chunk 得分叠加，自然提升两路共识结果。k 经验值 60。
 *
 * @author: lxcechoo@gmail.com
 */
public final class ResultFusion {

    private ResultFusion() {
    }

    /**
     * RRF 融合两路检索结果（等权）。
     *
     * @param vectorResults  向量路结果（按相似度降序）
     * @param keywordResults 词法路结果（按 BM25 降序）
     * @param k              RRF 常数（越大排名差异越平缓）
     * @return 融合后结果（按 RRF 分降序，scoreType=fused，按 chunkId 去重）
     */
    public static List<RetrievalResult> rrf(List<RetrievalResult> vectorResults,
                                            List<RetrievalResult> keywordResults,
                                            int k) {
        return rrf(vectorResults, keywordResults, k, 1.0, 1.0);
    }

    /**
     * 加权 RRF 融合两路检索结果。
     * <p>在标准 RRF 基础上引入权重：score = vectorWeight/(k+rank_v) + keywordWeight/(k+rank_k)。
     * <p>适用场景：
     * <ul>
     *   <li>短查询（&lt;5 字）：关键词更精准，可提高 keywordWeight（如 1.5）</li>
     *   <li>长查询/语义查询：向量更精准，可提高 vectorWeight（如 1.5）</li>
     *   <li>含专业术语：关键词权重更高</li>
     *   <li>纯自然语言描述：向量权重更高</li>
     * </ul>
     *
     * @param vectorResults  向量路结果
     * @param keywordResults 词法路结果
     * @param k              RRF 常数
     * @param vectorWeight   向量路权重（默认 1.0）
     * @param keywordWeight  词法路权重（默认 1.0）
     * @return 融合后结果
     */
    public static List<RetrievalResult> rrf(List<RetrievalResult> vectorResults,
                                            List<RetrievalResult> keywordResults,
                                            int k,
                                            double vectorWeight,
                                            double keywordWeight) {
        Map<String, RetrievalResult> repr = new LinkedHashMap<>();
        Map<String, Double> scores = new HashMap<>();

        accumulateWeighted(vectorResults, k, vectorWeight, repr, scores);
        accumulateWeighted(keywordResults, k, keywordWeight, repr, scores);

        return buildFused(repr, scores);
    }

    /**
     * RRF 融合多路检索结果（多查询场景：主查询 + 子查询各自一路）。
     * <p>每路按其内部排名累加 1/(k+rank)，跨路按 chunkId 去重，多路共识的 chunk 分数叠加自然提升。
     * 主查询结果列表应作为其中一路参与，保证原始意图的候选不被子查询稀释删除。
     *
     * @param rankedLists 多路已排序结果（每路内部按相关性降序）
     * @param k           RRF 常数
     * @return 融合后结果（按 RRF 分降序，scoreType=fused，按 chunkId 跨路去重）
     */
    public static List<RetrievalResult> rrfMulti(List<List<RetrievalResult>> rankedLists, int k) {
        Map<String, RetrievalResult> repr = new LinkedHashMap<>();
        Map<String, Double> scores = new HashMap<>();
        if (rankedLists == null || rankedLists.isEmpty()) {
            return List.of();
        }
        for (List<RetrievalResult> list : rankedLists) {
            accumulate(list, k, repr, scores);
        }
        return buildFused(repr, scores);
    }

    /** 由代表结果与累加分构造融合列表（按分降序，scoreType=fused） */
    private static List<RetrievalResult> buildFused(Map<String, RetrievalResult> repr, Map<String, Double> scores) {
        List<RetrievalResult> fused = new ArrayList<>(repr.size());
        for (Map.Entry<String, RetrievalResult> e : repr.entrySet()) {
            RetrievalResult r = e.getValue();
            fused.add(new RetrievalResult(
                    r.getText(),
                    scores.getOrDefault(e.getKey(), 0d),
                    r.getSource(),
                    r.getDocumentId(),
                    r.getChunkIndex(),
                    e.getKey(),
                    SearchConstants.SCORE_FUSED
            ));
        }
        fused.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return fused;
    }

    /** 累加单路结果：按排名累加 1/(k+rank)，并保留首次代表切片 */
    private static void accumulate(List<RetrievalResult> results, int k,
                                   Map<String, RetrievalResult> repr,
                                   Map<String, Double> scores) {
        accumulateWeighted(results, k, 1.0, repr, scores);
    }

    /** 加权累加单路结果：按排名累加 weight/(k+rank)，并保留首次代表切片 */
    private static void accumulateWeighted(List<RetrievalResult> results, int k, double weight,
                                           Map<String, RetrievalResult> repr,
                                           Map<String, Double> scores) {
        int rank = 1;
        for (RetrievalResult r : results) {
            String id = (r.getChunkId() != null && !r.getChunkId().isBlank())
                    ? r.getChunkId()
                    : (r.getDocumentId() + "_" + r.getChunkIndex());
            repr.putIfAbsent(id, r);
            scores.merge(id, weight / (k + rank), Double::sum);
            rank++;
        }
    }
}
