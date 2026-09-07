package com.knowledge.agent.custom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.agent.custom.dto.AgentDefinitionRequest;
import com.knowledge.agent.custom.dto.AgentDefinitionVo;
import com.knowledge.agent.custom.dto.StepDefinition;
import com.knowledge.agent.custom.entity.AgentDefinition;
import com.knowledge.agent.custom.mapper.AgentDefinitionMapper;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.service.KbPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 自定义 Agent 定义服务：CRUD + 发布 + 权限控制。
 * <p>权限规则：创建者（user_id == currentUserId）可管理自己的定义；
 * 查询时额外可见租户内已发布（PUBLISHED）定义（供参考，运行需复制为自己的）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDefinitionService {

    private static final Set<String> SOURCE_MODES = Set.of("kb", "content", "log");
    private static final Set<String> EXEC_MODES = Set.of("single", "multi");
    private static final Set<String> INPUT_FROM = Set.of("context", "prev");

    private final AgentDefinitionMapper definitionMapper;
    private final ObjectMapper objectMapper;
    private final KbPermissionService kbPermissionService;

    /** 创建定义（owner=当前用户，status=DRAFT） */
    public Long create(AgentDefinitionRequest req, Long userId) {
        // 数据源缺省 kb（运行时由对话框 inputType 覆盖：文件=log / 开关开=kb / 开关关=plain）
        if (req.getSourceMode() == null || req.getSourceMode().isBlank()) {
            req.setSourceMode("kb");
        }
        validate(req, userId);
        AgentDefinition def = new AgentDefinition();
        BeanUtils.copyProperties(req, def);
        def.setTenantId(TenantContext.requiredTenantId());
        def.setUserId(userId);
        def.setStatus("DRAFT");
        definitionMapper.insert(def);
        log.info("[自定义Agent] 用户={} 创建定义={} mode={}/{}", userId, def.getId(), req.getSourceMode(), req.getExecMode());
        return def.getId();
    }

    /** 编辑定义（仅创建者；PUBLISHED 修改后回落 DRAFT 重新发布） */
    public Long edit(AgentDefinitionRequest req, Long userId) {
        if (req.getId() == null) {
            throw new BizException("定义ID不能为空");
        }
        AgentDefinition def = getOwned(req.getId(), userId);
        validate(req, userId);
        BeanUtils.copyProperties(req, def, "id", "tenantId", "userId", "status", "deleted", "createTime", "updateTime");
        // 已发布内容被修改 → 回落到草稿，需重新发布才生效
        if ("PUBLISHED".equals(def.getStatus())) {
            def.setStatus("DRAFT");
        }
        definitionMapper.updateById(def);
        return def.getId();
    }

    /** 发布（仅创建者） */
    public void publish(Long id, Long userId) {
        AgentDefinition def = getOwned(id, userId);
        def.setStatus("PUBLISHED");
        definitionMapper.updateById(def);
    }

    /** 删除（仅创建者，软删） */
    public void delete(Long id, Long userId) {
        getOwned(id, userId);
        definitionMapper.deleteById(id);
    }

    /** 分页：创建者自己的全部 + 租户内已发布 */
    public IPage<AgentDefinitionVo> page(long current, long size, Long userId) {
        Long tenantId = TenantContext.requiredTenantId();
        LambdaQueryWrapper<AgentDefinition> wrapper = new LambdaQueryWrapper<AgentDefinition>()
                .eq(AgentDefinition::getTenantId, tenantId)
                .and(w -> w.eq(AgentDefinition::getUserId, userId)
                        .or().eq(AgentDefinition::getStatus, "PUBLISHED"))
                .orderByDesc(AgentDefinition::getCreateTime);
        Page<AgentDefinition> page = new Page<>(current, size);
        IPage<AgentDefinition> result = definitionMapper.selectPage(page, wrapper);
        return result.convert(this::toVo);
    }

    /** 详情 */
    public AgentDefinitionVo detail(Long id, Long userId) {
        AgentDefinition def = definitionMapper.selectById(id);
        if (def == null) {
            throw new BizException("自定义 Agent 不存在");
        }
        // 可见性：租户内自己创建或已发布
        boolean visible = def.getUserId().equals(userId) || "PUBLISHED".equals(def.getStatus());
        if (!visible) {
            throw new BizException(403, "无权查看该自定义 Agent");
        }
        return toVo(def);
    }

    /** 批量查询 Agent 名称（运行记录列表填充用） */
    public Map<Long, String> listNames(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<AgentDefinition> wrapper = new LambdaQueryWrapper<AgentDefinition>()
                .select(AgentDefinition::getId, AgentDefinition::getName)
                .in(AgentDefinition::getId, ids);
        return definitionMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(AgentDefinition::getId, AgentDefinition::getName));
    }

    /** 静默查询名称（详情用，查不到返回 null） */
    public String getByIdQuiet(Long id) {
        AgentDefinition def = definitionMapper.selectById(id);
        return def == null ? null : def.getName();
    }

    /** 运行前取定义并校验归属（仅创建者可运行） */
    public AgentDefinition getOwnedForRun(Long id, Long userId) {
        return getOwned(id, userId);
    }

    /** 获取创建者自己的定义（编辑/删除/发布/运行前置校验） */
    private AgentDefinition getOwned(Long id, Long userId) {
        AgentDefinition def = definitionMapper.selectById(id);
        if (def == null) {
            throw new BizException("自定义 Agent 不存在");
        }
        if (!def.getUserId().equals(userId)) {
            throw new BizException(403, "仅创建者可操作该自定义 Agent");
        }
        return def;
    }

    /** 业务校验：模式合法 + 依赖字段齐全 + 多步步骤 JSON 合法 */
    private void validate(AgentDefinitionRequest req, Long userId) {
        if (!SOURCE_MODES.contains(req.getSourceMode())) {
            throw new BizException("数据源模式非法：" + req.getSourceMode());
        }
        if (!EXEC_MODES.contains(req.getExecMode())) {
            throw new BizException("执行模型非法：" + req.getExecMode());
        }
        if ("kb".equals(req.getSourceMode())) {
            // 默认知识库可选（对话框检索开关打开时可随时切换其他知识库）
            if (req.getKbId() != null) {
                kbPermissionService.checkViewer(req.getKbId(), userId);
            }
        }
        if ("multi".equals(req.getExecMode())) {
            if (req.getSteps() == null || req.getSteps().isBlank()) {
                throw new BizException("多步骤流程必须配置 steps");
            }
            validateSteps(req.getSteps());
        }
    }

    /** 校验多步流程 JSON：结构合法 + 步骤数上限 + outputKey 唯一 + inputFrom 合法 */
    private void validateSteps(String stepsJson) {
        List<StepDefinition> steps;
        try {
            steps = objectMapper.readValue(stepsJson, new TypeReference<List<StepDefinition>>() {
            });
        } catch (Exception e) {
            throw new BizException("多步骤流程 JSON 格式非法: " + e.getMessage());
        }
        if (steps == null || steps.isEmpty()) {
            throw new BizException("多步骤流程至少需要 1 个步骤");
        }
        int maxSteps = 5;
        if (steps.size() > maxSteps) {
            throw new BizException("多步骤流程步骤数上限 " + maxSteps);
        }
        Set<String> keys = new java.util.HashSet<>();
        for (StepDefinition step : steps) {
            if (step.getStepName() == null || step.getStepName().isBlank()) {
                throw new BizException("步骤名称不能为空");
            }
            if (step.getPrompt() == null || step.getPrompt().isBlank()) {
                throw new BizException("步骤 " + step.getStepName() + " 提示词不能为空");
            }
            if (!INPUT_FROM.contains(step.getInputFrom())) {
                throw new BizException("步骤 " + step.getStepName() + " inputFrom 非法（context/prev）");
            }
            if (!keys.add(step.getOutputKey())) {
                throw new BizException("产物 key 重复：" + step.getOutputKey());
            }
        }
    }

    private AgentDefinitionVo toVo(AgentDefinition def) {
        AgentDefinitionVo vo = new AgentDefinitionVo();
        BeanUtils.copyProperties(def, vo);
        return vo;
    }
}
