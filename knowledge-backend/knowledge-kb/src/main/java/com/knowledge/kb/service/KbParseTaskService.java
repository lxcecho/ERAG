package com.knowledge.kb.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.knowledge.kb.dto.ParseTaskVo;
import com.knowledge.kb.dto.TaskDetailVo;
import com.knowledge.kb.dto.TaskQuery;
import com.knowledge.kb.entity.KbParseTask;

/**
 * 文档解析任务服务接口
 *
 * @author: lxcechoo@gmail.com
 */
public interface KbParseTaskService extends IService<KbParseTask> {

    /** 为文档创建一条"待处理"解析任务 */
    KbParseTask createTask(Long documentId, Long kbId, Long userId);

    /**
     * 异步执行解析任务：发布事件由 ai 模块监听执行 RAG 入库流程。
     */
    void process(Long taskId);

    /** 分页查询某知识库的解析任务（联查文档名） */
    IPage<ParseTaskVo> page(Long kbId, Integer pageNo, Integer pageSize);

    /** 分页查询（支持状态过滤） */
    IPage<ParseTaskVo> page(TaskQuery query);

    /** 任务详情（联查文档信息） */
    TaskDetailVo getDetail(Long taskId);

    /** 重试失败任务：重置状态为待处理后重新派发 */
    void retry(Long taskId);

    /**
     * 重置任务为待处理状态并同步文档状态为待解析（保留 retry_count 和 error_msg）。
     * <p>供手动重试、补偿任务恢复复用，保证任务与文档状态一致，避免"任务待处理但文档解析失败"的不一致。
     * <p>注意：retry_count 和 error_msg 由调用方按需重置（retry 重置为 0/空，补偿任务保留累计）。
     */
    void resetToPending(Long taskId);

    /**
     * 幂等闸门：条件更新 PENDING/FAILED→PROCESSING 并 retry_count+1。
     * <p>消费前置校验，防止并发/重复投递导致重复解析。影响行数=0 表示状态非 PENDING/FAILED
     * （已处理/处理中），消费端应 ack 丢弃。
     *
     * @return true 表示成功抢占到任务（可继续处理）；false 表示已被处理/并发，应跳过
     */
    boolean markProcessingIfPending(Long taskId);

    /** 标记任务成功并更新文档状态为已解析（含 chunkCount） */
    void markSuccess(Long taskId, int chunkCount);

    /** 标记任务失败并更新文档状态为解析失败（含错误信息） */
    void markFailed(Long taskId, String errorMsg);

    /**
     * 标记任务重试耗尽失败（DLQ 消费者调用）。
     * <p>将 retry_count 置为 maxRetries，防止补偿任务无限重投（selectRecoverableFailedTasks 用
     * retry_count < maxRetries 过滤，重试耗尽的任务不再自动恢复）。
     */
    void markFailedExhausted(Long taskId, String errorMsg);
}
