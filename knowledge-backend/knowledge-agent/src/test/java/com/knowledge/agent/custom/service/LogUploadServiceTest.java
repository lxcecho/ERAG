package com.knowledge.agent.custom.service;

import com.knowledge.agent.custom.config.CustomAgentProperties;
import com.knowledge.agent.custom.dto.UploadResultVo;
import com.knowledge.agent.custom.storage.CustomAgentStorageService;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.storage.StoredFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LogUploadService} 单元测试：后缀白名单、大小上限、全文模式上传（不切片向量化）、读取与清理。
 *
 * @author: lxcechoo@gmail.com
 */
@ExtendWith(MockitoExtension.class)
class LogUploadServiceTest {

    @Mock
    private CustomAgentStorageService fileStorage;

    @Spy
    private CustomAgentProperties props = new CustomAgentProperties();

    @InjectMocks
    private LogUploadService service;

    private StoredFile stored(String originalName, String path) {
        return new StoredFile(originalName, "stored-" + path, path, "/abs/" + path, 0, "log", "log", "md5");
    }

    @Test
    void should_reject_oversized_file() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(101L * 1024 * 1024);

        assertThrows(BizException.class, () -> service.upload(file));
        verify(fileStorage, never()).store(file);
    }

    @Test
    void should_reject_unsupported_suffix() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("app.exe");

        assertThrows(BizException.class, () -> service.upload(file));
        verify(fileStorage, never()).store(file);
    }

    @Test
    void should_return_full_mode_for_small_file() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("app.log");
        when(fileStorage.store(file)).thenReturn(stored("app.log", "2026/08/03/app.log"));
        String text = "2026-08-03 10:00:00 INFO 启动服务成功\n2026-08-03 10:00:01 WARN 内存使用率 80%";
        when(fileStorage.open("2026/08/03/app.log")).thenReturn(new ByteArrayInputStream(text.getBytes()));

        UploadResultVo result = service.upload(file);

        assertEquals("full", result.getMode());
        assertEquals("2026/08/03/app.log", result.getFileRef());
        // Tika 文本解析可能在末尾补换行，charCount 以解析后实际长度为准，只断言不小于原文
        assertTrue(result.getCharCount() >= text.length());
        assertEquals(0, result.getChunkCount());
    }

    @Test
    void should_return_full_mode_for_large_file_too() {
        // 大文件同样全文模式：上传阶段不再切片向量化入库，运行时由执行器按 logContextLimit 截断
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("big.log");
        when(fileStorage.store(file)).thenReturn(stored("big.log", "2026/08/03/big.log"));
        String bigText = "LOG".repeat(4000); // 12000 字符，远大于 logContextLimit 的默认配置无关，上传仍返回 full
        when(fileStorage.open("2026/08/03/big.log")).thenReturn(new ByteArrayInputStream(bigText.getBytes()));

        UploadResultVo result = service.upload(file);

        assertEquals("full", result.getMode());
        assertTrue(result.getCharCount() >= bigText.length());
        assertEquals(0, result.getChunkCount());
    }

    @Test
    void should_cleanup_upload_best_effort() {
        service.cleanupUpload("ref-1");
        verify(fileStorage).delete("ref-1");
    }

    @Test
    void should_read_text_of_uploaded_file() {
        when(fileStorage.open("ref-1")).thenReturn(new ByteArrayInputStream("hello log".getBytes()));
        String text = service.readText("ref-1");
        assertTrue(text.contains("hello log"));
    }
}
