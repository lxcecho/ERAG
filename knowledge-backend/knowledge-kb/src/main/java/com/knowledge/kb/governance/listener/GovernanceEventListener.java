package com.knowledge.kb.governance.listener;

import com.knowledge.kb.event.DocumentParsedEvent;
import com.knowledge.kb.governance.service.KnowledgeGovernanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 知识治理事件监听器：消费 knowledge-ai 发出的 {@link DocumentParsedEvent}。
 * <p>文档解析 + 入库成功后，异步触发治理流程（指纹 / 去重 / 评分），
 * 与 RAG 入库主流程解耦，治理失败不影响文档可用性。
 * <p>【多租户】运行于 docAsyncExecutor（TTL 包装），TenantContext 自动透传；
 * 治理 Service 内部按事件携带的 tenantId 显式 setTenantId 落库，双保险防串租户。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GovernanceEventListener {

    private final KnowledgeGovernanceService governanceService;

    @Async("docAsyncExecutor")
    @EventListener
    public void onDocumentParsed(DocumentParsedEvent event) {
        log.info("[治理监听] 收到解析完成事件 doc={} kb={}", event.documentId(), event.kbId());
        try {
            governanceService.onDocumentParsed(
                    event.tenantId(), event.kbId(), event.documentId(),
                    event.text(), event.chunkCount(), event.md5());
        } catch (Exception e) {
            // 治理是 best-effort：任何异常都不应影响主流程，仅记录日志
            log.error("[治理监听] 治理流程异常 doc={}", event.documentId(), e);
        }
    }
}
