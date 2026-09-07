package com.knowledge.agent.tool;

import com.knowledge.agent.entity.ToolMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册中心：启动时自动发现所有 {@link Tool} Bean，按 name 索引；并叠加 DB 元数据。
 * <p>
 * SPI 扩展机制：新增工具只需实现 {@link Tool} + 标注 {@code @Component}，
 * Spring 自动注入到 {@code List<Tool>} 构造参数，注册中心零配置接入。
 * <p>
 * <b>双层元数据</b>（v3-3）：代码层 {@link Tool} 为可执行行为真源(name/schema/execute)，
 * DB 层 {@link ToolMetadata} 为运行时可配置叠加层(启用/超时/版本/分类)。
 * {@link #getMetadata} 提供租户继承查询（租户自有优先于平台预置）；
 * {@link #listToolInfos} 提供发现视图（合并 code+DB，仅 enabled，供 LLM function-calling 声明）。
 * <p>
 * 线程安全：注册表在构造后不可变（工具为 Spring 单例），运行时只读；元数据查询委托
 * {@link ToolMetadataService}（每次查 DB，保证配置实时性）。
 * <p>
 * 构造兼容：保留 {@code (List<Tool>)} 单参数构造，元数据服务为可选注入（测试/无 DB 时为 null，
 * {@link #getMetadata} 降级为默认元数据），确保现有 {@code ToolRegistryTest} 零改动。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
public class ToolRegistry {

    /** name → Tool 映射（保持注册顺序，便于 listTools 稳定输出） */
    private final Map<String, Tool> tools;

    /** DB 元数据服务（可选：测试/无 DB 时为 null，元数据查询降级为默认） */
    @Autowired(required = false)
    private ToolMetadataService metadataService;

    /**
     * Spring 自动注入所有 Tool Bean。
     *
     * @param toolList 容器中所有 {@link Tool} 实现（可能为空）
     */
    public ToolRegistry(List<Tool> toolList) {
        Map<String, Tool> map = new LinkedHashMap<>();
        for (Tool tool : toolList) {
            if (map.containsKey(tool.name())) {
                log.warn("[ToolRegistry] 工具名冲突，后注册者覆盖前者: {}", tool.name());
            }
            map.put(tool.name(), tool);
            log.info("[ToolRegistry] 注册工具: {} ({})", tool.name(),
                    tool.getClass().getSimpleName());
        }
        this.tools = Map.copyOf(map);
        log.info("[ToolRegistry] 注册完成，共 {} 个工具: {}", tools.size(), tools.keySet());
    }

    /**
     * 按名称获取工具。
     *
     * @param name 工具名
     * @return 工具实例，不存在返回 null
     */
    public Tool getTool(String name) {
        return tools.get(name);
    }

    /** 是否存在指定名称的工具 */
    public boolean exists(String name) {
        return tools.containsKey(name);
    }

    /** 列出所有已注册工具（供 LLM function calling 声明可用工具集） */
    public List<Tool> listTools() {
        return List.copyOf(tools.values());
    }

    /**
     * 获取工具元数据（租户继承：租户自有优先，无则平台预置）。
     * <p>metadataService 为 null（测试/无 DB）时降级为默认元数据（enabled=true）。
     *
     * @param tenantId 租户ID
     * @param name     工具名
     * @return 元数据；不存在返回默认元数据（enabled=true, timeout=null）
     */
    public ToolMetadata getMetadata(Long tenantId, String name) {
        if (metadataService != null) {
            ToolMetadata meta = metadataService.getMetadata(tenantId, name);
            if (meta != null) {
                return meta;
            }
        }
        return defaultMetadata(name);
    }

    /**
     * 工具发现视图：合并代码层 Tool 与 DB 元数据，仅返回 enabled 工具，按 sort_order 排序。
     * <p>{@code codePresent=false} 表示 DB 有元数据但无代码实现（未部署/已下线），发现接口可标记。
     *
     * @param tenantId 租户ID
     * @return 启用工具信息列表（含 code 与 db-only），按 sort_order 升序
     */
    public List<ToolInfo> listToolInfos(Long tenantId) {
        // 1. 加载 DB 元数据（租户继承，去重）
        Map<String, ToolMetadata> metaMap = new LinkedHashMap<>();
        if (metadataService != null) {
            for (ToolMetadata m : metadataService.listByTenant(tenantId)) {
                metaMap.put(m.getToolName(), m);
            }
        }
        // 2. 合并 code Tool
        Map<String, ToolInfo> result = new LinkedHashMap<>();
        for (Tool tool : tools.values()) {
            ToolMetadata m = metaMap.remove(tool.name());
            result.put(tool.name(), toInfo(tool, m, true));
        }
        // 3. 合并 DB-only 元数据（无代码实现）
        for (ToolMetadata m : metaMap.values()) {
            result.put(m.getToolName(), toInfo(null, m, false));
        }
        // 4. 过滤 enabled + 排序
        List<ToolInfo> list = new ArrayList<>(result.values());
        list.removeIf(info -> !info.enabled());
        list.sort((a, b) -> {
            int sa = sortOrderOf(a.name(), tenantId);
            int sb = sortOrderOf(b.name(), tenantId);
            return Integer.compare(sa, sb);
        });
        return list;
    }

    /** 合并 code Tool + DB 元数据 为 ToolInfo */
    private ToolInfo toInfo(Tool tool, ToolMetadata m, boolean codePresent) {
        String name = tool != null ? tool.name() : (m != null ? m.getToolName() : "");
        String displayName = m != null && m.getDisplayName() != null ? m.getDisplayName() : name;
        String description = m != null && m.getDescription() != null && !m.getDescription().isBlank()
                ? m.getDescription() : (tool != null ? tool.description() : "");
        String category = m != null && m.getCategory() != null ? m.getCategory() : "other";
        String version = m != null ? m.getCurrentVersion() : null;
        boolean authRequired = m != null && m.getAuthRequired() != null
                ? m.getAuthRequired() == 1 : (tool != null && tool.authRequired());
        Integer timeoutMs = m != null ? m.getTimeoutMs() : null;
        boolean enabled = m == null || m.getEnabled() == null || m.getEnabled() == 1;
        return new ToolInfo(name, displayName, description, category, version,
                authRequired, timeoutMs, enabled, codePresent);
    }

    /** 取工具 sort_order（DB 元数据为准，无则按 name 顺序兜底） */
    private int sortOrderOf(String name, Long tenantId) {
        if (metadataService != null) {
            ToolMetadata m = metadataService.getMetadata(tenantId, name);
            if (m != null && m.getSortOrder() != null) {
                return m.getSortOrder();
            }
        }
        return Integer.MAX_VALUE;
    }

    /** 默认元数据（无 DB 记录时降级使用：enabled=true, timeout=null） */
    private static ToolMetadata defaultMetadata(String name) {
        ToolMetadata m = new ToolMetadata();
        m.setToolName(name);
        m.setEnabled(1);
        m.setTimeoutMs(null);
        m.setCurrentVersion(null);
        return m;
    }
}
