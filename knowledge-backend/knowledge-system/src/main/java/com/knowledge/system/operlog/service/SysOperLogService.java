package com.knowledge.system.operlog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.knowledge.system.operlog.dto.SysOperLogQuery;
import com.knowledge.system.operlog.entity.SysOperLog;

/**
 * 操作日志服务接口
 *
 * @author: lxcechoo@gmail.com
 */
public interface SysOperLogService extends IService<SysOperLog> {

    /**
     * 异步保存操作日志（在 operLogExecutor 线程池执行，不影响主请求）。
     * <p>必须由 Spring 代理调用（跨 Bean），避免 @Async 自调用失效。
     *
     * @param operLog 日志记录
     */
    void saveAsync(SysOperLog operLog);

    /** 分页查询操作日志 */
    IPage<SysOperLog> page(SysOperLogQuery query);
}
