package com.knowledge.kb.constant;

/**
 * 知识库模块状态常量
 * <p>状态字段统一以 tinyint 存储，含义在此集中声明，避免魔法值散落。
 *
 * @author: lxcechoo@gmail.com
 */
public final class KbConstants {

    private KbConstants() {
    }

    /* ==================== 知识库状态 ==================== */
    /** 正常 */
    public static final int KB_STATUS_NORMAL = 0;
    /** 停用 */
    public static final int KB_STATUS_DISABLED = 1;

    /* ==================== 文档解析状态 ==================== */
    /** 待解析 */
    public static final int DOC_STATUS_PENDING = 0;
    /** 解析中 */
    public static final int DOC_STATUS_PARSING = 1;
    /** 已解析 */
    public static final int DOC_STATUS_PARSED = 2;
    /** 解析失败 */
    public static final int DOC_STATUS_FAILED = 3;

    /* ==================== 解析任务状态 ==================== */
    /** 待处理 */
    public static final int TASK_STATUS_PENDING = 0;
    /** 处理中 */
    public static final int TASK_STATUS_PROCESSING = 1;
    /** 成功 */
    public static final int TASK_STATUS_SUCCESS = 2;
    /** 失败 */
    public static final int TASK_STATUS_FAILED = 3;

    /* ==================== 上传状态 ==================== */
    /** 上传成功 */
    public static final int UPLOAD_SUCCESS = 0;
    /** 上传失败 */
    public static final int UPLOAD_FAILED = 1;
}
