package com.knowledge.ai.listener;

import com.knowledge.ai.dto.IngestResult;
import com.knowledge.ai.search.ElasticsearchService;
import com.knowledge.ai.service.MilvusService;
import com.knowledge.ai.service.RagService;
import com.knowledge.kb.event.DocumentDeletedEvent;
import com.knowledge.kb.event.DocumentParseTaskEvent;
import com.knowledge.kb.service.KbParseTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * RAG 事件监听器：消费 knowledge-kb 发出的事件。
 * <p>解析任务事件：在 {@code docAsyncExecutor} 线程内同步执行（事件由 kb 侧 @Async 方法发布），
 * 通过 {@link KbParseTaskService} 状态机方法编排任务流转（PENDING/FAILED→PROCESSING→SUCCESS/FAILED），
 * 与 MQ 消费者（ParseTaskConsumer）复用同一套状态机，保证双路径状态流转一致。
 * <p><b>降级路径幂等</b>：{@code markProcessingIfPending} 条件更新保证重复事件不会重复解析
 * （已 SUCCESS/PROCESSING 的任务抢占失败即跳过）。
 * <p>文档删除事件：异步清理 Milvus 向量，避免阻塞删除请求。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagEventListener {

    private final RagService ragService;
    private final MilvusService milvusService;
    private final ElasticsearchService elasticsearchService;
    private final KbParseTaskService kbParseTaskService;

    @EventListener
    public void onParseTask(DocumentParseTaskEvent event) {
        Long taskId = event.taskId();
        Long documentId = event.documentId();
        log.info("[RAG监听] 收到解析任务 task={} doc={}", taskId, documentId);
        // 幂等闸门：条件抢占 PENDING/FAILED→PROCESSING（retry_count+1），失败说明已处理，跳过
        if (!kbParseTaskService.markProcessingIfPending(taskId)) {
            log.info("[RAG监听] task={} 已处理或并发抢占失败，跳过（幂等）", taskId);
            return;
        }
        try {
            IngestResult result = ragService.ingest(documentId);
            kbParseTaskService.markSuccess(taskId, result.getChunkCount());
            log.info("[RAG监听] 解析完成 task={} doc={} chunks={}", taskId, documentId, result.getChunkCount());
        } catch (Exception e) {
            log.error("[RAG监听] 解析失败 task={} doc={}", taskId, documentId, e);
            kbParseTaskService.markFailed(taskId, e.getMessage());
        }
    }

    @Async("docAsyncExecutor")
    @EventListener
    public void onDocumentDeleted(DocumentDeletedEvent event) {
        // 双删：Milvus 与 ES 各自独立清理，互不影响
        try {
            milvusService.deleteByDocument(event.documentId());
        } catch (Exception e) {
            log.warn("[RAG监听] 向量清理失败 doc={}: {}", event.documentId(), e.getMessage());
        }
        try {
            elasticsearchService.deleteByDocument(event.documentId());
        } catch (Exception e) {
            log.warn("[RAG监听] ES清理失败 doc={}: {}", event.documentId(), e.getMessage());
        }
    }
}
