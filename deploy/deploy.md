# ERAG 一体化部署方案

> 企业级 AI 知识库助手全栈部署：Frontend + Backend + MySQL + Milvus + Elasticsearch + RabbitMQ + Redis + MinIO
> 一条命令拉起全部服务，数据持久化 + 日志挂载 + 环境变量配置。

---

## 一、架构总览

```
                    ┌─────────────────────────────────────────┐
                    │              宿主机 :80                   │
                    │         Frontend (Nginx)                 │
                    │  SPA 静态资源 + /api 反代 backend:8080    │
                    │  SSE 关闭缓冲（流式对话长连接保活）        │
                    └──────────────────┬──────────────────────┘
                                       │ erag-net (bridge)
                    ┌──────────────────▼──────────────────────┐
                    │         Backend :8080 (Spring Boot)      │
                    │  context-path=/api  profile=dev          │
                    │  RAG / Agent / 知识治理 / 任务队列        │
                    └──┬──────┬──────┬──────┬──────┬──────────┘
                       │      │      │      │      │
          ┌────────────▼┐ ┌───▼──┐ ┌─▼──┐ ┌─▼───┐ ┌▼────────┐
          │ MySQL :3306 │ │Milvus│ │ ES │ │RMQ  │ │ Redis   │
          │ 业务库+建表  │ │:19530│ │:9200│ │:5672│ │:6379    │
          └─────────────┘ │向量库│ │词法 │ │任务 │ │全局缓存  │
                          └──┬──┘ └────┘ └─────┘ └─────────┘
                          ┌──┴──┐
                          │etcd │ (Milvus 元数据)
                          │+内io│
                          └─────┘
```

**服务清单（11 个容器）**：

| 服务 | 镜像 | 端口 | 职责 |
|---|---|---|---|
| mysql | mysql:8.0 | 3306 | 业务库，挂载 init.sql 自动建表 |
| etcd | coreos/etcd:v3.5.5 | - | Milvus 元数据存储（内部） |
| milvus-minio | minio/minio | - | Milvus 对象存储（内部，不暴露） |
| milvus | milvusdb/milvus:v2.4.4 | 19530 | 向量数据库（standalone） |
| elasticsearch | elasticsearch:8.13.4 | 9200 | 混合检索词法路（BM25） |
| rabbitmq | rabbitmq:3.13-management | 5672/15672 | 任务队列（重试/死信/幂等/补偿）+ 管理界面 |
| redis | redis:7.4-alpine | 6379 | 全局缓存（Prompt/KB/统计/检索） |
| minio | minio/minio | 9000/9001 | 应用文件存储 + Console |
| minio-init | minio/mc | - | 一次性创建 bucket（knowledge-files） |
| backend | erag-backend（构建） | 8080 | Spring Boot 应用 |
| frontend | erag-frontend（构建） | 80 | Nginx 静态资源 + 反代 |

**消息队列说明**：项目文档解析任务系统基于 **RabbitMQ 原生 DLX** 实现消息重试/死信队列/消费幂等/任务补偿，docker-compose 编排 RabbitMQ 与代码一致。

---

## 二、前置准备

### 2.1 安装 Docker
- Docker Engine 24+ 与 Docker Compose v2（`docker compose` 命令）
- 验证：`docker compose version`

### 2.2 Elasticsearch 内核参数（必须）
ES 8.x 要求 `vm.max_map_count >= 262144`，**宿主机执行一次**（重启失效需重设）：
```bash
# Linux
sudo sysctl -w vm.max_map_count=262144

# 永久生效
echo 'vm.max_map_count=262144' | sudo tee -a /etc/sysctl.conf

# Windows Docker Desktop：通常无需设置（WSL2 已满足），若 ES 启动失败参考官方 WSL2 配置
```

### 2.3 配置环境变量
```bash
cd deploy
cp .env.example .env
```
编辑 `.env`，**必须修改以下 API Key**（否则 LLM/Agent/向量化不可用）：
```env
LLM_API_KEY=sk-你的LLM密钥（DeepSeek/阿里云百炼等）
EMBEDDING_API_KEY=sk-你的Embedding密钥
```
> 其余为演示默认值，可按需调整端口、密码。
> RAG 与 Agent 层统一走 LangChain4j + `LLM_BASE_URL`。

---

## 三、启动部署

### 3.1 一键启动（首次会构建前后端镜像，约 5-10 分钟）
```bash
cd deploy
docker compose up -d --build
```

