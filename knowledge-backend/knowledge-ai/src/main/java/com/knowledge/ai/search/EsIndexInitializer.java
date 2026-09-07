package com.knowledge.ai.search;

import com.knowledge.ai.config.EsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * ES 索引初始化器：应用启动时检测索引是否存在，不存在则按 mapping 创建。
 * <p>容错策略：ES 未启动或连接失败时仅告警，不抛异常阻断应用启动——
 * 混合检索会自动降级为纯向量检索，保证 ES 缺席时系统仍可用。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsIndexInitializer implements ApplicationRunner {

    private final ElasticsearchService elasticsearchService;
    private final EsProperties props;

    @Override
    public void run(ApplicationArguments args) {
        if (!props.isEnabled()) {
            log.info("[ES初始化] es.enabled=false，跳过索引初始化");
            return;
        }
        try {
            if (elasticsearchService.indexExists()) {
                log.info("[ES初始化] 索引已存在 index={}", props.getIndexName());
            } else {
                elasticsearchService.createIndex();
            }
        } catch (Exception e) {
            log.warn("[ES初始化] 索引初始化失败（ES可能未启动），混合检索将降级纯向量: {}", e.getMessage());
        }
    }
}
