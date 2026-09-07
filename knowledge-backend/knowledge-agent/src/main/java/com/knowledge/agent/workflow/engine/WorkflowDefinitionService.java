package com.knowledge.agent.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.agent.workflow.definition.WorkflowDefinitionModel;
import com.knowledge.agent.workflow.entity.WorkflowDefinition;
import com.knowledge.agent.workflow.mapper.WorkflowDefinitionMapper;
import com.knowledge.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 流程定义服务：定义的 CRUD + JSON 模型解析。
 * <p>预置流程（如 policy_analysis）由 {@code WorkflowDefinitionInitializer} 在启动时幂等注册。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowDefinitionService {

    private final WorkflowDefinitionMapper definitionMapper;
    private final ObjectMapper objectMapper;

    public WorkflowDefinition getById(Long id) {
        WorkflowDefinition def = definitionMapper.selectById(id);
        if (def == null) {
            throw new BizException("流程定义不存在: " + id);
        }
        return def;
    }

    /** 按编码取最新启用版本 */
    public WorkflowDefinition findEnabledByCode(Long tenantId, String code) {
        return definitionMapper.selectOne(new LambdaQueryWrapper<WorkflowDefinition>()
                .eq(WorkflowDefinition::getTenantId, tenantId)
                .eq(WorkflowDefinition::getCode, code)
                .eq(WorkflowDefinition::getStatus, "ENABLED")
                .orderByDesc(WorkflowDefinition::getVersion)
                .last("LIMIT 1"));
    }

    public List<WorkflowDefinition> listByTenant(Long tenantId) {
        return definitionMapper.selectList(new LambdaQueryWrapper<WorkflowDefinition>()
                .eq(WorkflowDefinition::getTenantId, tenantId)
                .orderByDesc(WorkflowDefinition::getCreateTime));
    }

    /** 保存（新增或更新） */
    public WorkflowDefinition save(WorkflowDefinition def) {
        if (def.getId() == null) {
            definitionMapper.insert(def);
        } else {
            definitionMapper.updateById(def);
        }
        return def;
    }

    /** 判断是否已存在同 code+version（幂等注册用） */
    public boolean exists(Long tenantId, String code, Integer version) {
        return definitionMapper.selectCount(new LambdaQueryWrapper<WorkflowDefinition>()
                .eq(WorkflowDefinition::getTenantId, tenantId)
                .eq(WorkflowDefinition::getCode, code)
                .eq(WorkflowDefinition::getVersion, version)) > 0;
    }

    /** 解析定义 JSON 为模型 */
    public WorkflowDefinitionModel toModel(WorkflowDefinition def) {
        try {
            WorkflowDefinitionModel model = objectMapper.readValue(
                    def.getDefinition(), WorkflowDefinitionModel.class);
            if (model.getCode() == null) {
                model.setCode(def.getCode());
            }
            if (model.getName() == null) {
                model.setName(def.getName());
            }
            return model;
        } catch (JsonProcessingException e) {
            throw new BizException("流程定义 JSON 解析失败 def=" + def.getId() + ": " + e.getMessage());
        }
    }

    /** 模型序列化为 JSON（保存定义用） */
    public String toJson(WorkflowDefinitionModel model) {
        try {
            return objectMapper.writeValueAsString(model);
        } catch (JsonProcessingException e) {
            throw new BizException("流程定义序列化失败: " + e.getMessage());
        }
    }
}
