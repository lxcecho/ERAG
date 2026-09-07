package com.knowledge.kb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowledge.kb.entity.KbDocAuditLog;

/**
 * 文档审计服务：写多读少；写路径不阻塞主流程，默认提交到「文档操作日志执行器」异步落库。
 *
 * @author: lxcechoo@gmail.com
 */
public interface KbDocAuditLogService extends IService<KbDocAuditLog> {

    /** 异步写入审计日志（@Async(operLogExecutor)） */
    void asyncAudit(Long userId, Long docId, String action, boolean pass,
                    String passReason, String denyReason);
}
