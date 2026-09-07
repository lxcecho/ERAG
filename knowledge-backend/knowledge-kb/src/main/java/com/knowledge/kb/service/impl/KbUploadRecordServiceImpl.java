package com.knowledge.kb.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.kb.constant.KbConstants;
import com.knowledge.kb.dto.PageQuery;
import com.knowledge.kb.entity.KbUploadRecord;
import com.knowledge.kb.mapper.KbUploadRecordMapper;
import com.knowledge.kb.service.KbUploadRecordService;
import com.knowledge.kb.storage.StoredFile;
import org.springframework.stereotype.Service;

/**
 * 上传记录服务实现
 *
 * @author: lxcechoo@gmail.com
 */
@Service
public class KbUploadRecordServiceImpl extends ServiceImpl<KbUploadRecordMapper, KbUploadRecord>
        implements KbUploadRecordService {

    @Override
    public void recordSuccess(Long documentId, Long kbId, StoredFile sf, Long userId) {
        KbUploadRecord record = new KbUploadRecord();
        record.setDocumentId(documentId);
        record.setKbId(kbId);
        record.setOriginalName(sf.getOriginalName());
        record.setFileSize(sf.getSize());
        record.setFileType(sf.getFileType());
        record.setUploadStatus(KbConstants.UPLOAD_SUCCESS);
        record.setErrorMsg("");
        record.setCreatorId(userId);
        save(record);
    }

    @Override
    public void recordFailure(Long kbId, String originalName, long fileSize, String fileType, String errorMsg, Long userId) {
        KbUploadRecord record = new KbUploadRecord();
        record.setDocumentId(null);
        record.setKbId(kbId);
        record.setOriginalName(originalName == null ? "" : originalName);
        record.setFileSize(fileSize);
        record.setFileType(fileType == null ? "" : fileType);
        record.setUploadStatus(KbConstants.UPLOAD_FAILED);
        record.setErrorMsg(errorMsg == null ? "" : errorMsg);
        record.setCreatorId(userId);
        save(record);
    }

    @Override
    public IPage<KbUploadRecord> page(Long kbId, Integer pageNo, Integer pageSize) {
        PageQuery query = new PageQuery();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        Page<KbUploadRecord> page = query.toPage();
        return lambdaQuery()
                .eq(kbId != null, KbUploadRecord::getKbId, kbId)
                .orderByDesc(KbUploadRecord::getCreateTime)
                .page(page);
    }
}
