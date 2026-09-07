package com.knowledge.kb.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 知识库成员角色枚举（三角色权限模型）。
 * <p>权限层级：OWNER(3) > EDITOR(2) > VIEWER(1)。
 * 通过 level 比较实现"高角色覆盖低角色权限"，校验时只需判断 level >= 要求等级。
 *
 * @author: lxcechoo@gmail.com
 */
@Getter
@AllArgsConstructor
public enum KbRole {

    /** 所有者：全部权限，含成员管理与删除知识库 */
    OWNER("owner", 3),
    /** 编辑者：可上传/删除文档、问答 */
    EDITOR("editor", 2),
    /** 查看者：仅可问答 */
    VIEWER("viewer", 1);

    /** 角色编码（存库值） */
    private final String code;
    /** 权限等级（越大权限越高） */
    private final int level;

    /** 由编码解析枚举，非法值返回 null */
    public static KbRole of(String code) {
        return Arrays.stream(values())
                .filter(r -> r.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }
}
