package com.knowledge.ai.search;

import com.knowledge.ai.dto.RetrievalResult;

import java.util.List;

/**
 * 重排序服务：对融合后的候选集精排，提升最终 Top-N 精度。
 * <p>设计原因：RRF 是粗排（只用排名），Rerank 用 query 与候选的细粒度交互（Cross-Encoder）
 * 重打分；通过可插拔实现支持 noop（零成本）/ http（外部模型）切换，由配置 ai.rag.hybrid.rerank.type 决定。
 *
 * @author: lxcechoo@gmail.com
 */
public interface RerankService {

    /**
     * 对候选切片重排序。
     *
     * @param question   原始问题（与候选做相关性匹配）
     * @param candidates 融合后候选切片
     * @param topN       精排后保留条数
     * @return 精排结果
     */
    List<RetrievalResult> rerank(String question, List<RetrievalResult> candidates, int topN);
}
