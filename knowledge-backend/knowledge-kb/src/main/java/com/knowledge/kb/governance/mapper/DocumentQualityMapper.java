package com.knowledge.kb.governance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.kb.governance.dto.GovernanceQuery;
import com.knowledge.kb.governance.dto.QualityVo;
import com.knowledge.kb.governance.entity.DocumentQuality;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文档质量评分 Mapper。
 * <p>提供联表分页查询（携带文档名），按 KB / 文档过滤。
 *
 * @author: lxcechoo@gmail.com
 */
public interface DocumentQualityMapper extends BaseMapper<DocumentQuality> {

    @Select("""
            <script>
            SELECT q.id, q.kb_id AS kbId, q.doc_id AS docId, d.original_name AS docName,
                   q.score, q.completeness, q.freshness, q.structure, q.coverage,
                   q.summary, q.evaluator, q.create_time AS createTime
            FROM document_quality q
            LEFT JOIN kb_document d ON q.doc_id = d.id AND d.deleted = 0
            WHERE 1 = 1
              <if test="query.kbId != null"> AND q.kb_id = #{query.kbId} </if>
              <if test="query.docId != null"> AND q.doc_id = #{query.docId} </if>
            ORDER BY q.score DESC, q.create_time DESC
            </script>
            """)
    IPage<QualityVo> selectQualityPage(IPage<QualityVo> page, @Param("query") GovernanceQuery query);
}
