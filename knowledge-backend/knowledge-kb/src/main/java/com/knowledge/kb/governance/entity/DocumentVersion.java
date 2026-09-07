package com.knowledge.kb.governance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档版本历史实体（同文档每次更新生成一条版本记录，主表 kb_document.version 指向当前版本）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("document_version")
public class DocumentVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long kbId;

    private Long docId;

    /** 版本号 */
    private Integer version;

    private String storedName;

    private String filePath;

    private Long fileSize;

    private String md5;

    /** 版本变更说明 */
    private String changeLog;

    private Long creatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
