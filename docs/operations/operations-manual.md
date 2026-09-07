# ERAG 运维手册

> 本文档涵盖 ERAG 系统的日常运维、监控告警、故障排查和性能调优。

---

## 一、运维架构

### 1.1 监控体系

```
┌─────────────────────────────────────────────────────────────┐
│                    监控体系                                    │
├─────────────────────────────────────────────────────────────┤
│  应用层                                                       │
│  ├─ Actuator：健康检查 + 指标暴露                              │
│  ├─ Prometheus：指标采集                                      │
│  ├─ Grafana：可视化面板                                       │
│  └─ 自定义 AIOps：7 指标看板 + 链路追踪 + 告警                  │
│                                                               │
│  中间件层                                                      │
│  ├─ MySQL：慢查询日志 + 错误日志                               │
│  ├─ Redis：INFO 命令 + SLOWLOG                               │
│  ├─ Elasticsearch：_cluster/health + _nodes/stats             │
│  ├─ RabbitMQ：Management API + 队列监控                       │
│  └─ Milvus：metrics 端点                                      │
│                                                               │
│  容器层                                                       │
│  ├─ Docker：docker stats                                      │
│  └─ cAdvisor：容器资源监控                                     │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 服务清单

| 服务 | 容器名 | 端口 | 健康检查 |
|------|--------|------|---------|
| Frontend | erag-frontend | 80 | HTTP 200 |
| Backend | erag-backend | 8080 | /actuator/health |
| MySQL | erag-mysql | 3306 | mysqladmin ping |
| Redis | erag-redis | 6379 | redis-cli ping |
| Elasticsearch | erag-es | 9200 | curl http://localhost:9200 |
| RabbitMQ | erag-rabbitmq | 5672 | rabbitmq-diagnostics ping |
| Milvus | erag-milvus | 19530 | gRPC health |
| MinIO | erag-minio | 9000 | HTTP 200 |

---

## 二、日常运维

### 2.1 服务管理

#### 启动服务

```bash
# 启动全部服务
docker compose up -d

# 启动单个服务
docker compose up -d backend

# 重建并启动（代码更新后）
docker compose up -d --build backend frontend
```

#### 停止服务

```bash
# 停止全部（保留数据）
docker compose down

# 停止并删除数据卷（慎用！）
docker compose down -v
```

#### 查看状态

```bash
# 查看所有服务状态
docker compose ps

# 查看服务日志
docker compose logs -f backend
docker compose logs --tail=100 backend

# 查看资源使用
docker stats
```

### 2.2 数据备份

#### MySQL 备份

```bash
# 全量备份
docker compose exec mysql mysqldump -uroot -proot123 knowledge_ai > backup_$(date +%Y%m%d).sql

# 恢复
docker compose exec -T mysql mysql -uroot -proot123 knowledge_ai < backup_20260806.sql

# 定时备份（crontab）
0 2 * * * cd /path/to/erag && docker compose exec -T mysql mysqldump -uroot -proot123 knowledge_ai > /backup/mysql_$(date +\%Y\%m\%d).sql
```

#### MinIO 备份

```bash
# 备份文件存储
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin123
docker compose exec minio mc cp --recursive local/knowledge-files /backup/minio/

# 恢复
docker compose exec minio mc cp --recursive /backup/minio/ local/knowledge-files/
```

#### Redis 备份

```bash
# 触发 RDB 快照
docker compose exec redis redis-cli BGSAVE

# 复制 RDB 文件
docker compose cp redis:/data/dump.rdb /backup/redis/
```

### 2.3 日志管理

#### 日志位置

```
deploy/logs/
├── backend/        # 后端日志（knowledge-admin.log + error.log）
├── mysql/          # MySQL 日志
├── milvus/         # Milvus 日志
├── es/             # Elasticsearch 日志
├── rabbitmq/       # RabbitMQ 日志
├── minio/          # MinIO 日志
└── nginx/          # Nginx 日志
```

#### 日志级别调整

```bash
# 动态调整后端日志级别（无需重启）
curl -X POST http://localhost:8080/api/actuator/loggers/com.knowledge \
  -H 'Content-Type: application/json' \
  -d '{"configuredLevel": "DEBUG"}'

# 恢复
curl -X POST http://localhost:8080/api/actuator/loggers/com.knowledge \
  -H 'Content-Type: application/json' \
  -d '{"configuredLevel": "INFO"}'
```

#### 日志清理

```bash
# 清理 30 天前的日志
find deploy/logs -name "*.log" -mtime +30 -delete

