package com.knowledge.agent.tool;

import com.knowledge.kb.service.KbPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具权限校验器：在工具执行前校验调用方对企业资源的访问权限。
 * <p>
 * 三级权限模型（v3-3 增强，签名不变）：
 * <ol>
 *   <li><b>工具启用</b>：{@link ToolMetadataService#isEnabled}——DB 元数据 enabled=0 拒绝
 *       （租户级开关，平台预置 tenant=0 为默认）；</li>
 *   <li><b>工具授权</b>：{@link ToolMetadataService#isGranted}——tool_permission 显式授权判定，
 *       默认开放（无记录放行），有记录则要求命中 ALLOW（租户级/角色级）；</li>
 *   <li><b>KB 权限</b>：{@code authRequired=true} 时校验用户对 kbId 的 viewer 权限，
 *       不通过抛 {@link ToolException}（PERMISSION_DENIED）。</li>
 * </ol>
 * <p>
 * 前两级对所有工具生效（无论 authRequired）；第三级仅对接触 KB 数据的工具生效。
 * 文档级权限（VIEW/DOWNLOAD/EDIT）由各工具内部通过 {@code DocPermissionService} 细粒度校验，
 * 本校验器只做工具级 + KB 级粗粒度门禁。
 * <p>
 * 注：工具启用/授权查询用 {@code @InterceptorIgnore} 显式 tenant_id（不依赖 TenantContext），
 * 因本校验在 ToolExecutor 设置租户上下文之前执行。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolPermissionChecker {

    private final KbPermissionService kbPermissionService;
    private final ToolMetadataService metadataService;

    /**
     * 校验工具执行权限（工具启用 → 工具授权 → KB 权限）。
     *
     * @param tool 待执行工具
     * @param ctx  执行上下文
     * @throws ToolException 权限不通过时抛出（TOOL_DISABLED / PERMISSION_DENIED）
     */
    public void check(Tool tool, ToolContext ctx) {
        Long tenantId = ctx.getTenantId();

        // 1. 工具启用校验（租户级开关）
        if (!metadataService.isEnabled(tenantId, tool.name())) {
            log.warn("[ToolPermission] 工具未启用 tool={} tenant={}", tool.name(), tenantId);
            throw new ToolException(tool.name(), "TOOL_DISABLED", "工具未启用: " + tool.name());
        }

        // 2. 工具授权校验（默认开放：无记录放行）
        if (!metadataService.isGranted(tenantId, ctx.getUserId(), tool.name())) {
            log.warn("[ToolPermission] 无工具调用权限 tool={} user={} tenant={}",
                    tool.name(), ctx.getUserId(), tenantId);
            throw new ToolException(tool.name(), "PERMISSION_DENIED", "无工具调用权限: " + tool.name());
        }

        // 3. KB 级权限校验（仅 authRequired=true 的工具）
        if (!tool.authRequired()) {
            return;
        }
        if (ctx.getKbId() == null) {
            throw new ToolException(tool.name(), "PERMISSION_DENIED",
                    "工具 [" + tool.name() + "] 需要 KB 权限，但未指定知识库ID");
        }
        // KbPermissionService.checkViewer 不通过时抛 BizException(403)，
        // 这里捕获并转为 ToolException，保持工具层异常统一
        try {
            kbPermissionService.checkViewer(ctx.getKbId(), ctx.getUserId());
        } catch (com.knowledge.common.exception.BizException e) {
            log.warn("[ToolPermission] 工具={} 用户={} KB={} 权限校验失败: {}",
                    tool.name(), ctx.getUserId(), ctx.getKbId(), e.getMessage());
            throw new ToolException(tool.name(), "PERMISSION_DENIED", e.getMessage(), e);
        }
    }
}
