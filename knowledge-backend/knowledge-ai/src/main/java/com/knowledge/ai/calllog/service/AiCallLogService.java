package com.knowledge.ai.calllog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.ai.calllog.dto.AiCallLogQuery;
import com.knowledge.ai.calllog.dto.AiCallStatsQuery;
import com.knowledge.ai.calllog.dto.AiCallStatsVo;
import com.knowledge.ai.calllog.entity.AiCallLog;

/**
 * AI 调用日志服务：提供日志分页查询与运营统计聚合。
 *
 * @author: lxcechoo@gmail.com
 */
public interface AiCallLogService {

    /** 分页查询调用日志 */
    IPage<AiCallLog> page(AiCallLogQuery query);

    /** 运营统计：概览 + 按模型/用户/日期排行 */
    AiCallStatsVo stats(AiCallStatsQuery query);
}
