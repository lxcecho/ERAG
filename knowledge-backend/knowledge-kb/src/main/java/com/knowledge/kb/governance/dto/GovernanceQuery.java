package com.knowledge.kb.governance.dto;

import com.knowledge.kb.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 治理通用分页查询条件（去重列表 / 审核记录 / 质量列表复用）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GovernanceQuery extends PageQuery {

    /** 知识库ID */
    private Long kbId;

    /** 状态（去重：PENDING/CONFIRMED/IGNORED；审核：PENDING/APPROVED/REJECTED） */
    private String status;

    /** 重复类型 EXACT/NEAR */
    private String dupType;

    /** 文档ID（版本/审核/质量列表按文档过滤） */
    private Long docId;
}
