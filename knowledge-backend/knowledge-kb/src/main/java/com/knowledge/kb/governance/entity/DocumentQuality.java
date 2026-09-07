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
 * 文档质量评分实体（4 维评分 + 总分，规则或 LLM 评估）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("document_quality")
public class DocumentQuality implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long kbId;

    private Long docId;

    /** 总分 0-100 */
    private Integer score;

    /** 完整性 0-25 */
    private Integer completeness;

    /** 时效性 0-25 */
    private Integer freshness;

    /** 结构性 0-25 */
    private Integer structure;

    /** 覆盖度 0-25 */
    private Integer coverage;

    /** 评分说明 */
    private String summary;

    /** RULE 规则 / LLM 模型 */
    private String evaluator;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
