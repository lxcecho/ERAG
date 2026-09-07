package com.knowledge.ai.service;

import com.knowledge.ai.dto.DashboardStatsVo;

/**
 * 工作台统计服务：聚合知识库 / 文档 / 切片 / 今日对话四项指标。
 *
 * @author: lxcechoo@gmail.com
 */
public interface DashboardStatsService {

    /** 工作台四项统计 */
    DashboardStatsVo stats();
}
