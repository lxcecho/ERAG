package com.knowledge.ai.controller;

import com.knowledge.ai.dto.IngestResult;
import com.knowledge.ai.search.ElasticsearchService;
import com.knowledge.ai.service.MilvusService;
import com.knowledge.ai.service.RagService;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.exception.BizException;
import com.knowledge.common.result.Result;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.service.KbDocumentService;
import com.knowledge.kb.service.KbPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 索引管理接口：提供 ES + Milvus 双库索引重建能力。
 * <p>设计原因：双写采用 best-effort，ES 写失败仅标记 es_indexed=2 不阻断；
 * 此接口供拥有者重建指定文档索引（先清旧索引再重新入库），用于补偿数据不一致。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Tag(name = "索引管理接口")
@RestController
@RequestMapping("/ai/index")
@RequiredArgsConstructor
public class IndexController {

    private final RagService ragService;
    private final MilvusService milvusService;
    private final ElasticsearchService elasticsearchService;
    private final KbDocumentService kbDocumentService;
    private final KbPermissionService kbPermissionService;

    @Operation(summary = "重建文档索引（清空 Milvus + ES 后重新双写入库）")
    @PostMapping("/rebuild")
    public Result<IngestResult> rebuild(@RequestParam Long documentId) {
        Long userId = SecurityUtils.currentUserId();
        KbDocument doc = kbDocumentService.getById(documentId);
        if (doc == null) {
            throw new BizException("文档不存在: " + documentId);
        }
        // 仅拥有者可重建索引
        kbPermissionService.checkOwner(doc.getKbId(), userId);

        // 1. 清旧索引（双库各自容错）
        try {
            milvusService.deleteByDocument(documentId);
        } catch (Exception e) {
            log.warn("[索引重建] Milvus清理失败 doc={}: {}", documentId, e.getMessage());
        }
        try {
            elasticsearchService.deleteByDocument(documentId);
        } catch (Exception e) {
            log.warn("[索引重建] ES清理失败 doc={}: {}", documentId, e.getMessage());
        }

        // 2. 重新入库（双写 Milvus + ES，chunkId 幂等覆盖）
        IngestResult result = ragService.ingest(documentId);
        log.info("[索引重建] doc={} chunks={}", documentId, result.getChunkCount());
        return Result.success(result);
    }
}
