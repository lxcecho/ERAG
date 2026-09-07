package com.knowledge.kb.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.kb.dto.ParseTaskVo;
import com.knowledge.kb.dto.TaskDetailVo;
import com.knowledge.kb.dto.TaskQuery;
import com.knowledge.kb.entity.KbParseTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 解析任务 Mapper
 *
 * @author: lxcechoo@gmail.com
 */
public interface KbParseTaskMapper extends BaseMapper<KbParseTask> {

    /**
     * 任务分页查询：左联 kb_document 取文档原始名，按知识库过滤。
     */
    @Select("""
            <script>
            SELECT t.id, t.document_id AS documentId, t.kb_id AS kbId, t.status, t.error_msg AS errorMsg,
                   t.start_time AS startTime, t.end_time AS endTime, t.create_time AS createTime,
                   d.original_name AS originalName
            FROM kb_parse_task t
            LEFT JOIN kb_document d ON t.document_id = d.id AND d.deleted = 0
            <where>
              <if test="kbId != null"> AND t.kb_id = #{kbId} </if>
            </where>
            ORDER BY t.create_time DESC
            </script>
            """)
    IPage<ParseTaskVo> selectTaskPage(IPage<ParseTaskVo> page, @Param("kbId") Long kbId);

    /**
     * 任务分页查询（支持状态过滤）：左联 kb_document 取文档信息。
     */
    @Select("""
            <script>
            SELECT t.id, t.document_id AS documentId, t.kb_id AS kbId, t.status, t.error_msg AS errorMsg,
                   t.start_time AS startTime, t.end_time AS endTime, t.create_time AS createTime,
                   d.original_name AS originalName
            FROM kb_parse_task t
            LEFT JOIN kb_document d ON t.document_id = d.id AND d.deleted = 0
            <where>
              <if test="query.kbId != null"> AND t.kb_id = #{query.kbId} </if>
              <if test="query.status != null"> AND t.status = #{query.status} </if>
            </where>
            ORDER BY t.create_time DESC
            </script>
            """)
    IPage<ParseTaskVo> selectTaskPageQuery(IPage<ParseTaskVo> page, @Param("query") TaskQuery query);

    /**
     * 任务详情：联查文档信息。
     */
    @Select("""
            SELECT t.id, t.document_id AS documentId, t.kb_id AS kbId, t.status, t.error_msg AS errorMsg,
                   t.start_time AS startTime, t.end_time AS endTime, t.create_time AS createTime,
                   d.original_name AS originalName, d.file_size AS fileSize, d.file_type AS fileType,
                   d.status AS docStatus, d.chunk_count AS chunkCount
            FROM kb_parse_task t
            LEFT JOIN kb_document d ON t.document_id = d.id AND d.deleted = 0
            WHERE t.id = #{taskId}
            """)
    TaskDetailVo selectDetail(@Param("taskId") Long taskId);

    /**
     * 扫描卡住任务：PROCESSING 且 start_time 早于阈值（进程崩溃/重启遗留）。
     * <p>跨租户全局扫描，关闭 MP 租户拦截器（补偿任务需覆盖所有租户）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM kb_parse_task WHERE status = #{status} AND start_time < #{threshold}")
    List<KbParseTask> selectStuckTasks(@Param("status") int status,
                                       @Param("threshold") java.time.LocalDateTime threshold);

    /**
     * 扫描可恢复的失败任务：FAILED 且 retry_count < maxRetries（自动恢复重投）。
     * <p>跨租户全局扫描，关闭 MP 租户拦截器。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM kb_parse_task WHERE status = #{status} AND retry_count < #{maxRetries}")
    List<KbParseTask> selectRecoverableFailedTasks(@Param("status") int status,
                                                   @Param("maxRetries") int maxRetries);
}


