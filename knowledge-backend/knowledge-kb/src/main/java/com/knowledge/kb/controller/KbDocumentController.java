package com.knowledge.kb.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.annotation.BusinessType;
import com.knowledge.common.annotation.OperLog;
import com.knowledge.common.exception.BizException;
import com.knowledge.common.result.Result;
import com.knowledge.kb.dto.KbDocumentQuery;
import com.knowledge.kb.dto.KbDocumentVo;
import com.knowledge.kb.dto.UploadResultVo;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.permission.DocPermission;
import com.knowledge.kb.permission.service.DocPermissionService;
import com.knowledge.kb.permission.service.DocVisibleSetService;
import com.knowledge.kb.service.KbDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 文档管理接口：上传 / 查询 / 删除 / 触发解析。
 * <p>【文档级权限】所有直接访问文档资源的接口必须经 DocPermissionService.requiredAccess 校验，
 * 禁止仅凭 KB 级 viewer 角色就放行 KB 内任意文档。
 *
 * @author: lxcechoo@gmail.com
 */
@Tag(name = "文档管理接口")
@RestController
@RequestMapping("/kb/documents")
@RequiredArgsConstructor
public class KbDocumentController {

    private final KbDocumentService kbDocumentService;
    private final DocPermissionService docPermissionService;
    private final DocVisibleSetService docVisibleSetService;

    @Operation(summary = "上传文档（支持 PDF / Word / Markdown）")
    @PostMapping("/upload")
    @OperLog(title = "文档管理", businessType = BusinessType.INSERT, recordParam = false)
    public Result<UploadResultVo> upload(
            @Parameter(description = "所属知识库ID") @RequestParam Long kbId,
            @Parameter(description = "文档文件") @RequestParam MultipartFile file) {
        return Result.success(kbDocumentService.upload(kbId, file, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "文档分页列表")
    @GetMapping
    public Result<IPage<KbDocumentVo>> page(KbDocumentQuery query) {
        if (query == null || query.getKbId() == null) {
            throw new BizException(400, "需指定知识库ID");
        }
        Long uid = SecurityUtils.currentUserId();
        IPage<KbDocumentVo> page = kbDocumentService.page(query);

        // 【文档级权限】列表后置过滤：剔除用户无 VIEW 权限的文档
        // owner/editor 场景：DocVisibleSet 为 allVisible=true，短路（不剔除）；
        // viewer 场景：调用 filterDocIds（内部一次性 ACL + 继承 + 创建者判断）精确保留可见文档
        DocVisibleSetService.VisibleSet vs = docVisibleSetService.computeViewableDocIds(uid, query.getKbId());
        if (!vs.isAllVisible()) {
            Set<Long> allowDocIds = docPermissionService.filterDocIds(uid, query.getKbId(),
                    page.getRecords().stream().map(KbDocumentVo::getId).toList());
            page.setRecords(page.getRecords().stream()
                    .filter(v -> allowDocIds.contains(v.getId()))
                    .toList());
        }
        return Result.success(page);
    }

    @Operation(summary = "文档详情")
    @GetMapping("/{id}")
    public Result<KbDocumentVo> detail(@PathVariable Long id) {
        KbDocument doc = requireDoc(id);
        docPermissionService.requiredAccess(SecurityUtils.currentUserId(), doc, DocPermission.VIEW);
        return Result.success(kbDocumentService.getDetail(id));
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    @OperLog(title = "文档管理", businessType = BusinessType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        KbDocument doc = requireDoc(id);
        docPermissionService.requiredAccess(SecurityUtils.currentUserId(), doc, DocPermission.DELETE);
        kbDocumentService.remove(id);
        // 删除后：失效本 KB 所有用户可见集合缓存（ACL/可见性关联）
        docPermissionService.evictCacheForDocPermissionChange(doc.getKbId(), null);
        return Result.success();
    }

    @Operation(summary = "触发文档解析（异步），返回解析任务ID")
    @PostMapping("/{id}/parse")
    @OperLog(title = "文档管理", businessType = BusinessType.OTHER)
    public Result<Long> parse(@PathVariable Long id) {
        KbDocument doc = requireDoc(id);
        docPermissionService.requiredAccess(SecurityUtils.currentUserId(), doc, DocPermission.EDIT);
        return Result.success(kbDocumentService.triggerParse(id, SecurityUtils.currentUserId()));
    }

    private KbDocument requireDoc(Long id) {
        KbDocument doc = kbDocumentService.getById(id);
        if (doc == null) throw new BizException(404, "文档不存在");
        return doc;
    }
}
