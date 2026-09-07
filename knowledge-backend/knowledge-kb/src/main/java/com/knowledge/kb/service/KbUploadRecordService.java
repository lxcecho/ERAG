package com.knowledge.kb.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.knowledge.kb.entity.KbUploadRecord;
import com.knowledge.kb.storage.StoredFile;

/**
 * 上传记录服务接口
 *
 * @author: lxcechoo@gmail.com
 */
public interface KbUploadRecordService extends IService<KbUploadRecord> {

    /** 记录上传成功 */
    void recordSuccess(Long documentId, Long kbId, StoredFile storedFile, Long userId);

    /** 记录上传失败 */
    void recordFailure(Long kbId, String originalName, long fileSize, String fileType, String errorMsg, Long userId);

    /** 分页查询某知识库的上传记录 */
    IPage<KbUploadRecord> page(Long kbId, Integer pageNo, Integer pageSize);
}
