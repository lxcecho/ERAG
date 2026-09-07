# ERAG 部署文档

## 1. 环境要求

### 1.1 硬件要求

| 组件 | 最低配置 | 推荐配置 |
|------|---------|---------|
| CPU | 4核 | 8核+ |
| 内存 | 8GB | 16GB+ |
| 磁盘 | 50GB | 100GB+ SSD |

### 1.2 软件要求

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | OpenJDK / Oracle JDK |
| Maven | 3.8+ | 构建工具 |
| Docker | 24+ | 容器运行时 |
| Docker Compose | 2.20+ | 容器编排 |
| Conda | 23+ | Python环境管理（MinerU） |

## 2. 快速开始（开发环境）

### 2.1 克隆项目

```bash
git clone <repository-url>
cd ERAG
```

### 2.2 启动基础设施

```bash
# 启动 MySQL、Redis、Milvus、ES、RabbitMQ
cd knowledge-backend
docker-compose up -d
```

### 2.3 启动MinerU服务

```bash
# 方式一：使用conda环境
cd mineru-service
conda env create -f environment.yml
conda activate rag
python run.py

# 方式二：使用Docker
cd mineru-service
docker-compose up -d
```

### 2.4 启动Java后端

```bash
cd knowledge-backend

# 使用Maven启动
mvn spring-boot:run -pl knowledge-admin -Dspring-boot.run.profiles=dev

# 或打包后运行
mvn clean package -DskipTests
java -jar knowledge-admin/target/knowledge-admin.jar --spring.profiles.active=dev
```

### 2.5 访问服务

- 后端API: http://localhost:8080/api
- Swagger文档: http://localhost:8080/api/swagger-ui.html
- MinerU服务: http://localhost:8000/docs

## 3. 生产环境部署

### 3.1 配置环境变量

创建 `.env` 文件：

```bash
# 数据库配置
MYSQL_HOST=mysql
MYSQL_PORT=3306
MYSQL_DATABASE=knowledge
MYSQL_USER=knowledge
MYSQL_PASSWORD=your_password

# Redis配置
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password

# Milvus配置
MILVUS_HOST=milvus
MILVUS_PORT=19530

# Elasticsearch配置
ES_URIS=http://elasticsearch:9200
ES_USERNAME=
ES_PASSWORD=

# RabbitMQ配置
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=erag
RABBITMQ_PASSWORD=your_rabbitmq_password

# MinerU配置
MINERU_URL=http://mineru:8000

# LLM配置
LLM_BASE_URL=http://ollama:11434/v1
LLM_API_KEY=ollama
LLM_MODEL=deepseek-r1:latest

# Embedding配置
EMBEDDING_BASE_URL=http://ollama:11434/v1
EMBEDDING_API_KEY=ollama
EMBEDDING_MODEL=bge-m3
EMBEDDING_DIMENSION=1024

# JWT配置
JWT_SECRET=your_jwt_secret_key_at_least_32_chars

# 存储配置
KB_STORAGE_TYPE=minio
KB_STORAGE_MINIO_ENDPOINT=http://minio:9000
KB_STORAGE_MINIO_ACCESS_KEY=minioadmin
KB_STORAGE_MINIO_SECRET_KEY=minioadmin
KB_STORAGE_MINIO_BUCKET=knowledge-files
```

### 3.2 使用Docker Compose部署

```bash
# 构建并启动所有服务
docker-compose -f docker-compose.prod.yml up -d

# 查看服务状态
docker-compose -f docker-compose.prod.yml ps

# 查看日志
docker-compose -f docker-compose.prod.yml logs -f knowledge-backend
```

### 3.3 docker-compose.prod.yml