# 配置 logback 自动清理（已配置）
# <maxHistory>30</maxHistory>
# <totalSizeCap>10GB</totalSizeCap>
```

---

## 三、监控告警

### 3.1 Prometheus 指标

#### 访问地址

- Prometheus: http://localhost:9090
- 后端指标: http://localhost:8080/api/actuator/prometheus

#### 关键指标

| 指标 | 说明 | 告警阈值 |
|------|------|---------|
| `http_server_requests_seconds_count` | HTTP 请求总数 | - |
| `http_server_requests_seconds_sum` | HTTP 请求总耗时 | P95 > 5s |
| `jvm_memory_used_bytes` | JVM 内存使用 | > 80% |
| `hikaricp_connections_active` | 数据库活跃连接 | > 80% |
| `redis_commands_duration_seconds` | Redis 命令延迟 | > 100ms |

### 3.2 Grafana 监控面板

#### 导入面板

```bash
# 访问 Grafana
open http://localhost:3000

# 导入 ERAG 面板
# 1. 左侧菜单 → Dashboards → Import
# 2. 上传 deploy/monitoring/grafana-dashboard.json
# 3. 选择 Prometheus 数据源
```

#### 面板说明

| 面板 | 内容 |
|------|------|
| JVM 内存使用 | Heap Used / Heap Max |
| HTTP 请求 QPS | 按 URI 分组的请求速率 |
| HTTP 响应时间 | P95 延迟 |
| AI 调用统计 | 总调用次数 / 成功率 |
| 数据库连接池 | Active / Idle / Pending |
| Redis 操作延迟 | 按命令分组的平均延迟 |

### 3.3 自定义 AIOps 监控

#### 访问地址

- 运维看板: http://localhost → AI 运维 → 运维看板
- 链路追踪: http://localhost → AI 运维 → 链路追踪
- 告警规则: http://localhost → AI 运维 → 告警规则
- 调用日志: http://localhost → AI 运维 → 调用日志

#### 7 项监控指标

| 指标 | 说明 |
|------|------|
| 模型调用次数 | LLM API 调用总量 |
| Token 消耗 | Prompt + Completion tokens |
| Agent 耗时 | Agent 任务执行时间 |
| Milvus 查询 | 向量检索延迟 |
| ES 查询 | BM25 检索延迟 |
| RabbitMQ 延迟 | 消息队列处理延迟 |
| Workflow 成功率 | 工作流执行成功率 |

### 3.4 告警配置

#### 告警规则

```sql
-- 插入告警规则
INSERT INTO alert_rule (name, resource, metric, operator, threshold, duration, enabled)
VALUES ('LLM 调用失败率过高', 'llm', 'error_rate', '>', 0.1, 300, 1);
```

#### 告警通道

支持 Webhook 告警，可对接：

- 钉钉机器人
- 飞书机器人
- 企业微信机器人
- 邮件通知

---

## 四、故障排查

### 4.1 常见问题

#### Q1: 后端启动失败

```bash
# 查看启动日志
docker compose logs backend

# 常见原因：
# 1. 数据库连接失败 → 检查 MySQL 是否 healthy
# 2. 端口冲突 → 检查 8080 是否被占用
# 3. 配置错误 → 检查 .env 文件
```

#### Q2: Milvus 连接失败

```bash
# 检查 Milvus 状态
docker compose ps milvus
docker compose logs milvus

# 常见原因：
# 1. etcd 未就绪 → 等待 etcd 启动
# 2. 内存不足 → 增加 Milvus 内存限制
# 3. 端口冲突 → 检查 19530 是否被占用
```

#### Q3: Elasticsearch 启动失败

```bash
# 检查 ES 日志
docker compose logs elasticsearch

# 常见原因：
# 1. vm.max_map_count 不足
sudo sysctl -w vm.max_map_count=262144

# 2. 内存不足
# 调整 ES_JAVA_OPTS
```

#### Q4: RabbitMQ 消息积压

```bash
# 查看队列状态
docker compose exec rabbitmq rabbitmqctl list_queues name messages consumers

# 常见原因：
# 1. 消费者宕机 → 重启 backend
# 2. 消费速度慢 → 增加消费者数量
# 3. 消息处理异常 → 查看死信队列
```

#### Q5: Redis 内存不足

```bash
# 查看 Redis 内存
docker compose exec redis redis-cli info memory

# 清理过期数据
docker compose exec redis redis-cli FLUSHDB

# 调整内存策略
# docker-compose.yml 中 --maxmemory 512mb
```

### 4.2 性能排查

#### 慢查询排查

```bash
# MySQL 慢查询
docker compose exec mysql mysql -uroot -proot123 -e "SHOW VARIABLES LIKE 'slow_query_log';"
docker compose exec mysql mysql -uroot -proot123 -e "SHOW VARIABLES LIKE 'long_query_time';"

# 查看慢查询日志
docker compose exec mysql cat /var/log/mysql/slow.log
```

#### JVM 性能排查

```bash
# 查看 JVM 参数
docker compose exec backend java -XX:+PrintFlagsFinal -version | grep -E "HeapSize|MetaspaceSize"

# 生成 heap dump
docker compose exec backend jmap -dump:format=b,file=/tmp/heap.hprof 1

