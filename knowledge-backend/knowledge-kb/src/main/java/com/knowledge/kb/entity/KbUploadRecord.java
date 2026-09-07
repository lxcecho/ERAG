package com.knowledge.kb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 上传记录实体（审计日志，不做逻辑删除）
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("kb_upload_record")
public class KbUploadRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属租户ID */
    private Long tenantId;

    /** 关联文档ID（上传失败时可为空） */
    private Long documentId;

    /** 所属知识库ID */
    private Long kbId;

    /** 原始文件名 */
    private String originalName;

    /** 文件大小(字节) */
    private Long fileSize;

    /** 文件类型 */
    private String fileType;

    /** 上传状态：0成功 1失败 */
    private Integer uploadStatus;

    /** 失败原因 */
    private String errorMsg;

    /** 上传人ID */
    private Long creatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
