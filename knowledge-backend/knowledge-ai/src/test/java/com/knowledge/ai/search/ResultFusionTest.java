/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.search;

import com.knowledge.ai.dto.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RRF 融合单元测试：验证双路去重、双路共识提升、回退对齐、降级。
 */
class ResultFusionTest {

    @Test
    void rrf_两路重叠时双路共识切片得分最高() {
        // 向量路：c1(rank1) c2(rank2)
        RetrievalResult v1 = new RetrievalResult("t1", 0.9, "s.pdf", 1L, 0, "c1", SearchConstants.SCORE_VECTOR);
        RetrievalResult v2 = new RetrievalResult("t2", 0.8, "s.pdf", 1L, 1, "c2", SearchConstants.SCORE_VECTOR);
        // 关键词路：c2(rank1) c3(rank2) —— c2 双路命中
        RetrievalResult k1 = new RetrievalResult("t2", 5.0, "s.pdf", 1L, 1, "c2", SearchConstants.SCORE_BM25);
        RetrievalResult k2 = new RetrievalResult("t3", 4.0, "s.pdf", 1L, 2, "c3", SearchConstants.SCORE_BM25);

        List<RetrievalResult> fused = ResultFusion.rrf(List.of(v1, v2), List.of(k1, k2), 60);

        // 去重后 3 条（c1/c2/c3）
        assertEquals(3, fused.size());
        // c2 双路得分叠加：1/(60+2) + 1/(60+1)，应为最高
        assertEquals("c2", fused.get(0).getChunkId());
        assertEquals(SearchConstants.SCORE_FUSED, fused.get(0).getScoreType());
        // c2 分数 = 向量 rank2 + 关键词 rank1
        double expectedC2 = 1.0 / (60 + 2) + 1.0 / (60 + 1);
        assertEquals(expectedC2, fused.get(0).getScore(), 0.000001);
        // 降序
        assertTrue(fused.get(0).getScore() >= fused.get(1).getScore());
    }

    @Test
    void rrf_向量路为空时返回关键词路() {
        RetrievalResult k1 = new RetrievalResult("t", 5, "s.pdf", 1L, 0, "c1", SearchConstants.SCORE_BM25);
        List<RetrievalResult> fused = ResultFusion.rrf(List.of(), List.of(k1), 60);
        assertEquals(1, fused.size());
        assertEquals("c1", fused.get(0).getChunkId());
        // 单路 rank1 得分 = 1/(60+1)
        assertEquals(1.0 / 61, fused.get(0).getScore(), 0.000001);
    }

    @Test
    void rrf_关键词路为空时返回向量路() {
        RetrievalResult v1 = new RetrievalResult("t", 0.9, "s.pdf", 1L, 0, "c1", SearchConstants.SCORE_VECTOR);
        List<RetrievalResult> fused = ResultFusion.rrf(List.of(v1), List.of(), 60);
        assertEquals(1, fused.size());
    }

    @Test
    void rrf_chunkId缺失时回退documentId_chunkIndex对齐去重() {
        // 两路 chunkId 均为 null，但 documentId=1 chunkIndex=0 相同，应视为同一 chunk 去重
        RetrievalResult v1 = new RetrievalResult("t1", 0.9, "s.pdf", 1L, 0, null, SearchConstants.SCORE_VECTOR);
        RetrievalResult k1 = new RetrievalResult("t1", 5.0, "s.pdf", 1L, 0, null, SearchConstants.SCORE_BM25);
        List<RetrievalResult> fused = ResultFusion.rrf(List.of(v1), List.of(k1), 60);
        assertEquals(1, fused.size(), "同 documentId+chunkIndex 应去重为一条");
    }

    @Test
    void rrf_双路均为空时返回空() {
        List<RetrievalResult> fused = ResultFusion.rrf(List.of(), List.of(), 60);
        assertTrue(fused.isEmpty());
    }
}