# 复制出来分析
docker compose cp backend:/tmp/heap.hprof ./heap.hprof
```

#### 数据库连接池排查

```bash
# 查看连接池状态
curl http://localhost:8080/api/actuator/metrics/hikaricp.connections.active
curl http://localhost:8080/api/actuator/metrics/hikaricp.connections.idle
curl http://localhost:8080/api/actuator/metrics/hikaricp.connections.pending
```

---

## 五、性能调优

### 5.1 Embedding 批量大小

```yaml
# application.yml
ai:
  embedding:
    batch-size: 64  # 默认 64，可根据供应商调整
```

| 供应商 | 建议 batch-size |
|--------|----------------|
| dashscope | 25 |
| OpenAI | 128 |
| Ollama | 64 |

### 5.2 RAG 检索参数

```yaml
ai:
  rag:
    top-k: 5              # 最终返回结果数
    min-score: 0.6         # 相似度下限
    chunk-size: 800        # 切片大小
    chunk-overlap: 200     # 切片重叠
    hybrid:
      enabled: true        # 启用混合检索
      vector-top-k: 20     # 向量路召回数
      es-top-k: 20         # BM25 路召回数
      fusion:
        rrf-k: 60          # RRF 常数
```

### 5.3 数据库优化

#### 索引优化

```sql
-- 查看表索引
SHOW INDEX FROM chat_message;

-- 添加缺失索引
CREATE INDEX idx_message_session ON chat_message(session_id, create_time);
CREATE INDEX idx_calllog_tenant_time ON ai_call_log(tenant_id, create_time);
```

#### 连接池配置

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### 5.4 Redis 优化

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
```

### 5.5 JVM 优化

```bash
# docker-compose.yml 环境变量
JAVA_OPTS: >-
  -Xms1g -Xmx1g
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:+HeapDumpOnOutOfMemoryError
  -XX:HeapDumpPath=/tmp/heap.hprof
```

---

## 六、扩缩容

### 6.1 垂直扩展

增加单容器资源限制：

```yaml
# docker-compose.yml
deploy:
  resources:
    limits:
      cpus: '4.0'
      memory: 4G
    reservations:
      cpus: '1.0'
      memory: 1G
```

### 6.2 水平扩展

#### Backend 扩展

```bash
# 启动多个 backend 实例
docker compose up -d --scale backend=3

# Nginx 负载均衡配置
upstream backend {
    server backend:8080 weight=1;
    server backend:8081 weight=1;
    server backend:8082 weight=1;
}
```

#### 数据库读写分离

```yaml
spring:
  datasource:
    master:
      url: jdbc:mysql://master:3306/knowledge_ai
    slave:
      url: jdbc:mysql://slave:3306/knowledge_ai
```

---

## 七、灾备恢复

### 7.1 备份策略

| 数据 | 备份频率 | 保留时间 | 备份方式 |
|------|---------|---------|---------|
| MySQL | 每日 2:00 | 30 天 | mysqldump |
| Redis | 每小时 | 7 天 | RDB 快照 |
| MinIO | 每日 3:00 | 30 天 | mc cp |
| Milvus | 每日 4:00 | 7 天 | milvus-backup |

### 7.2 恢复流程

#### MySQL 恢复

```bash
# 1. 停止 backend
docker compose stop backend

# 2. 恢复数据库
docker compose exec -T mysql mysql -uroot -proot123 knowledge_ai < /backup/mysql_20260806.sql

# 3. 启动 backend
docker compose start backend
```

#### 全量恢复

```bash
# 1. 停止所有服务
docker compose down

# 2. 恢复数据卷
cp -r /backup/mysql/* deploy/data/mysql/
cp -r /backup/redis/* deploy/data/redis/
cp -r /backup/minio/* deploy/data/minio/

# 3. 启动服务
docker compose up -d
```

---

## 八、安全运维

### 8.1 密钥轮换

```bash
# 1. 生成新密钥
NEW_SECRET=$(openssl rand -base64 32)

# 2. 更新 .env
sed -i "s/JWT_SECRET=.*/JWT_SECRET=$NEW_SECRET/" .env

# 3. 重启 backend（所有实例）
docker compose restart backend
```

### 8.2 证书更新

```bash
# 1. 更新证书文件
cp /path/to/new/cert.pem deploy/nginx/ssl/
cp /path/to/new/key.pem deploy/nginx/ssl/

# 2. 重载 Nginx
docker compose exec frontend nginx -s reload
```

### 8.3 访问审计

```bash
# 查看登录日志
docker compose exec mysql mysql -uroot -proot123 knowledge_ai \
  -e "SELECT * FROM sys_oper_log WHERE business_type=0 ORDER BY oper_time DESC LIMIT 100;"

# 查看 API 调用统计
docker compose exec mysql mysql -uroot -proot123 knowledge_ai \
  -e "SELECT model_name, COUNT(*) as calls, SUM(prompt_tokens) as tokens FROM ai_call_log GROUP BY model_name;"
```

---

> 📌 返回：[架构文档](../architecture.md) | [部署文档](../../deploy/deploy.md) | [安全指南](../security/security-guide.md)
