package com.knowledge.ai.ops.collector;

/**
 * 基础设施指标资源名常量。
 * <p>对齐 {@code AlertService.alert()} 的 source 命名，评估时零转换。
 *
 * @author: lxcechoo@gmail.com
 */
public final class MetricResource {

    private MetricResource() {
    }

    /** Milvus 向量检索 */
    public static final String MILVUS_SEARCH = "milvus:search";

    /** Elasticsearch 关键词检索 */
    public static final String ES_SEARCH = "es:search";

    /** RabbitMQ 文档解析消费 */
    public static final String MQ_PARSE = "mq:parse";
}
