package com.knowledge.ai.consistency;

import com.knowledge.ai.search.ElasticsearchService;
import com.knowledge.ai.service.MilvusService;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.service.KbDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 双写一致性检查器：定期校验 Milvus 和 Elasticsearch 的文档索引一致性。
 * <p>背景：文档入库时双写 Milvus（向量）+ Elasticsearch（BM25），但 ES 写入是 best-effort，
 * 可能因网络/ES 暂时不可用导致部分文档仅写入 Milvus 而未写入 ES。
 * <p>策略：每小时扫描最近 24 小时内解析成功的文档，检查其是否同时存在于两个存储中，
 * 对缺失的文档触发补偿写入。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DualWriteConsistencyChecker {

    private final KbDocumentService kbDocumentService;
    private final MilvusService milvusService;
    private final ElasticsearchService elasticsearchService;

    /**
     * 定时一致性检查（每小时执行一次）。
     * <p>扫描最近 24 小时内状态为 PARSED 的文档，检查 Milvus 和 ES 双写一致性。
     */
    @Scheduled(cron = "0 0 * * * ?")  // 每小时整点执行
    public void checkConsistency() {
        log.info("[一致性检查] 开始执行");
        try {
            // 获取最近 24 小时内解析完成的文档
            List<KbDocument> recentDocs = kbDocumentService.listRecentParsed(24);
            if (recentDocs.isEmpty()) {
                log.info("[一致性检查] 无最近解析的文档，跳过");
                return;
            }

            int checked = 0;
            int milvusMissing = 0;
            int esMissing = 0;
            int compensated = 0;

            for (KbDocument doc : recentDocs) {
                try {
                    // 检查 Milvus 中是否有该文档的向量
                    boolean inMilvus = !milvusService.queryByDocument(doc.getId(), 1).isEmpty();

                    // 检查 ES 中是否有该文档的索引（通过 BM25 搜索验证）
                    // 注意：这里仅做存在性检查，不做内容比对
                    boolean inEs = checkEsHasDocument(doc.getId());

                    if (!inMilvus && !inEs) {
                        // 两边都缺失：需要重新入库
                        log.warn("[一致性检查] 文档 {} 在 Milvus 和 ES 中均缺失，需重新入库", doc.getId());
                        milvusMissing++;
                        esMissing++;
                    } else if (!inMilvus) {
                        // 仅 Milvus 缺失：重新向量化
                        log.warn("[一致性检查] 文档 {} 在 Milvus 中缺失", doc.getId());
                        milvusMissing++;
                    } else if (!inEs) {
                        // 仅 ES 缺失：补偿写入 ES
                        log.warn("[一致性检查] 文档 {} 在 ES 中缺失，触发补偿写入", doc.getId());
                        esMissing++;
                        try {
                            compensateEsWrite(doc);
                            compensated++;
                        } catch (Exception e) {
                            log.error("[一致性检查] 补偿写入 ES 失败 doc={}", doc.getId(), e);
                        }
                    }

                    checked++;
                } catch (Exception e) {
                    log.warn("[一致性检查] 检查文档 {} 异常: {}", doc.getId(), e.getMessage());
                }
            }

            log.info("[一致性检查] 完成 checked={} milvusMissing={} esMissing={} compensated={}",
                    checked, milvusMissing, esMissing, compensated);

        } catch (Exception e) {
            log.error("[一致性检查] 执行异常", e);
        }
    }

    /**
     * 检查 ES 中是否包含指定文档的切片。
     * <p>通过查询 documentId 字段判断，返回是否有命中。
     */
    private boolean checkEsHasDocument(Long documentId) {
        try {
            // 使用一个简单的查询来检查 ES 中是否有该文档
            // 这里利用 search 方法，通过 source 字段匹配 documentId
            List<com.knowledge.ai.dto.RetrievalResult> results =
                    elasticsearchService.search("documentId:" + documentId, null, 1);
            return !results.isEmpty();
        } catch (Exception e) {
            log.debug("[一致性检查] ES 查询文档 {} 异常: {}", documentId, e.getMessage());
            return false;
        }
    }

    /**
     * 补偿写入 ES：从 Milvus 读取文档的切片，重新写入 ES。
     * <p>这是一个 best-effort 操作，失败不影响主流程。
     */
    private void compensateEsWrite(KbDocument doc) {
        // 从 Milvus 读取该文档的所有切片
        List<com.knowledge.ai.dto.RetrievalResult> milvusChunks =
                milvusService.queryByDocument(doc.getId(), 1000);

        if (milvusChunks.isEmpty()) {
            log.warn("[一致性检查] Milvus 中也无文档 {} 的切片，无法补偿", doc.getId());
            return;
        }

        // 将 Milvus 中的切片转换为 TextSegment 并写入 ES
        List<dev.langchain4j.data.segment.TextSegment> segments = milvusChunks.stream()
                .map(chunk -> {
                    dev.langchain4j.data.document.Metadata md = new dev.langchain4j.data.document.Metadata();
                    md.put("kbId", String.valueOf(doc.getKbId()));
                    md.put("documentId", String.valueOf(doc.getId()));
                    md.put("source", chunk.getSource());
                    md.put("chunkIndex", String.valueOf(chunk.getChunkIndex()));
                    md.put("chunkId", chunk.getChunkId());
                    return dev.langchain4j.data.segment.TextSegment.from(chunk.getText(), md);
                })
                .collect(Collectors.toList());

        elasticsearchService.store(segments);
        log.info("[一致性检查] 补偿写入 ES 完成 doc={} chunks={}", doc.getId(), segments.size());
    }
}