```yaml
version: '3.8'

services:
  # Java后端
  knowledge-backend:
    build:
      context: ./knowledge-backend
      dockerfile: Dockerfile
    container_name: erag-backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - MYSQL_HOST=mysql
      - REDIS_HOST=redis
      - MILVUS_HOST=milvus
      - ES_URIS=http://elasticsearch:9200
      - RABBITMQ_HOST=rabbitmq
      - MINERU_URL=http://mineru:8000
    depends_on:
      - mysql
      - redis
      - milvus
      - elasticsearch
      - rabbitmq
      - mineru
    restart: unless-stopped

  # MinerU PDF解析服务
  mineru:
    build:
      context: ./mineru-service
      dockerfile: Dockerfile
    container_name: erag-mineru
    ports:
      - "8000:8000"
    environment:
      - MINERU_HOST=0.0.0.0
      - MINERU_PORT=8000
      - MINERU_LOG_LEVEL=INFO
    restart: unless-stopped

  # MySQL
  mysql:
    image: mysql:8.0
    container_name: erag-mysql
    ports:
      - "3306:3306"
    environment:
      - MYSQL_ROOT_PASSWORD=root_password
      - MYSQL_DATABASE=knowledge
      - MYSQL_USER=knowledge
      - MYSQL_PASSWORD=your_password
    volumes:
      - mysql-data:/var/lib/mysql
    restart: unless-stopped

  # Redis
  redis:
    image: redis:7-alpine
    container_name: erag-redis
    ports:
      - "6379:6379"
    command: redis-server --requirepass your_redis_password
    volumes:
      - redis-data:/data
    restart: unless-stopped

  # Milvus
  milvus:
    image: milvusdb/milvus:v2.4-latest
    container_name: erag-milvus
    ports:
      - "19530:19530"
    environment:
      - ETCD_USE_EMBED=true
      - COMMON_STORAGETYPE=local
    volumes:
      - milvus-data:/var/lib/milvus
    restart: unless-stopped

  # Elasticsearch
  elasticsearch:
    image: elasticsearch:8.11.0
    container_name: erag-elasticsearch
    ports:
      - "9200:9200"
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    restart: unless-stopped

  # RabbitMQ
  rabbitmq:
    image: rabbitmq:3-management
    container_name: erag-rabbitmq
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      - RABBITMQ_DEFAULT_USER=erag
      - RABBITMQ_DEFAULT_PASS=your_rabbitmq_password
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq
    restart: unless-stopped

  # MinIO对象存储
  minio:
    image: minio/minio
    container_name: erag-minio
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      - MINIO_ROOT_USER=minioadmin
      - MINIO_ROOT_PASSWORD=minioadmin
    command: server /data --console-address ":9001"
    volumes:
      - minio-data:/data
    restart: unless-stopped

  # Nginx网关
  nginx:
    image: nginx:alpine
    container_name: erag-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf
      - ./nginx/ssl:/etc/nginx/ssl
    depends_on:
      - knowledge-backend
    restart: unless-stopped

volumes:
  mysql-data:
  redis-data:
  milvus-data:
  es-data:
  rabbitmq-data:
  minio-data:
```

## 4. 配置说明

### 4.1 解析器配置

```yaml
ai:
  parser:
    # 解析器类型：tika | mineru
    type: ${AI_PARSER_TYPE:tika}
    mineru:
      # MinerU服务地址
      url: ${MINERU_URL:http://localhost:8000}
      # 请求超时（秒）
      timeout: ${MINERU_TIMEOUT:120}
      # 最大重试次数
      max-retries: ${MINERU_MAX_RETRIES:2}
      # 失败时是否回退到Tika
      fallback-to-tika: ${MINERU_FALLBACK_TIKA:true}
```

### 4.2 RAG配置

```yaml
ai:
  rag:
    # 切片大小（字符数）
    chunk-size: 800
    # 切片重叠（字符数）
    chunk-overlap: 200
    # 检索返回数量
    top-k: 5
    # 最小相似度分数
    min-score: 0.6
```

### 4.3 混合检索配置

