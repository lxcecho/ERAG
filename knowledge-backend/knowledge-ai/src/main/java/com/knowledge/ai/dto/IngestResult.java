package com.knowledge.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 文档入库结果
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@AllArgsConstructor
public class IngestResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文档ID */
    private Long documentId;

    /** 切片数量 */
    private int chunkCount;

    /** 入库向量条数 */
    private int vectorCount;
}
