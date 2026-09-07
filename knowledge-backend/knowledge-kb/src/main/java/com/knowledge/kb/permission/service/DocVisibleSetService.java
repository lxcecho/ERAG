package com.knowledge.kb.permission.service;

import com.knowledge.kb.permission.DocVisibility;

import java.util.Set;

/**
 * 可见文档集合预计算服务（带 Caffeine 本地缓存）。
 * <p>
 * 给 Milvus / ES 「pre-filter（预过滤）」场景使用：如果当前 KB 下用户可见文档数量较小（<1000），
 * 把可见 docId 集合直接作为检索条件的 `documentId IN (...)` 传给 Milvus/ES，
 * 能提前剪枝大量无权文档，降低 Post-Filter 需要处理的 topK 规模。
 * <p>
 * 如果可见集合极大（owner/editor 看整个 KB），返回哨兵 DOC_SET_KB_ALL 表示「整个 KB 可见」，
 * 调用方应走 Post-Filter（扩大 topK 初召）路径。
 *
 * @author: lxcechoo@gmail.com
 */
public interface DocVisibleSetService {

    /** 哨兵值：表示整个知识库可见（owner/editor 场景）；具体返回时用 isAllVisible() 方法判断 */
    long DOC_SET_KB_ALL_SENTINEL = -1L;

    /**
     * 预计算用户在某 KB 下可见文档集合
     *
     * @return 可见集合结果：allVisible=true 表示整个 KB 级可见，否则 docIds 是可见 docId 集合（size<=阈值）
     */
    VisibleSet computeViewableDocIds(Long userId, Long kbId);

    /** 按 docId 过滤（用户是否在可见集合里看得到 docId；若 allVisible=true 且 doc 不是 PRIVATE+非创建者 → 需另查 ACL） */
    boolean isViewable(Long userId, Long kbId, long docId, DocVisibility visibility, Long creatorId);

    /** 失效缓存（写路径触发） */
    void evict(Long kbId, Long userId);

    /** 失效整个 KB 的可见集合缓存（文档新增/删除/ACL 批量变更时触发） */
    void evictForKb(Long kbId);

    /** 可见集合结果对象 */
    record VisibleSet(boolean allVisible, Set<Long> docIds, boolean truncated) {
        /** 是否整个 KB 级全可见（owner/editor 对 PUBLIC/PROTECTED 可见） */
        public boolean isAllVisible() { return allVisible; }
        /** 截断标识：true 表示可见集合超过阈值（1000），建议走 Post-Filter 而不是 IN 查询 */
        public boolean isTruncated() { return truncated; }
    }
}
