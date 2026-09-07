package com.knowledge.kb.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.knowledge.kb.dto.KbDocumentQuery;
import com.knowledge.kb.dto.KbDocumentVo;
import com.knowledge.kb.dto.UploadResultVo;
import com.knowledge.kb.entity.KbDocument;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档服务接口
 *
 * @author: lxcechoo@gmail.com
 */
public interface KbDocumentService extends IService<KbDocument> {

    /**
     * 上传文档：校验 → 存储 → 入库 → 记录上传 → 创建解析任务 → 计数
     *
     * @param kbId  所属知识库ID
     * @param file  上传文件
     * @param userId 当前用户ID
     * @return 上传结果（含文档ID与解析任务ID）
     */
    UploadResultVo upload(Long kbId, MultipartFile file, Long userId);

    /** 文档分页（联查知识库名称） */
    IPage<KbDocumentVo> page(KbDocumentQuery query);

    /** 文档详情 */
    KbDocumentVo getDetail(Long id);

    /** 删除文档（软删，并递减知识库文档数） */
    void remove(Long id);

    /** 触发文档解析：创建/复用解析任务并异步执行，返回任务ID */
    Long triggerParse(Long documentId, Long userId);

    /**
     * 查询最近 N 小时内解析成功的文档（用于双写一致性检查）。
     *
     * @param hours 最近 N 小时
     * @return 解析成功的文档列表
     */
    java.util.List<KbDocument> listRecentParsed(int hours);
}
