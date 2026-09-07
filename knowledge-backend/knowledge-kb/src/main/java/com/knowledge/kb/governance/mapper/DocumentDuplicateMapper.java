package com.knowledge.kb.governance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.kb.governance.dto.DuplicateVo;
import com.knowledge.kb.governance.entity.DocumentDuplicate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文档重复关系 Mapper。
 * <p>提供联表分页查询（携带两文档名称），按 KB / 状态 / 类型过滤。
 *
 * @author: lxcechoo@gmail.com
 */
public interface DocumentDuplicateMapper extends BaseMapper<DocumentDuplicate> {

    @Select("""
            <script>
            SELECT d.id, d.kb_id AS kbId, d.doc_id1 AS docId1, d.doc_id2 AS docId2,
                   d.similarity, d.dup_type AS dupType, d.status, d.create_time AS createTime,
                   a.original_name AS docName1, b.original_name AS docName2
            FROM document_duplicate d
            LEFT JOIN kb_document a ON d.doc_id1 = a.id AND a.deleted = 0
            LEFT JOIN kb_document b ON d.doc_id2 = b.id AND b.deleted = 0
            WHERE 1 = 1
              <if test="query.kbId != null"> AND d.kb_id = #{query.kbId} </if>
              <if test="query.status != null and query.status != ''"> AND d.status = #{query.status} </if>
              <if test="query.dupType != null and query.dupType != ''"> AND d.dup_type = #{query.dupType} </if>
            ORDER BY d.create_time DESC
            </script>
            """)
    IPage<DuplicateVo> selectDuplicatePage(IPage<DuplicateVo> page, @Param("query") com.knowledge.kb.governance.dto.GovernanceQuery query);
}
