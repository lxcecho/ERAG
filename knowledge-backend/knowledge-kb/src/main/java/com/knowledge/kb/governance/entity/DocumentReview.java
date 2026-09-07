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
 * 文档审核记录实体（审核动作审计流水：SUBMIT/APPROVE/REJECT）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("document_review")
public class DocumentReview implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long kbId;

    private Long docId;

    private Long reviewerId;

    /** SUBMIT / APPROVE / REJECT */
    private String action;

    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
