# ERAG - 企业级RAG知识助手

## 📖 项目简介

ERAG 是一个企业级 RAG（Retrieval-Augmented Generation）知识助手系统，支持多文档格式解析、智能切片、向量检索和 LLM 问答。

### ✨ 核心特性

- 📄 **多格式文档解析** - 支持 PDF、Word、Markdown，使用 MinerU 智能解析 PDF
- ✂️ **智能文本切片** - 基于 Markdown 标题结构切分，保留文档层级
- 🔍 **混合检索** - 向量检索 + BM25 词法检索 + RRF 融合
- 🎯 **精准重排序** - Cross-Encoder 重排序，提升检索精度
- 💬 **流式对话** - SSE 流式输出，实时响应
- 🔐 **多租户隔离** - 支持多租户数据隔离
- 📊 **知识治理** - 文档去重、版本管理、质量评分

## 🏗️ 系统架构

```
用户请求
    │
    ▼
Spring Boot 应用层
├── RAG服务（编排核心）
├── Agent服务（任务型）
└── 工作流服务（可控流程）
    │
    ▼
RAG Pipeline
解析 → 清洗 → 切片 → 增强 → 向量化 → 存储
    │
    ▼
数据存储层
├── MySQL（元数据）
├── Redis（缓存）
├── Milvus（向量）
└── Elasticsearch（全文）
    │
    ▼
外部服务层
├── MinerU（PDF解析）
├── Ollama（本地LLM）
├── LLM API（在线LLM）
└── Reranker（重排序）
```

## 📁 项目结构

```
ERAG/
├── knowledge-backend/          # Java后端
│   ├── knowledge-admin/        # 管理后台（启动模块）
│   ├── knowledge-ai/           # AI/RAG核心模块
│   ├── knowledge-kb/           # 知识库管理模块
│   ├── knowledge-auth/         # 认证授权模块
│   └── knowledge-common/       # 公共模块
├── mineru-service/             # MinerU PDF解析服务
│   ├── app/                    # Python应用代码
│   ├── Dockerfile              # Docker配置
│   └── docker-compose.yml      # 容器编排
└── docs/                       # 项目文档
    ├── architecture.md         # 架构设计文档
    ├── deployment.md           # 部署文档
    └── api.md                  # API文档
```

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Docker 24+ & Docker Compose
- Conda（可选，用于MinerU本地开发）

### 方式一：Docker Compose 一键部署（推荐）

```bash
# 1. 克隆项目
git clone <repository-url>
cd ERAG

# 2. 启动所有服务
docker-compose up -d

# 3. 访问服务
# - 后端API: http://localhost:8080/api
# - MinerU: http://localhost:8000
# - Swagger: http://localhost:8080/api/swagger-ui.html
```

### 方式二：本地开发环境

```bash
# 1. 启动基础设施
cd knowledge-backend
docker-compose up -d

# 2. 启动MinerU服务
cd ../mineru-service
conda env create -f environment.yml
conda activate rag
python run.py

# 3. 启动Java后端（IDEA或命令行）
cd ../knowledge-backend
mvn spring-boot:run -pl knowledge-admin
```

## 📚 文档

- [架构设计文档](docs/architecture.md) - 系统架构详细说明
- [部署文档](docs/deployment.md) - 完整部署指南

## 🔧 配置说明

### 解析器配置

```yaml
ai:
  parser:
    type: mineru  # 解析器类型：tika | mineru
    mineru:
      url: http://localhost:8000
      timeout: 120
      fallback-to-tika: true
```

### RAG配置

```yaml
ai:
  rag:
    chunk-size: 800
    chunk-overlap: 200
    top-k: 5
    min-score: 0.6
```

## 🛠️ 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Spring Boot 3.5.16, LangChain4j 1.18.0 |
| PDF解析 | MinerU, Apache Tika |
| 向量化 | bge-m3 (Ollama), 1024维 |
| 向量库 | Milvus |
| 全文检索 | Elasticsearch |
| 消息队列 | RabbitMQ |
| 缓存 | Redis |
| 数据库 | MySQL 8.0 |
| LLM | Ollama / DeepSeek / OpenAI |
| 重排序 | BGE-Reranker |

## 📊 核心流程

### 文档入库流程

```
上传PDF → MinerU解析 → Markdown清洗 → 标题切片 → 元数据增强 → 向量化 → 存储
```

### 问答检索流程

```
用户提问 → 查询重写 → 混合检索 → 重排序 → Prompt构建 → LLM生成 → 返回答案
```

## 📄 许可证

本项目基于 Apache License 2.0 许可证开源
