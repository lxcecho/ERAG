package com.knowledge.agent.tool;

import com.knowledge.agent.entity.ToolMetadata;
import com.knowledge.agent.entity.ToolPermission;
import com.knowledge.agent.entity.ToolVersion;
import com.knowledge.agent.mapper.ToolMetadataMapper;
import com.knowledge.agent.mapper.ToolPermissionMapper;
import com.knowledge.agent.mapper.ToolVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 工具元数据服务：Tool Registry 三表的统一管理入口。
 * <p>职责：
 * <ul>
 *   <li>元数据 CRUD + 租户继承查询（租户自有优先于平台预置 tenant_id=0）；</li>
 *   <li>版本发布（DRAFT→PUBLISHED，旧版自动 ARCHIVED）+ 版本历史查询；</li>
 *   <li>工具启用判定 + 角色授权判定（默认开放：无授权记录则放行）。</li>
 * </ul>
 * <p>租户可见性查询用 {@code @InterceptorIgnore} 关闭 MP 租户拦截器，手工注入
 * {@code (tenant_id = #{tenantId} OR tenant_id = 0)} 条件（与 prompt 模块一致）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolMetadataService {

    private final ToolMetadataMapper metadataMapper;
    private final ToolVersionMapper versionMapper;
    private final ToolPermissionMapper permissionMapper;
    private final ToolUserRolesResolver rolesResolver;

    /** 平台预置租户ID */
    private static final long PLATFORM_TENANT_ID = 0L;

    /**
     * 获取工具元数据（租户继承：租户自有优先，无则取平台预置）。
     *
     * @return 元数据；租户与平台均无记录时返回 null（调用方降级为默认）
     */
    public ToolMetadata getMetadata(Long tenantId, String toolName) {
        return metadataMapper.selectVisibleByTenantAndName(tenantId, toolName);
    }

    /** 列出租户可见的全部工具元数据（租户自有覆盖平台预置，按 tool_name 去重）。 */
    public List<ToolMetadata> listByTenant(Long tenantId) {
        return metadataMapper.listVisibleByTenant(tenantId);
    }

    /** 列出工具的全部版本（租户可见，含平台预置），按版本倒序。 */
    public List<ToolVersion> listVersions(Long tenantId, String toolName) {
        return versionMapper.listVisibleByTenantAndName(tenantId, toolName);
    }

    /**
     * 注册/更新工具元数据（upsert by tenant+name）。
     * <p>同租户同工具名已存在则更新可配置字段，否则插入新行。
     */
    public void register(ToolMetadata meta) {
        ToolMetadata existing = metadataMapper.selectExactByTenantAndName(
                meta.getTenantId(), meta.getToolName());
        if (existing != null) {
            existing.setDisplayName(meta.getDisplayName());
            existing.setDescription(meta.getDescription());
            existing.setCategory(meta.getCategory());
            if (meta.getAuthRequired() != null) existing.setAuthRequired(meta.getAuthRequired());
            if (meta.getTimeoutMs() != null) existing.setTimeoutMs(meta.getTimeoutMs());
            if (meta.getEnabled() != null) existing.setEnabled(meta.getEnabled());
            if (meta.getIcon() != null) existing.setIcon(meta.getIcon());
            if (meta.getSortOrder() != null) existing.setSortOrder(meta.getSortOrder());
            metadataMapper.updateById(existing);
            log.info("[ToolMeta] 更新工具元数据 tenant={} tool={}", meta.getTenantId(), meta.getToolName());
        } else {
            if (meta.getEnabled() == null) meta.setEnabled(1);
            if (meta.getAuthRequired() == null) meta.setAuthRequired(0);
            if (meta.getDeleted() == null) meta.setDeleted(0);
            if (meta.getCurrentVersion() == null) meta.setCurrentVersion("1.0.0");
            metadataMapper.insert(meta);
            log.info("[ToolMeta] 新增工具元数据 tenant={} tool={}", meta.getTenantId(), meta.getToolName());
        }
    }

    /**
     * 发布工具新版本：归档同租户旧 PUBLISHED → 插入新 PUBLISHED 版本 → 更新元数据 current_version。
     * <p>事务保证三步原子；元数据行不存在时仅告警（current_version 更新 0 行），版本记录仍写入。
     */
    @Transactional(rollbackFor = Exception.class)
    public void publishVersion(Long tenantId, String toolName, String version,
                                String changelog, String schemaSnapshot) {
        // 1. 归档同租户旧 PUBLISHED 版本
        int archived = versionMapper.archivePublished(tenantId, toolName);
        // 2. 插入新 PUBLISHED 版本
        ToolVersion v = new ToolVersion();
        v.setTenantId(tenantId);
        v.setToolName(toolName);
        v.setVersion(version);
        v.setStatus("PUBLISHED");
        v.setChangelog(changelog == null ? "" : changelog);
        v.setSchemaSnapshot(schemaSnapshot);
        v.setDeleted(0);
        versionMapper.insert(v);
        // 3. 更新元数据 current_version（精确租户匹配）
        int updated = metadataMapper.updateCurrentVersion(tenantId, toolName, version);
        if (updated == 0) {
            log.warn("[ToolMeta] 发布版本时未找到元数据行 tenant={} tool={}（current_version 未更新）",
                    tenantId, toolName);
        }
        log.info("[ToolMeta] 发布版本 tenant={} tool={} version={} archivedOld={}",
                tenantId, toolName, version, archived);
    }

    /** 设置工具启用状态（精确租户匹配，平台预置 tenant=0 由平台管理员设置）。 */
    public void setEnabled(Long tenantId, String toolName, boolean enabled) {
        int updated = metadataMapper.updateEnabled(tenantId, toolName, enabled ? 1 : 0);
        log.info("[ToolMeta] 设置启用 tenant={} tool={} enabled={} updated={}",
                tenantId, toolName, enabled, updated);
    }

    /**
     * 判定工具是否启用（null 元数据 → 默认启用）。
     */
    public boolean isEnabled(Long tenantId, String toolName) {
        ToolMetadata meta = getMetadata(tenantId, toolName);
        return meta == null || (meta.getEnabled() != null && meta.getEnabled() == 1);
    }

    /**
     * 判定用户是否有工具调用权限（默认开放模型）。
     * <p>规则：
     * <ul>
     *   <li>无 tool_permission 记录 → 放行（默认开放，避免破坏现有行为）；</li>
     *   <li>有记录 → 要求命中 ALLOW(enabled=1)：租户级(subject_type='T') 或
     *       角色级(subject_type='R' 且 subject_id 在用户角色集内)；否则拒绝。</li>
     * </ul>
     */
    public boolean isGranted(Long tenantId, Long userId, String toolName) {
        List<ToolPermission> records = permissionMapper.selectByTenantAndTool(tenantId, toolName);
        if (records == null || records.isEmpty()) {
            return true; // 默认开放
        }
        Set<Long> roleIds = rolesResolver.resolveRoleIds(tenantId, userId);
        boolean allowed = records.stream().anyMatch(p ->
                p.getEnabled() != null && p.getEnabled() == 1 && (
                        "T".equals(p.getSubjectType())
                                || ("R".equals(p.getSubjectType()) && roleIds.contains(p.getSubjectId()))
                ));
        if (!allowed) {
            log.debug("[ToolPerm] 拒绝访问 tenant={} user={} tool={} records={} roles={}",
                    tenantId, userId, toolName, records.size(), roleIds);
        }
        return allowed;
    }
}
