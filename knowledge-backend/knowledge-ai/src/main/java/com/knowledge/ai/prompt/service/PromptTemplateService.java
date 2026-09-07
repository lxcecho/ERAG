package com.knowledge.ai.prompt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.knowledge.ai.prompt.dto.PromptTemplateQuery;
import com.knowledge.ai.prompt.dto.PromptTemplateRequest;
import com.knowledge.ai.prompt.dto.PromptTemplateVO;
import com.knowledge.ai.prompt.dto.PromptTestRequest;
import com.knowledge.ai.prompt.dto.PromptTestResult;
import com.knowledge.ai.prompt.entity.PromptTemplate;

import java.util.List;
import java.util.Map;

/**
 * Prompt 模板服务（三表：元数据 / 版本内容 / 变量定义）。
 * <p>核心模型：{@code prompt_template}（元数据，同租户一 code 一行）+ {@code prompt_version}
 * （版本内容，状态机 DRAFT→PUBLISHED→ARCHIVED）+ {@code prompt_variable}（变量定义，模板级）。
 * <ul>
 *   <li>创建：新 prompt_code，建 template + v1 DRAFT version + variable 行。</li>
 *   <li>编辑：DRAFT 原地更新；PUBLISHED/ARCHIVED 派生新 DRAFT 版本（不可变保护）。</li>
 *   <li>发布：目标转 PUBLISHED，同模板旧 PUBLISHED 转 ARCHIVED。</li>
 *   <li>回滚：基于历史版本派生新 PUBLISHED 版本（内容复刻）。</li>
 *   <li>测试：渲染变量 + 调用 LLM 预览。</li>
 * </ul>
 * 租户可见性：读取含平台预置(tenant_id=0)；写入归属当前租户。
 * <p>对外返回 {@link PromptTemplateVO}（聚合三表），保持前端兼容。
 *
 * @author: lxcechoo@gmail.com
 */
public interface PromptTemplateService extends IService<PromptTemplate> {

    /** 主列表分页：每个 prompt_code 取最新版本 */
    IPage<PromptTemplateVO> page(PromptTemplateQuery query);

    /** 某 prompt_code 的全部版本（按版本倒序） */
    List<PromptTemplateVO> listVersions(String promptCode);

    /** 版本详情 */
    PromptTemplateVO detail(Long id);

    /** 创建新 Prompt（新 prompt_code，version=1，DRAFT） */
    Long create(PromptTemplateRequest request, Long creatorId);

    /**
     * 编辑：DRAFT 原地更新；PUBLISHED/ARCHIVED 派生新 DRAFT 版本。
     * @return 最终生效的版本行ID（DRAFT 原地更新返回原 id，派生返回新 id）
     */
    Long edit(PromptTemplateRequest request, Long creatorId);

    /** 发布：目标转 PUBLISHED，同模板旧 PUBLISHED 转 ARCHIVED */
    void publish(Long id);

    /** 回滚：基于历史版本派生新 PUBLISHED 版本（内容复刻，version 递增） */
    void rollback(Long id, Long creatorId);

    /** 删除（软删单个版本行） */
    void remove(Long id);

    /** 测试：渲染模板变量 + 调用 LLM 生成预览 */
    PromptTestResult test(PromptTestRequest request);

    /** 渲染模板：{var} 命名占位符替换 */
    String render(String content, Map<String, String> variables);

    /** 按 code 取已发布版本（租户自有优先于平台预置） */
    PromptTemplateVO getPublished(String promptCode);

    /**
     * 解析 RAG 系统提示词。
     * <p>{@code ai.rag.use-db-template=true} 时取 prompt_code=rag_system_prompt 的 PUBLISHED 版本渲染；
     * 否则回退内置 {@link com.knowledge.ai.prompt.PromptTemplates#SYSTEM_TEMPLATE}。
     *
     * @param context 检索到的上下文文本
     * @return 渲染后的系统提示词
     */
    String resolveRagSystemPrompt(String context);

    /**
     * 解析普通对话系统提示词（不检索知识库场景）。
     * <p>{@code ai.rag.use-db-template=true} 时取 prompt_code=default_system 的 PUBLISHED 版本；
     * 否则回退内置 {@link com.knowledge.ai.prompt.PromptTemplates#PLAIN_SYSTEM_TEMPLATE}。
     *
     * @return 系统提示词
     */
    String resolvePlainSystemPrompt();
}
