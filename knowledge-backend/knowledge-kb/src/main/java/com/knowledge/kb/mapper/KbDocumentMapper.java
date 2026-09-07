package com.knowledge.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.kb.dto.KbDocumentQuery;
import com.knowledge.kb.dto.KbDocumentVo;
import com.knowledge.kb.entity.KbDocument;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文档 Mapper
 * <p>除 BaseMapper 通用能力外，提供联表分页查询（携带知识库名称）。
 *
 * @author: lxcechoo@gmail.com
 */
public interface KbDocumentMapper extends BaseMapper<KbDocument> {

    /**
     * 文档分页查询：左联 knowledge_base 取知识库名称，支持按知识库 / 状态 / 文件名关键字过滤。
     */
    @Select("""
            <script>
            SELECT d.id, d.kb_id AS kbId, d.original_name AS originalName, d.stored_name AS storedName,
                   d.file_path AS filePath, d.file_size AS fileSize, d.file_type AS fileType,
                   d.file_suffix AS fileSuffix, d.md5, d.status, d.chunk_count AS chunkCount,
                   d.creator_id AS creatorId, d.create_time AS createTime,
                   b.name AS kbName
            FROM kb_document d
            LEFT JOIN knowledge_base b ON d.kb_id = b.id AND b.deleted = 0
            WHERE d.deleted = 0
              <if test="query.kbId != null"> AND d.kb_id = #{query.kbId} </if>
              <if test="query.status != null"> AND d.status = #{query.status} </if>
              <if test="query.originalName != null and query.originalName != ''">
                  AND d.original_name LIKE CONCAT('%', #{query.originalName}, '%')
              </if>
            ORDER BY d.create_time DESC
            </script>
            """)
    IPage<KbDocumentVo> selectDocumentPage(IPage<KbDocumentVo> page, @Param("query") KbDocumentQuery query);

    /**
     * 物理删除文档（绕过 @TableLogic 软删），供保留期硬删除使用。
     */
    @Delete("DELETE FROM kb_document WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
