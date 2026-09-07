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
 * 多路 RRF 融合（rrfMulti）单元测试：跨路去重 / 多路共识加权 / 空输入兜底。
 */
class ResultFusionMultiTest {

    private static final int K = 60;

    @Test
    void rrfMulti_空输入返回空() {
        assertTrue(ResultFusion.rrfMulti(null, K).isEmpty());
        assertTrue(ResultFusion.rrfMulti(List.of(), K).isEmpty());
        assertTrue(ResultFusion.rrfMulti(List.of(List.of()), K).isEmpty());
    }

    @Test
    void rrfMulti_跨路按chunkId去重() {
        // 同一 chunkId 在两路出现，应合并为一条
        List<RetrievalResult> listA = List.of(result("文本", "c1", 0.9));
        List<RetrievalResult> listB = List.of(result("文本", "c1", 0.8));
        List<RetrievalResult> fused = ResultFusion.rrfMulti(List.of(listA, listB), K);
        assertEquals(1, fused.size(), "同 chunkId 跨路应去重为一条");
    }

    @Test
    void rrfMulti_多路共识chunk得分高于单路chunk() {
        // c1 在两路都排第 1（共识），c2 仅一路排第 1
        List<RetrievalResult> listA = List.of(result("共识文本", "c1", 0.9), result("单路文本", "c2", 0.5));
        List<RetrievalResult> listB = List.of(result("共识文本", "c1", 0.8));
        List<RetrievalResult> fused = ResultFusion.rrfMulti(List.of(listA, listB), K);
        assertEquals(2, fused.size());
        assertEquals("c1", fused.get(0).getChunkId(), "多路共识的 c1 应排第一");
        assertTrue(fused.get(0).getScore() > fused.get(1).getScore(),
                "共识 chunk 分数应高于单路 chunk");
    }

    @Test
    void rrfMulti_单路等价于仅该路() {
        List<RetrievalResult> list = List.of(result("a", "c1", 0.9), result("b", "c2", 0.5));
        List<RetrievalResult> fused = ResultFusion.rrfMulti(List.of(list), K);
        assertEquals(2, fused.size());
        assertEquals("c1", fused.get(0).getChunkId());
    }

    @Test
    void rrfMulti_融合后scoreType为fused() {
        List<RetrievalResult> list = List.of(result("a", "c1", 0.9));
        List<RetrievalResult> fused = ResultFusion.rrfMulti(List.of(list), K);
        assertEquals("fused", fused.get(0).getScoreType());
    }

    private RetrievalResult result(String text, String chunkId, double score) {
        return new RetrievalResult(text, score, "src", 1L, 0, chunkId, "vector");
    }
}
