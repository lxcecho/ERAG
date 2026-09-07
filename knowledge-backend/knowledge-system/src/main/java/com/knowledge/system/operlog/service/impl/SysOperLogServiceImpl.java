package com.knowledge.system.operlog.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.system.operlog.dto.SysOperLogQuery;
import com.knowledge.system.operlog.entity.SysOperLog;
import com.knowledge.system.operlog.mapper.SysOperLogMapper;
import com.knowledge.system.operlog.service.SysOperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务实现
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysOperLogServiceImpl extends ServiceImpl<SysOperLogMapper, SysOperLog>
        implements SysOperLogService {

    /**
     * 异步落库：由 OperLogAspect 跨 Bean 调用，经 Spring 代理触发 @Async。
     * 使用 operLogExecutor 线程池，与文档解析线程池隔离。
     */
    @Async("operLogExecutor")
    @Override
    public void saveAsync(SysOperLog operLog) {
        try {
            save(operLog);
        } catch (Exception e) {
            // 日志落库失败不影响业务，仅记录警告
            log.warn("[操作日志] 异步落库失败: {}", e.getMessage());
        }
    }

    @Override
    public IPage<SysOperLog> page(SysOperLogQuery query) {
        return lambdaQuery()
                .like(query.getTitle() != null && !query.getTitle().isBlank(),
                        SysOperLog::getTitle, query.getTitle())
                .eq(query.getBusinessType() != null, SysOperLog::getBusinessType, query.getBusinessType())
                .eq(query.getStatus() != null, SysOperLog::getStatus, query.getStatus())
                .like(query.getOperUser() != null && !query.getOperUser().isBlank(),
                        SysOperLog::getOperUser, query.getOperUser())
                .orderByDesc(SysOperLog::getCreateTime)
                .page(query.toPage());
    }
}
