package com.knowledge.kb.governance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 文档重复关系实体（检测出的重复对，doc_id1 < doc_id2 保证唯一）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("document_duplicate")
public class DocumentDuplicate implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long kbId;

    /** 文档A（较小 ID） */
    private Long docId1;

    /** 文档B（较大 ID） */
    private Long docId2;

    /** 相似度 0~1 */
    private BigDecimal similarity;

    /** EXACT / NEAR */
    private String dupType;

    /** PENDING / CONFIRMED / IGNORED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
