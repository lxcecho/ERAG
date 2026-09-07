package com.knowledge.ai.prompt.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.ai.calllog.service.AiCallLogger;
import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.prompt.PromptTemplates;
import com.knowledge.ai.prompt.dto.PromptTemplateQuery;
import com.knowledge.ai.prompt.dto.PromptTemplateRequest;
import com.knowledge.ai.prompt.dto.PromptTemplateVO;
import com.knowledge.ai.prompt.dto.PromptTestRequest;
import com.knowledge.ai.prompt.dto.PromptTestResult;
import com.knowledge.ai.prompt.entity.PromptTemplate;
import com.knowledge.ai.prompt.entity.PromptVariable;
import com.knowledge.ai.prompt.entity.PromptVersion;
import com.knowledge.ai.prompt.enums.PromptStatus;
import com.knowledge.ai.prompt.mapper.PromptTemplateMapper;
import com.knowledge.ai.prompt.mapper.PromptVariableMapper;
import com.knowledge.ai.prompt.mapper.PromptVersionMapper;
import com.knowledge.ai.prompt.service.PromptTemplateService;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Prompt 模板服务实现（三表：元数据 / 版本内容 / 变量定义）。
 * <p>三表协作：
 * <ul>
 *   <li>{@code prompt_template}：元数据（tenant_id/prompt_code/name/type），同租户一 code 一行。</li>
 *   <li>{@code prompt_version}：版本内容（content/status/remark），状态机 DRAFT→PUBLISHED→ARCHIVED。</li>
 *   <li>{@code prompt_variable}：变量定义（var_name），模板级，聚合为逗号字符串兼容前端。</li>
 * </ul>
 * <p>对外返回 {@link PromptTemplateVO}（聚合三表），id=版本行ID，保持前端兼容。
 * <p>租户可见性：读取含平台预置(tenant_id=0)；写入归属当前租户，平台预置模板不可被租户
 * 直接修改/删除，编辑/回滚平台模板将派生当前租户的自有 template + version。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl extends ServiceImpl<PromptTemplateMapper, PromptTemplate>
        implements PromptTemplateService {

    private final PromptVersionMapper versionMapper;
    private final PromptVariableMapper variableMapper;
    private final AiProperties aiProperties;
    private final ChatModel chatModel;
    private final AiCallLogger aiCallLogger;

    /** 当前租户ID（未登录/无上下文时回退 0=平台视角，仅可见平台预置） */
    private static Long currentTenantId() {
        Long id = TenantContext.getTenantId();
        return id == null ? TenantContext.PLATFORM_TENANT_ID : id;
    }

    @Override
    public IPage<PromptTemplateVO> page(PromptTemplateQuery query) {
        return baseMapper.pageLatestPerCode(query.toPage(), currentTenantId(), query);
    }

    @Override
    public List<PromptTemplateVO> listVersions(String promptCode) {
        if (promptCode == null || promptCode.isBlank()) {
            throw new BizException("promptCode 不能为空");
        }
        return baseMapper.listVersionsByCode(currentTenantId(), promptCode);
    }

    @Override
    @Cacheable(value = "prompt", key = "'detail:' + #id + ':' + T(com.knowledge.common.context.TenantContext).getTenantId()",
            unless = "#result == null")
    public PromptTemplateVO detail(Long id) {
        PromptTemplateVO vo = baseMapper.findVoByIdVisible(currentTenantId(), id);
        if (vo == null) {
            throw new BizException("模板不存在或无权访问");
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "prompt", allEntries = true)
    public Long create(PromptTemplateRequest request, Long creatorId) {
        Long tenantId = currentTenantId();
        // 同租户同 code 已存在则禁止重复创建（改用编辑派生新版本）
        if (baseMapper.findByCode(tenantId, request.getPromptCode()) != null) {
            throw new BizException("promptCode 已存在，请使用编辑派生新版本");
        }
        // 1) 元数据主表
        PromptTemplate template = new PromptTemplate();
        template.setTenantId(tenantId);
        template.setPromptCode(request.getPromptCode());
        template.setName(request.getName());
        template.setType(request.getType());
        template.setCreatorId(creatorId);
        baseMapper.insert(template);
        // 2) 版本内容表（v1 草稿）
        PromptVersion version = new PromptVersion();
        version.setTemplateId(template.getId());
        version.setVersion(1);
        version.setContent(request.getContent());
        version.setStatus(PromptStatus.DRAFT.name());
        version.setRemark(request.getRemark() != null ? request.getRemark() : "");
        version.setCreatorId(creatorId);
        versionMapper.insert(version);
        // 3) 变量定义表
        replaceVariables(template.getId(), request.getVariables());
        log.info("[Prompt模板] 新建 code={} name={} v1 template={} version={} tenant={}",
                template.getPromptCode(), template.getName(), template.getId(), version.getId(), tenantId);
        return version.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "prompt", allEntries = true)
    public Long edit(PromptTemplateRequest request, Long creatorId) {
        if (request.getId() == null) {
            throw new BizException("编辑需传入版本ID");
        }
        Long tenantId = currentTenantId();
        PromptTemplateVO target = baseMapper.findVoByIdVisible(tenantId, request.getId());
        if (target == null) {
            throw new BizException("模板不存在或无权访问");
        }
        PromptStatus status = PromptStatus.valueOf(target.getStatus());
        // DRAFT 且归属当前租户：原地更新 version + template 元数据 + 替换变量
        if (status == PromptStatus.DRAFT && tenantId.equals(target.getTenantId())) {
            PromptVersion version = versionMapper.selectById(request.getId());
            version.setContent(request.getContent());
            if (request.getRemark() != null) {
                version.setRemark(request.getRemark());
            }
            versionMapper.updateById(version);
            // 同步更新模板元数据 name/type
            PromptTemplate template = baseMapper.selectById(target.getTemplateId());
            template.setName(request.getName());
            template.setType(request.getType());
            baseMapper.updateById(template);
            replaceVariables(target.getTemplateId(), request.getVariables());
            log.info("[Prompt模板] 编辑(DRAFT原地) versionId={} code={}", request.getId(), target.getPromptCode());
            return version.getId();
        }
        // PUBLISHED/ARCHIVED 或平台预置：派生新 DRAFT 版本（不可变保护）
        PromptTemplate tenantTemplate = baseMapper.findByCode(tenantId, target.getPromptCode());
        if (tenantTemplate == null) {
            // 平台预置：派生当前租户的自有 template
            tenantTemplate = new PromptTemplate();
            tenantTemplate.setTenantId(tenantId);
            tenantTemplate.setPromptCode(target.getPromptCode());
            tenantTemplate.setName(request.getName());
            tenantTemplate.setType(request.getType());
            tenantTemplate.setCreatorId(creatorId);
            baseMapper.insert(tenantTemplate);
        } else {
            // 租户已有 template：同步 name/type
            tenantTemplate.setName(request.getName());
            tenantTemplate.setType(request.getType());
            baseMapper.updateById(tenantTemplate);
        }
        PromptVersion derived = new PromptVersion();
        derived.setTemplateId(tenantTemplate.getId());
        derived.setVersion(nextVersion(tenantTemplate.getId()));
        derived.setContent(request.getContent());
        derived.setStatus(PromptStatus.DRAFT.name());
        derived.setRemark(request.getRemark() != null ? request.getRemark() : "派生自 v" + target.getVersion());
        derived.setCreatorId(creatorId);
        versionMapper.insert(derived);
        replaceVariables(tenantTemplate.getId(), request.getVariables());
        log.info("[Prompt模板] 编辑(派生新版本) code={} newV={} fromV={} template={}",
                target.getPromptCode(), derived.getVersion(), target.getVersion(), tenantTemplate.getId());
        return derived.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "prompt", allEntries = true)
    public void publish(Long id) {
        Long tenantId = currentTenantId();
        PromptTemplateVO target = baseMapper.findVoByIdVisible(tenantId, id);
        if (target == null) {
            throw new BizException("模板不存在或无权访问");
        }
        if (!tenantId.equals(target.getTenantId())) {
            throw new BizException("不能发布平台预置或其他租户的模板，请先派生自有版本");
        }
        if (PromptStatus.PUBLISHED.name().equals(target.getStatus())) {
            return; // 已发布，幂等
        }
        // 同模板旧 PUBLISHED 转 ARCHIVED，保证唯一
        versionMapper.archivePublished(target.getTemplateId());
        PromptVersion version = versionMapper.selectById(id);
        version.setStatus(PromptStatus.PUBLISHED.name());
        versionMapper.updateById(version);
        log.info("[Prompt模板] 发布 code={} v={} versionId={}", target.getPromptCode(), target.getVersion(), id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "prompt", allEntries = true)
    public void rollback(Long id, Long creatorId) {
        Long tenantId = currentTenantId();
        PromptTemplateVO target = baseMapper.findVoByIdVisible(tenantId, id);
        if (target == null) {
            throw new BizException("模板不存在或无权访问");
        }
        // 派生新 PUBLISHED 版本，内容复刻自目标历史版本；始终归属当前租户
        PromptTemplate tenantTemplate = baseMapper.findByCode(tenantId, target.getPromptCode());
        if (tenantTemplate == null) {
            // 平台预置回滚：派生当前租户的自有 template
            tenantTemplate = new PromptTemplate();
            tenantTemplate.setTenantId(tenantId);
            tenantTemplate.setPromptCode(target.getPromptCode());
            tenantTemplate.setName(target.getName());
            tenantTemplate.setType(target.getType());
            tenantTemplate.setCreatorId(creatorId);
            baseMapper.insert(tenantTemplate);
        }
        PromptVersion derived = new PromptVersion();
        derived.setTemplateId(tenantTemplate.getId());
        derived.setVersion(nextVersion(tenantTemplate.getId()));
        derived.setContent(target.getContent());
        derived.setStatus(PromptStatus.PUBLISHED.name());
        derived.setRemark("回滚自 v" + target.getVersion());
        derived.setCreatorId(creatorId);
        // 先归档同模板旧 PUBLISHED，再保存新版本
        versionMapper.archivePublished(tenantTemplate.getId());
        versionMapper.insert(derived);
        log.info("[Prompt模板] 回滚 code={} newV={} fromV={} template={}",
                target.getPromptCode(), derived.getVersion(), target.getVersion(), tenantTemplate.getId());
    }

    @Override
    @CacheEvict(value = "prompt", allEntries = true)
    public void remove(Long id) {
        Long tenantId = currentTenantId();
        PromptTemplateVO target = baseMapper.findVoByIdVisible(tenantId, id);
        if (target == null) {
            throw new BizException("模板不存在或无权访问");
        }
        if (!tenantId.equals(target.getTenantId())) {
            throw new BizException("不能删除平台预置或其他租户的模板");
        }
        versionMapper.deleteById(id);
        log.info("[Prompt模板] 删除 versionId={} code={} v={}", id, target.getPromptCode(), target.getVersion());
    }

    @Override
    public PromptTestResult test(PromptTestRequest request) {
        String content = request.getContent();
        // promptCode 非空时优先加载 DB 已发布模板内容
        if (request.getPromptCode() != null && !request.getPromptCode().isBlank()) {
            PromptTemplateVO published = baseMapper.getPublishedByCode(currentTenantId(), request.getPromptCode());
            if (published != null && published.getContent() != null) {
                content = published.getContent();
            }
        }
        if (content == null || content.isBlank()) {
            throw new BizException("测试内容不能为空（promptCode 与 content 至少传一个）");
        }
        String rendered = PromptTemplates.render(content, request.getVariables());
        AiCallLogger.Tracer tracer = aiCallLogger.trace("prompt_test", "CHAT",
                aiProperties.getLlm().getModelName());
        try {
            ChatResponse resp = chatModel.chat(UserMessage.from(rendered));
            String output = resp.aiMessage().text();
            TokenUsage usage = resp.tokenUsage();
            int promptTokens = usage == null || usage.inputTokenCount() == null ? 0 : usage.inputTokenCount();
            int completionTokens = usage == null || usage.outputTokenCount() == null ? 0 : usage.outputTokenCount();
            tracer.success(promptTokens, completionTokens);
            int tokens = promptTokens + completionTokens;
            log.info("[Prompt模板] 测试 renderedLen={} outputLen={} tokens={}",
                    rendered.length(), output == null ? 0 : output.length(), tokens);
            return new PromptTestResult(rendered, output == null ? "" : output, tokens);
        } catch (RuntimeException e) {
            tracer.failure(e);
            throw e;
        }
    }

    @Override
    public String render(String content, Map<String, String> variables) {
        return PromptTemplates.render(content, variables);
    }

    @Override
    public PromptTemplateVO getPublished(String promptCode) {
        return baseMapper.getPublishedByCode(currentTenantId(), promptCode);
    }

    @Override
    public String resolveRagSystemPrompt(String context) {
        // 未启用 DB 模板，回退内置默认模板
        if (!aiProperties.getRag().isUseDbTemplate()) {
            return PromptTemplates.render(PromptTemplates.SYSTEM_TEMPLATE, Map.of("context", context));
        }
        // 启用 DB 模板：取 prompt_code=rag_system_prompt 的 PUBLISHED 版本
        PromptTemplateVO template = baseMapper.getPublishedByCode(currentTenantId(), "rag_system_prompt");
        if (template == null || template.getContent() == null || template.getContent().isBlank()) {
            log.warn("[Prompt模板] 未找到已发布的 rag_system_prompt，回退内置默认模板");
            return PromptTemplates.render(PromptTemplates.SYSTEM_TEMPLATE, Map.of("context", context));
        }
        return PromptTemplates.render(template.getContent(), Map.of("context", context));
    }

    @Override
    public String resolvePlainSystemPrompt() {
        // 未启用 DB 模板，回退内置普通对话模板
        if (!aiProperties.getRag().isUseDbTemplate()) {
            return PromptTemplates.PLAIN_SYSTEM_TEMPLATE;
        }
        // 启用 DB 模板：取 prompt_code=default_system 的 PUBLISHED 版本
        PromptTemplateVO template = baseMapper.getPublishedByCode(currentTenantId(), "default_system");
        if (template == null || template.getContent() == null || template.getContent().isBlank()) {
            log.warn("[Prompt模板] 未找到已发布的 default_system，回退内置普通对话模板");
            return PromptTemplates.PLAIN_SYSTEM_TEMPLATE;
        }
        return template.getContent();
    }

    // ==================== 内部辅助方法 ====================

    /** 取某模板的下一个版本号（同模板内 max(version)+1） */
    private int nextVersion(Long templateId) {
        Integer max = versionMapper.maxVersionByTemplateId(templateId);
        return (max == null ? 0 : max) + 1;
    }

    /**
     * 替换某模板的变量定义：先物理删除旧变量，再按逗号字符串拆分插入新变量。
     * <p>variablesStr 为空时仅清空不插入（即该模板无变量）。
     */
    private void replaceVariables(Long templateId, String variablesStr) {
        variableMapper.deleteByTemplateId(templateId);
        if (variablesStr == null || variablesStr.isBlank()) {
            return;
        }
        for (String raw : variablesStr.split(",")) {
            String varName = raw.trim();
            if (varName.isEmpty()) {
                continue;
            }
            PromptVariable variable = new PromptVariable();
            variable.setTemplateId(templateId);
            variable.setVarName(varName);
            variable.setDescription("");
            variable.setRequired(1);
            variable.setDefaultValue("");
            variableMapper.insert(variable);
        }
    }
}
