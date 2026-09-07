package com.knowledge.ai.rag.cache.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 语义缓存实体：按 embedding 余弦相似度命中缓存，跳过检索+LLM 生成。
 * <p>vectorText 存储问题向量（逗号分隔的 float 值），lookup 时逐一余弦比较。
 * <p>设计原因：MySQL 无原生向量类型，candidateLimit 限制计算量（默认 50 条），
 * 1536 维 * 50 条 ≈ 77K 次乘法，Java 端 < 1ms。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("rag_semantic_cache")
public class SemanticCache implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属租户ID（隔离） */
    private Long tenantId;

    /** 知识库ID（隔离） */
    private Long kbId;

    /** 原始问题文本 */
    private String question;

    /** 问题 MD5 指纹（精确匹配层，避免向量计算开销） */
    private String questionMd5;

    /** 问题向量（逗号分隔 float，如 "0.123,0.456,..."） */
    private String vectorText;

    /** 缓存的 LLM 回答 */
    private String answer;

    /** 引用来源 JSON（RetrievalResult 列表序列化） */
    private String sourcesJson;

    /** 命中相似度（写入时记录，用于调试与淘汰策略） */
    private Double similarity;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
