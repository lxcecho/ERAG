# ERAG 架构设计文档

## 1. 系统概述

ERAG（Enterprise RAG Knowledge Assistant）是一个企业级RAG知识助手系统，基于LangChain4j构建，支持多文档格式解析、智能切片、向量检索和LLM问答。

## 2. 核心模块说明

### 2.1 文档解析模块 (Parser)

**职责：** 将PDF/Word/Markdown等格式文档转换为结构化文本

**组件：**
- `SmartDocumentParser` - 智能解析器（自动选择MinerU或Tika）
- `MinerUClient` - MinerU微服务客户端
- `MarkdownTextCleaner` - Markdown文本清洗器

**流程：**
```
PDF文件 -> SmartDocumentParser -> MinerU解析 -> Markdown清洗 -> 结构化文本
                                      |
                                      + (失败时) -> Tika解析 -> 纯文本
```

### 2.2 文本切片模块 (Splitter)

**职责：** 将长文档切分为适合向量化的小块

**策略：**
- `DocumentByMarkdownHeaderSplitter` - 按Markdown标题层级切分（推荐）
- `RecursiveCharacterTextSplitter` - 递归字符切分（备用）

**特点：**
- 保留文档结构（标题层级）
- 表格整片保留
- 支持Parent-Child模式

### 2.3 元数据增强模块 (Enricher)

**职责：** 为每个chunk注入检索友好的元数据

**注入内容：**
```
前缀：【公司人事档案 > 第二章 人员信息 > 2.1 技术部】
元数据：documentName, headingPath, chunkIndex, chunkId
```

### 2.4 向量化与存储模块

**组件：**
- `EmbeddingService` - 文本向量化（bge-m3, 1024维）
- `MilvusService` - 向量存储与检索
- `ElasticsearchService` - 全文索引（BM25检索）

### 2.5 检索增强模块

**组件：**
- `HybridSearchService` - 混合检索（向量 + BM25 + RRF融合）
- `ReRankingService` - 重排序（Cross-Encoder）
- `QueryTransformer` - 查询重写（可选）

## 3. 数据流

### 3.1 文档入库流程

```
1. 用户上传PDF -> KbDocumentController.upload()
2. 存储文件 -> StorageService (Local/MinIO)
3. 创建解析任务 -> RabbitMQ / Spring Event
4. 调用MinerU解析 -> MinerUClient.parsePdf()
5. Markdown清洗 -> MarkdownTextCleaner.clean()
6. 文本切片 -> DocumentByMarkdownHeaderSplitter
7. 元数据增强 -> MetadataPrefixEnricher.enrich()
8. 向量化 -> EmbeddingService.embedAll()
9. 存储 -> MilvusService.store() + ElasticsearchService.store()
```

### 3.2 问答检索流程

```
1. 用户提问 -> ChatController.ask()
2. 查询重写 -> QueryTransformer.rewrite()（可选）
3. 混合检索 -> HybridSearchService.search()
4. 重排序 -> ReRankingService.rerank()
5. 构建Prompt -> 系统模板 + 检索结果 + 历史对话
6. LLM生成 -> ChatModel.generate()
7. 返回结果 -> 流式/非流式响应
```

## 4. 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 框架 | Spring Boot 3.5.16 | 应用框架 |
| RAG框架 | LangChain4j 1.18.0 | RAG核心 |
| PDF解析 | MinerU / Tika | 文档解析 |
| 向量化 | bge-m3 (Ollama) | 1024维向量 |
| 向量库 | Milvus | 向量存储 |
| 全文检索 | Elasticsearch | BM25检索 |
| 消息队列 | RabbitMQ | 异步任务 |
| 缓存 | Redis | 会话/缓存 |
| 数据库 | MySQL | 元数据存储 |
| LLM | Ollama / DeepSeek / OpenAI | 大模型 |
| 重排序 | BGE-Reranker | Cross-Encoder |

## 5. 部署架构

### 5.1 开发环境

```
本地开发环境
├── Java后端 (IDEA运行) :8080
├── MinerU (Docker) :8000
└── Docker Desktop
    ├── MySQL :3306
    ├── Redis :6379
    ├── Milvus :19530
    └── Elasticsearch :9200
```

### 5.2 生产环境

```
Docker Compose
├── Nginx (网关) :80/:443
├── Java后端 (多实例) :8080
├── MinerU (多实例) :8000
└── 数据存储层
    ├── MySQL
    ├── Redis
    ├── Milvus
    ├── Elasticsearch
    ├── MinIO
    └── RabbitMQ
```

## 6. 扩展性设计

### 6.1 解析器扩展

实现 `DocumentParser` 接口即可添加新解析器。

### 6.2 切片器扩展

实现 `DocumentSplitter` 接口即可添加新切片策略。

### 6.3 检索器扩展

实现 `SearchService` 接口即可添加新检索方式。

## 7. 性能优化

- MinerU异步解析（RabbitMQ队列）
- 向量索引：IVF_FLAT / HNSW
- 批量向量化（batch_size=64）
- 混合检索（向量+BM25）
- 流式输出（SSE）
- 上下文压缩
- Prompt模板缓存

## 8. 安全设计

- JWT Token认证
- 多租户隔离
- 文档级权限控制
- 敏感信息加密
- SQL注入防护
- XSS防护
- SSRF防护