### 3.2 查看启动状态
```bash
docker compose ps
```
等待所有服务 `healthy`（Milvus/ES 启动较慢，约 1-2 分钟）：
```
NAME              STATUS                   PORTS
erag-mysql        Up (healthy)             0.0.0.0:3306->3306
erag-etcd         Up (healthy)
erag-milvus       Up (healthy)             0.0.0.0:19530->19530
erag-es           Up (healthy)             0.0.0.0:9200->9200
erag-rabbitmq     Up (healthy)             0.0.0.0:5672->5672, 15672
erag-redis        Up (healthy)             0.0.0.0:6379->6379
erag-minio        Up (healthy)             0.0.0.0:9000->9000, 9001
erag-backend      Up                       0.0.0.0:8080->8080
erag-frontend     Up                       0.0.0.0:80->80
```

### 3.3 查看后端启动日志（确认中间件连接成功）
```bash
docker compose logs -f backend
```
看到 `Started KnowledgeApplication` 即启动成功。

---

## 四、服务访问地址

| 服务 | 地址 | 账号 |
|---|---|---|
| **前端应用** | http://localhost | admin / 123456 |
| **后端 API** | http://localhost:8080/api | - |
| **Swagger 文档** | http://localhost:8080/api/doc.html | - |
| **Actuator 健康检查** | http://localhost:8080/api/actuator/health | - |
| **RabbitMQ 管理界面** | http://localhost:15672 | erag / erag123 |
| **MinIO Console** | http://localhost:9001 | minioadmin / minioadmin123 |
| **Elasticsearch** | http://localhost:9200 | 无认证 |
| **MySQL** | localhost:3306 | root / root123 |

> 前端访问 http://localhost 即可使用全部功能（Nginx 反代 /api 到后端）。

---

## 五、数据持久化与日志

### 5.1 数据持久化（`deploy/data/`）
所有中间件数据持久化到宿主机，容器删除重建不丢数据：
```
deploy/data/
├── mysql/          # MySQL 数据文件
├── etcd/           # Milvus 元数据
├── milvus/         # Milvus 向量数据
├── milvus-minio/   # Milvus 内部对象存储
├── es/             # Elasticsearch 索引数据
├── rabbitmq/       # RabbitMQ 消息持久化
├── redis/          # Redis RDB/AOF
├── minio/          # 应用文件存储（上传的文档）
└── files/          # 后端本地文件存储兜底（kb.storage.type=local 时用）
```

### 5.2 日志挂载（`deploy/logs/`）
```
deploy/logs/
├── backend/        # 后端日志（knowledge-admin.log + error.log，logback 滚动）
├── mysql/          # MySQL 慢查询/错误日志
├── milvus/         # Milvus 运行日志
├── es/             # Elasticsearch 日志
├── rabbitmq/       # RabbitMQ 日志
├── minio/          # MinIO 访问日志
└── nginx/          # 前端 Nginx access/error 日志
```
后端日志按天 + 100MB 滚动，保留 30 天，错误日志单独文件 `knowledge-admin-error.log`。

---

## 六、配置说明

### 6.1 环境变量（`.env`）
所有配置集中 `.env` 文件，docker compose 自动读取并注入 backend 容器（`env_file: .env`）。
后端 `application.yml` / `application-dev.yml` 用 `${VAR:default}` 引用，环境变量优先级高于默认值。

### 6.2 关键配置项

| 配置 | 默认 | 说明 |
|---|---|---|
| `KB_PARSE_MQ_ENABLED` | true | 文档解析走 RabbitMQ；false 降级 @Async 事件（无需 RabbitMQ） |
| `KB_GOVERNANCE_ENABLED` | true | 知识治理（去重/评分/审核） |
| `HYBRID_ENABLED` | false | 混合检索（ES+Milvus+RRF）；false 走纯向量 |
| `RERANK_TYPE` | noop | 精排；`http` 启用外部 Cross-Encoder |
| `RAG_USE_DB_TEMPLATE` | true | Prompt 用 DB 模板（需 init.sql 种子数据） |
| `SPRING_CACHE_TYPE` | redis | 全局缓存；`none` 直通 DB（无 Redis 时降级） |
| `TENANT_ENABLED` | false | 多租户；演示关闭 |
| `AI_EMBEDDING_BATCH_SIZE` | 64 | Embedding 批量大小（dashscope ≤25，OpenAI ≤2048） |
| `EMBEDDING_DIMENSION` | 1536 | Embedding 向量维度（须与 Milvus 集合维度一致） |

### 6.3 降级模式（无 RabbitMQ 也能跑）
设置 `.env` 中 `KB_PARSE_MQ_ENABLED=false`，后端走原 `@Async` + Spring Event 路径，
`RabbitMqConfig` / `ParseTaskProducer` / `ParseTaskConsumer` 不装配，应用正常启动（任务无重试/死信能力）。

---

## 七、常用命令

