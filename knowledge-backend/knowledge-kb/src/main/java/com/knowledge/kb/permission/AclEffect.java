package com.knowledge.kb.permission;

/**
 * ACL 授权效果枚举
 *
 * @author: lxcechoo@gmail.com
 */
public enum AclEffect {
    ALLOW("A", "允许"),
    DENY("D", "拒绝");

    private final String code;
    private final String label;

    AclEffect(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static AclEffect of(String code) {
        if (code == null) return ALLOW;
        return code.equalsIgnoreCase("D") ? DENY : ALLOW;
    }
}
