# IDEA 启动后端指南（加载 deploy/.env）

> 目标：用 IDEA 打开项目并启动 `knowledge-admin` 后端时，自动读取 [deploy/.env](../deploy/.env) 的配置，与 docker compose / [start-backend.ps1](../deploy/start-backend.ps1) 保持同一套环境变量，避免重复维护。

---

## 一、核心原理（必读）

- **Spring Boot 不会自动读取 `.env` 文件**。`.env` 是 docker compose `env_file` 专用格式；后端 `application.yml` / `application-dev.yml` 中所有配置均为 `${VAR:default}` 占位符（见 [application.yml](../knowledge-backend/knowledge-admin/src/main/resources/application.yml)）。
- 因此「依赖 .env 启动」= 把 `.env` 的键值注入 IDEA 运行环境，本指南提供 3 种注入方式。
- **IDEA 环境变量优先级**：Run Configuration 的 `Environment variables` 字段 ＞ EnvFile 加载的 `.env` ＞ 系统环境变量 ＞ `application.yml` 默认值。

### ⚠️ 本地必踩的坑：容器主机名 vs localhost

`deploy/.env` 是为**容器内运行**准备的，服务名是容器名（`milvus`/`rabbitmq`/`elasticsearch`/`minio`）。**本地 IDE 跑后端时这些主机名不可达**，必须覆盖为 `localhost`：

| 变量 | .env 值（容器版） | 本地覆盖值 |
|---|---|---|
| `DB_HOST` | （dev 默认 localhost） | `localhost` |
| `MILVUS_HOST` | `milvus` | `localhost` |
| `RABBITMQ_HOST` | `rabbitmq` | `localhost` |
| `REDIS_HOST` | （默认 localhost） | `localhost` |
| `ES_URIS` | `http://elasticsearch:9200` | `http://localhost:9200` |
| `KB_STORAGE_MINIO_ENDPOINT` | `http://minio:9000` | `http://localhost:9000` |

> 其余变量（LLM/Embedding/账号密码）容器版与本地版通用，无需改动。

---

## 二、前置准备

1. **启动中间件**：MySQL / Milvus（含 etcd、milvus-minio）为硬依赖；Elasticsearch / RabbitMQ / Redis / MinIO 可按需启用（各有降级开关）。全部用 Docker 起：
   ```powershell
   cd deploy
   docker compose up -d mysql milvus elasticsearch rabbitmq redis minio
   ```
   > 详见 [本地部署调试手册](本地部署调试手册.md)。
2. **启动 Ollama**（Embedding 本地向量化）：
   ```powershell
   ollama serve
   ollama pull bge-m3:latest
   ```
3. **确认 .env 密钥有效**：`deploy/.env` 中 `LLM_API_KEY` 需为有效密钥（当前已配置百炼 maas 端点）。

---

## 三、方法一（推荐）：EnvFile 插件加载 .env

无需手工维护变量列表，改 `.env` 即生效，与部署配置天然一致。

1. **安装插件**：IDEA → `File` → `Settings` → `Plugins` → `Marketplace` → 搜索 **EnvFile** → `Install` → 重启 IDEA
2. **打开运行配置**：工具栏 `Edit Configurations...` → 选中 Spring Boot 配置 `KnowledgeApplication`（如未创建，`+` → `Spring Boot`，Main class 选 `com.knowledge.KnowledgeApplication`）
3. **加载 .env**：切到 **EnvFile** 标签页 → `+` → 选择 [deploy/.env](../deploy/.env) → 勾选启用
4. **覆盖容器主机名**（关键）：在 `Environment variables` 字段追加（优先级高于 EnvFile）：
   ```
   MILVUS_HOST=localhost;RABBITMQ_HOST=localhost;REDIS_HOST=localhost;ES_URIS=http://localhost:9200;KB_STORAGE_MINIO_ENDPOINT=http://localhost:9000
   ```
   `DB_HOST` 可省略（dev 配置默认 localhost）。
5. `Apply` → 启动运行，验证日志出现 `Started KnowledgeApplication` 且无连接报错。

---

## 四、方法二：手动粘贴环境变量（不装插件）

在 Run Configuration 的 `Environment variables` 字段粘贴以下清单（占位符替换为 `.env` 中实际值）：

