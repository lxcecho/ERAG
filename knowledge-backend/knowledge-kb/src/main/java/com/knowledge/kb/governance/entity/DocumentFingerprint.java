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
 * 文档指纹实体（去重基础数据：MD5 精确 + SimHash 近似）。
 * <p>1:1 关联 kb_document，文档解析完成后由治理监听器写入。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("document_fingerprint")
public class DocumentFingerprint implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long kbId;

    /** 文档ID */
    private Long docId;

    /** MD5（与 kb_document.md5 冗余，便于按 KB 批量查重） */
    private String md5;

    /** SimHash 64 位指纹（NULL=尚未计算，如解析失败） */
    private Long simhash;

    /** 解析后纯文本长度 */
    private Integer contentLength;

    /** 切片数量（近似 token 估计） */
    private Integer tokenCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
