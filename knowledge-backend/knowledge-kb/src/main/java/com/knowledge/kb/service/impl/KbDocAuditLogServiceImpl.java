package com.knowledge.kb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.common.context.TenantContext;
import com.knowledge.kb.entity.KbDocAuditLog;
import com.knowledge.kb.mapper.KbDocAuditLogMapper;
import com.knowledge.kb.service.KbDocAuditLogService;
import com.knowledge.auth.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 文档审计服务：@Async(operLogExecutor) 异步落库，不阻塞主流程。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbDocAuditLogServiceImpl extends ServiceImpl<KbDocAuditLogMapper, KbDocAuditLog>
        implements KbDocAuditLogService {

    @Override
    @Async("operLogExecutor")
    public void asyncAudit(Long userId, Long docId, String action, boolean pass,
                           String passReason, String denyReason) {
        try {
            KbDocAuditLog log = new KbDocAuditLog();
            log.setTenantId(TenantContext.getTenantId());
            log.setUserId(userId != null ? userId : resolveUserId());
            log.setDocId(docId);
            log.setAction(action);
            log.setResult(pass ? "A" : "D");
            log.setPassReason(pass ? passReason : null);
            log.setDenyReason(pass ? null : denyReason);
            // 尝试填充 IP/UA（审计辅助）
            tryFillRequestInfo(log);
            this.save(log);
        } catch (Exception e) {
            // 审计写入失败不影响主流程，只打 warn
            KbDocAuditLogServiceImpl.log.warn("[DocAudit] 审计写入失败 doc={} action={}: {}", docId, action, e.getMessage());
        }
    }

    private Long resolveUserId() {
        try { return SecurityUtils.currentUserId(); } catch (Exception ignore) { return null; }
    }

    private void tryFillRequestInfo(KbDocAuditLog log) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return;
            HttpServletRequest req = attrs.getRequest();
            log.setIp(req.getRemoteAddr());
            String ua = req.getHeader("User-Agent");
            if (ua != null && ua.length() > 512) ua = ua.substring(0, 512);
            log.setUserAgent(ua);
        } catch (Exception ignore) {
            // 审计辅助字段，失败静默
        }
    }
}
