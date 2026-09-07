package com.knowledge.ai.calllog.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 调用统计查询条件（时间范围）。
 * <p>默认统计最近 7 天；startTime/endTime 为 yyyy-MM-dd 格式。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class AiCallStatsQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    private String startTime;
    private String endTime;
}
