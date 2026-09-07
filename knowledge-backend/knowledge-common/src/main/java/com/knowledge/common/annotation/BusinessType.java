package com.knowledge.common.annotation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作业务类型枚举
 * <p>用于 {@link OperLog} 标注操作类别，便于操作日志按类型筛选统计。
 *
 * @author: lxcechoo@gmail.com
 */
@Getter
@AllArgsConstructor
public enum BusinessType {

    /** 其它 */
    OTHER(0, "其它"),
    /** 新增 */
    INSERT(1, "新增"),
    /** 修改 */
    UPDATE(2, "修改"),
    /** 删除 */
    DELETE(3, "删除"),
    /** 导出 */
    EXPORT(4, "导出"),
    /** 导入 */
    IMPORT(5, "导入"),
    /** 登录 */
    LOGIN(6, "登录");

    private final int code;
    private final String desc;
}
