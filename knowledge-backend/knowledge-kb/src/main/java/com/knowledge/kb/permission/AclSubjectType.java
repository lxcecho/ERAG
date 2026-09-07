package com.knowledge.kb.permission;

/**
 * ACL 主体类型枚举
 *
 * @author: lxcechoo@gmail.com
 */
public enum AclSubjectType {
    USER("U", "用户"),
    ROLE("R", "角色"),
    DEPT("D", "部门（含祖先部门）");

    private final String code;
    private final String label;

    AclSubjectType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static AclSubjectType of(String code) {
        if (code == null) return USER;
        for (AclSubjectType t : values()) {
            if (t.code.equalsIgnoreCase(code.trim())) return t;
        }
        return USER;
    }
}
