package com.knowledge.system.operlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志实体
 * <p>不带逻辑删除：日志需长期留存用于审计，仅按时间清理。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属租户（NULL=平台级操作） */
    private Long tenantId;

    /** 模块标题 */
    private String title;

    /** 业务类型 0其它1新增2修改3删除4导出5导入6登录 */
    private Integer businessType;

    /** 方法名（类.方法） */
    private String method;

    /** 请求URL */
    private String requestUrl;

    /** 请求参数(JSON) */
    private String requestParam;

    /** 响应结果(截断) */
    private String responseResult;

    /** 状态 0正常 1异常 */
    private Integer status;

    /** 错误信息 */
    private String errorMsg;

    /** 操作IP */
    private String operIp;

    /** 操作用户 */
    private String operUser;

    /** 耗时(ms) */
    private Long costTime;

    /** 操作时间 */
    private LocalDateTime createTime;
}
