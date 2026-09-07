package com.knowledge.kb.permission;

/**
 * 文档可见性枚举（控制 viewer 默认能否看到）。
 *
 * @author: lxcechoo@gmail.com
 */
public enum DocVisibility {

    /** 公开：继承 KB 角色权限，viewer 可看（默认） */
    PUBLIC("P", "公开"),
    /** 私有：仅创建者 + KB owner/editor + ACL 显式授权可看，viewer 默认不可见 */
    PRIVATE("R", "私有"),
    /** 保护：viewer 默认不可，需 ACL 追加授权（跨部门协作文档场景） */
    PROTECTED("T", "保护");

    private final String code;
    private final String label;

    DocVisibility(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static DocVisibility of(String code) {
        if (code == null) return PUBLIC;
        for (DocVisibility v : values()) {
            if (v.code.equalsIgnoreCase(code.trim())) return v;
        }
        return PUBLIC;
    }
}
