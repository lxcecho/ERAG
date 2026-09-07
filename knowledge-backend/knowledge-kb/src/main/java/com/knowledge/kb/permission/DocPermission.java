package com.knowledge.kb.permission;

/**
 * 文档级权限动作枚举（5 个正交权限）。
 * <p>设计原则：权限位互不隐含（VIEW 不代表 DOWNLOAD），每个动作在鉴权时独立校验。
 *
 * @author: lxcechoo@gmail.com
 */
public enum DocPermission {

    /** 预览/检索可见（RAG 检索命中 + 文档详情页可见） */
    VIEW("VIEW", "预览/检索可见"),
    /** 编辑（重命名、更新可见性、重新解析、移动知识库） */
    EDIT("EDIT", "编辑文档"),
    /** 删除（软删文档 + 清理 Milvus/ES 向量） */
    DELETE("DELETE", "删除文档"),
    /** 下载原文件 / 导出 PDF */
    DOWNLOAD("DOWNLOAD", "下载原文件"),
    /** 生成外链分享给临时访客（非系统用户） */
    SHARE("SHARE", "分享文档");

    private final String code;
    private final String label;

    DocPermission(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    /** 把字符串解析为枚举（不区分大小写），未知则返回 null */
    public static DocPermission of(String code) {
        if (code == null || code.isBlank()) return null;
        for (DocPermission p : values()) {
            if (p.code.equalsIgnoreCase(code.trim())) return p;
        }
        return null;
    }
}
