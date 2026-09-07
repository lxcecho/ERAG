package com.knowledge.kb.permission.service;

import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.permission.DocPermission;

import java.util.Collection;
import java.util.Set;

/**
 * 文档级权限服务（集中化）。
 * <p>
 * 所有「用户能不能对这个文档做什么」的判断都走本服务，禁止在业务代码里散写 if/else 做权限判断。
 * <p>
 * 说明：为避免 knowledge-kb 与 knowledge-ai 模块循环依赖，本服务不感知 {@code RetrievalResult}。
 * RAG 检索后过滤流程应在业务层（RagServiceImpl）组合：先取 docIds → 调 {@link #filterDocIds} → 业务层自行基于 docIdSet removeIf。
 *
 * @author: lxcechoo@gmail.com
 */
public interface DocPermissionService {

    /** 对单文档单动作判断（true=允许，false=拒绝）；userId=null 时默认取当前登录用户 */
    boolean canAccess(Long userId, KbDocument doc, DocPermission action);

    /** 断言权限，失败抛 BizException(403)；同时异步写审计日志（VIEW/DOWNLOAD/EDIT/DELETE/SHARE） */
    void requiredAccess(Long userId, KbDocument doc, DocPermission action);

    /**
     * 批量过滤：返回传入 docId 集合中用户具有 VIEW 权限的子集。
     * <p>典型用途：
     * <ul>
     *   <li>文档分页列表：对 Mapper 返回的本页 docId 做后置过滤</li>
     *   <li>RAG Post-Filter：对检索结果中命中的 docId 做集合裁剪，再在业务层按 docIdSet 移除 chunk</li>
     *   <li>共享链接/导出预览：前置判断某批文档是否对当前用户可见</li>
     * </ul>
     *
     * @param userId 用户ID，null 表示当前登录
     * @param kbId   知识库ID（用于可见集合缓存分桶 + KB 角色/权限继承判定）
     * @param docIds 候选文档集合
     * @return 允许通过 VIEW 校验的 docId 子集（可能与 docIds 等大、子集或空集；不额外补入其他文档）
     */
    Set<Long> filterDocIds(Long userId, Long kbId, Collection<Long> docIds);

    /** 权限变更事件：清空本租户可见集合缓存（写路径调用：ACL 改、可见性改、踢成员） */
    void evictCacheForDocPermissionChange(Long kbId, Long userId);
}