```bash
# 启动全部
docker compose up -d

# 重新构建前后端镜像（代码更新后）
docker compose up -d --build backend frontend

# 查看日志
docker compose logs -f backend          # 后端实时日志
docker compose logs -f rabbitmq         # RabbitMQ 日志

# 单独重启某服务
docker compose restart backend

# 停止全部（保留数据）
docker compose down

# 停止并删除数据卷（清空所有数据，慎用！）
docker compose down -v
rm -rf data/ logs/

# 进入容器
docker compose exec mysql mysql -uroot -proot123 knowledge_ai
docker compose exec backend sh
```

---

## 八、常见问题排障

### Q1: Milvus 启动慢 / unhealthy
Milvus 首次启动需初始化 etcd + minio，`start_period: 60s`。若超时：
```bash
docker compose logs milvus
docker compose restart milvus
```

### Q2: Elasticsearch 启动失败 `max virtual memory areas`
未设置内核参数，执行：
```bash
sudo sysctl -w vm.max_map_count=262144
docker compose restart elasticsearch
```

### Q3: 后端报 `Connection refused` 连不上中间件
检查中间件是否 healthy：
```bash
docker compose ps
```
backend 配置了 `depends_on: condition: service_healthy`，会等中间件就绪才启动。
若仍失败，确认 `.env` 中服务名与 docker-compose 一致（`DB_HOST=mysql` 等）。

### Q4: 后端启动成功但 LLM/Agent 报错
`.env` 中 API Key 未配置（仍为占位符 `sk-your-xxx`），修改后重启：
```bash
docker compose restart backend
```

### Q5: RabbitMQ 管理界面登录失败
默认账号 `erag/erag123`（非 guest）。guest 仅限 localhost，跨容器不可用，故用自定义账号。
账号由 `.env` 的 `RABBITMQ_USERNAME/PASSWORD` 控制，修改后需删数据重建：
```bash
docker compose down rabbitmq
rm -rf data/rabbitmq
docker compose up -d rabbitmq
```

### Q6: 端口冲突（80/8080/3306 等被占用）
修改 `.env` 端口映射变量（如 `FRONTEND_PORT=8081`），重启即可。

### Q7: 前端访问 502 Bad Gateway
后端未就绪，Nginx 反代失败。等后端 `Started KnowledgeApplication` 后刷新：
```bash
docker compose logs --tail=50 backend
```

### Q8: MySQL 建表未执行
`init.sql` 仅在 MySQL 数据目录为空（首次启动）时执行。若需重新建表：
```bash
docker compose down
rm -rf data/mysql
docker compose up -d mysql
```

---

## 九、生产部署建议（演示项目可略）

1. **密码强随机**：`.env` 所有密码换为强随机值，`JWT_SECRET` 至少 32 字节随机串
2. **HTTPS**：Nginx 配置 SSL 证书，或前置云负载均衡
3. **ES 安全**：开启 `xpack.security.enabled=true` + 配置账号密码
4. **资源限制**：已配置 `deploy.resources.limits`（CPU/内存），详见 docker-compose.yml
5. **独立管理端口**：Actuator 端点用独立 `management.server.port`，不暴露到公网
6. **备份**：定期备份 `data/mysql`（mysqldump）与 `data/minio`（文件）
7. **日志收集**：接 ELK / Loki 集中收集 `logs/` 目录
8. **Sentinel Dashboard**：生产部署 Sentinel Dashboard 容器，动态推送限流/熔断规则
9. **监控告警**：部署 Prometheus + Grafana，导入 `deploy/monitoring/grafana-dashboard.json`
10. **双写一致性**：后端已内置定时检查（每小时），自动补偿 Milvus/ES 索引不一致
11. **数据库迁移**：使用 Flyway 管理增量变更，详见 `db/migration/` 目录

### 资源限制配置

docker-compose.yml 已为所有服务配置资源限制：

| 服务 | CPU 限制 | 内存限制 |
|------|---------|---------|
| etcd | 1.0 | 512M |
| MySQL | 2.0 | 2G |
| Milvus | 2.0 | 4G |
| Redis | 0.5 | 256M |
| Elasticsearch | 2.0 | 2G |
| Backend | 4.0 | 2G |

### 监控部署

```bash
# 1. 启动 Prometheus + Grafana
docker compose -f docker-compose.monitoring.yml up -d

# 2. 访问 Grafana
open http://localhost:3000

# 3. 导入 ERAG 面板
# 左侧菜单 → Dashboards → Import → 上传 deploy/monitoring/grafana-dashboard.json
```

---

> 📌 **本地开发调试**（中间件用 Docker、后端/前端用 IDE 跑）请参见 [docs/本地部署调试手册.md](../docs/本地部署调试手册.md)