```bash
LLM_BASE_URL=<.env 的 LLM_BASE_URL>;LLM_API_KEY=<.env 的 LLM_API_KEY>;LLM_MODEL=<.env 的 LLM_MODEL>;EMBEDDING_BASE_URL=http://localhost:11434/v1;EMBEDDING_API_KEY=ollama;EMBEDDING_MODEL=bge-m3:latest;EMBEDDING_DIMENSION=1024;MILVUS_HOST=localhost;RABBITMQ_HOST=localhost;RABBITMQ_USERNAME=erag;RABBITMQ_PASSWORD=erag123;REDIS_HOST=localhost;ES_URIS=http://localhost:9200;KB_STORAGE_MINIO_ENDPOINT=http://localhost:9000;KB_STORAGE_MINIO_ACCESS_KEY=minioadmin;KB_STORAGE_MINIO_SECRET_KEY=minioadmin123;KB_STORAGE_MINIO_BUCKET=knowledge-files
```

> 分隔符为分号 `;`；新版 IDEA 也可点 `Show env variables` 用表格逐条添加。

### 最小必需清单（其余走默认值）

后端 `application.yml`/`application-dev.yml` 已内置合理默认值（MySQL `localhost/root/root123`、Milvus/Redis/ES `localhost`、MinIO `localhost:9000` 等），因此**本地最快启动只需 3 个必改项**：

```bash
LLM_API_KEY=<你的密钥>;EMBEDDING_BASE_URL=http://localhost:11434/v1;EMBEDDING_API_KEY=ollama;EMBEDDING_MODEL=bge-m3:latest;EMBEDDING_DIMENSION=1024
```

> 若使用 Ollama 之外的 Embedding（如 OpenAI），需同步改 `EMBEDDING_BASE_URL` 并保证 `EMBEDDING_DIMENSION` 与 Milvus collection 维度一致（当前 1024）。

---

## 五、方法三：直接跑现有脚本（零配置）

项目已内置「读 .env → 覆盖 localhost → mvn 启动」的完整脚本 [start-backend.ps1](../deploy/start-backend.ps1)。在 IDEA 底部 `Terminal` 执行：

```powershell
powershell -ExecutionPolicy Bypass -File deploy\start-backend.ps1
```

- 优点：完全复用 .env，零手工维护，与部署一致
- 缺点：非 IDE 调试进程（无法在 IDEA 中直接断点），适合快速验证

---

## 六、启动验证与排障

### 验证清单

1. 后端日志出现 `Started KnowledgeApplication`（约 10-20s）
2. `http://localhost:8080/api/actuator/health` 返回 `{"status":"UP"}`
3. Swagger 文档：`http://localhost:8080/api/doc.html`
4. 登录接口可用：`POST /api/auth/login`（默认账号 admin/123456）

### 常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| `Connection refused` MySQL | DB_HOST 未指向 localhost 或 MySQL 未启动 | 检查覆盖变量；`docker compose ps` |
| Milvus 连不上 | MILVUS_HOST=milvus（容器名） | 覆盖 `MILVUS_HOST=localhost` |
| RabbitMQ 登录失败 | 容器版账号 erag/erag123 未设置 | 检查 RABBITMQ_USERNAME/PASSWORD 注入 |
| LLM 报 401/超时 | LLM_API_KEY 未注入或无效 | 确认 EnvFile/环境变量生效（可临时加 `-Dspring.profiles.active=dev` 观察） |
| 中文乱码 | 控制台编码非 UTF-8 | 启动前设置 `JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8`（脚本已内置） |
| 端口占用 8080 | 已有实例运行 | 改 `server.port` 或停掉旧进程 |

### 完整配置参考

所有环境变量及默认值见 [application.yml](../knowledge-backend/knowledge-admin/src/main/resources/application.yml) 与 [application-dev.yml](../knowledge-backend/knowledge-admin/src/main/resources/application-dev.yml)，模板见 [deploy/.env.example](../deploy/.env.example)。

---

> 相关文档：[本地部署调试手册](本地部署调试手册.md)（中间件启动）· [部署文档](../deploy/deploy.md)（容器化部署）· [架构设计](architecture.md)