```yaml
ai:
  rag:
    hybrid:
      # 总开关
      enabled: ${HYBRID_ENABLED:false}
      # ES词法检索开关
      es-enabled: ${HYBRID_ES_ENABLED:true}
      # ES召回数量
      es-top-k: 20
      # 向量召回数量
      vector-top-k: 20
      # 融合算法
      fusion:
        type: rrf
        rrf-k: 60
      # 重排序配置
      rerank:
        type: ${RERANK_TYPE:noop}
        top-n: 5
        url: ${RERANK_URL:https://api.siliconflow.cn/v1/rerank}
        api-key: ${RERANK_API_KEY:}
        model: ${RERANK_MODEL:BAAI/bge-reranker-v2-m3}
```

## 5. 数据库初始化

### 5.1 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS knowledge DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 5.2 执行初始化脚本

```bash
# 执行SQL初始化脚本
mysql -u root -p knowledge < docs/sql/init.sql
```

## 6. 健康检查

### 6.1 服务健康检查

```bash
# 检查Java后端
curl http://localhost:8080/api/actuator/health

# 检查MinerU服务
curl http://localhost:8000/health

# 检查Milvus
curl http://localhost:19530/healthz

# 检查Elasticsearch
curl http://localhost:9200/_cluster/health
```

### 6.2 查看服务状态

```bash
# Docker Compose服务状态
docker-compose ps

# 查看资源使用
docker stats

# 查看日志
docker-compose logs -f [service_name]
```

## 7. 常见问题

### 7.1 MinerU服务无法启动

**问题：** MinerU容器启动失败

**解决：**
```bash
# 查看日志
docker-compose logs mineru

# 检查依赖
docker exec -it erag-mineru pip list | grep magic-pdf

# 重建镜像
docker-compose build --no-cache mineru
```

### 7.2 向量维度不匹配

**问题：** Milvus collection维度与Embedding模型不一致

**解决：**
```bash
# 删除旧collection
curl -X DELETE http://localhost:19530/v2/vectordb/collections/drop \
  -H "Content-Type: application/json" \
  -d '{"collection_name": "knowledge_chunks"}'

# 重启服务重新创建collection
docker-compose restart knowledge-backend
```

### 7.3 解析超时

**问题：** 大PDF文件解析超时

**解决：**
```yaml
# 增加超时配置
ai:
  parser:
    mineru:
      timeout: 300  # 5分钟
```

## 8. 备份与恢复

### 8.1 数据备份

```bash
# MySQL备份
docker exec erag-mysql mysqldump -u root -p knowledge > backup.sql

# Milvus备份
# 使用Milvus的备份工具或手动备份数据目录

# 文件备份
tar -czf files_backup.tar.gz /path/to/knowledge-files
```

### 8.2 数据恢复

```bash
# MySQL恢复
docker exec -i erag-mysql mysql -u root -p knowledge < backup.sql

# 文件恢复
tar -xzf files_backup.tar.gz -C /path/to/
```

## 9. 监控与告警

### 9.1 Prometheus监控

```yaml
# 访问Prometheus
http://localhost:9090

# 常用指标
- http_server_requests_seconds (请求延迟)
- jvm_memory_used_bytes (JVM内存)
- rag_ingest_total (入库总数)
- rag_search_total (检索总数)
```

### 9.2 日志查看

```bash
# 实时日志
docker-compose logs -f knowledge-backend

# 错误日志
docker-compose logs knowledge-backend | grep ERROR

# 导出日志
docker-compose logs --no-color > app.log
```

## 10. 升级指南

### 10.1 版本升级

```bash
# 拉取最新代码
git pull origin main

# 重新构建
mvn clean package -DskipTests

# 重启服务
docker-compose down
docker-compose up -d --build
```

### 10.2 数据库迁移

```bash
# 执行迁移脚本
mysql -u root -p knowledge < docs/sql/migration/v1.1.sql
```
