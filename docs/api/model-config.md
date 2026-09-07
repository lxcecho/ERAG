# 模型配置说明（LLM / Embedding）

> 本项目 LLM 与 Embedding 均基于 **OpenAI 兼容协议**（LangChain4j `OpenAiChatModel` / `OpenAiStreamingChatModel`），
> 通过 `application.yml`（本地默认）与 `deploy/.env`（Docker 部署）两组配置切换供应商，无需改代码。

---

## 1. 对话模型（LLM）

### 1.1 本地默认：Ollama + deepseek-r1（开发环境，无需外网）

`knowledge-admin/src/main/resources/application.yml`：

```yaml
ai:
  llm:
    base-url: ${LLM_BASE_URL:http://localhost:11434/v1}
    api-key: ${LLM_API_KEY:ollama}          # Ollama 不校验 key，任意非空占位
    model-name: ${LLM_MODEL:deepseek-r1:latest}
    stream-timeout: ${LLM_STREAM_TIMEOUT:600}
```

- 安装：`ollama pull deepseek-r1:latest`（8.2B Q4_K_M，131072 上下文，支持 thinking）
- 特点：本地免费推理，但生成较慢（每轮 2-4 分钟），流式超时已调大至 600s
- 计费：`ai.pricing.models.deepseek-r1:latest: { input: 0, output: 0 }`

### 1.2 云端切换：阿里云百炼（qwen-max / deepseek 系列）

通过环境变量覆盖默认值（本地 `java -jar` 或 Docker 均适用）：

```
LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
LLM_API_KEY=sk-xxx
LLM_MODEL=qwen-max            # 或 deepseek-v3 / deepseek-r1（百炼型号）
LLM_STREAM_TIMEOUT=300
```

`deploy/.env` 已按此配置（部署时生效）。

### 1.3 模型计费

`ai.pricing.models` 按模型名配置每千 token 单价（元），用于 AI 调用日志费用估算：
未命中模型走 `default-input/output`（0.002/0.006）。

---

## 2. 踩坑记录（重要）

### ⚠️ 阿里云专属 MaaS 网关不兼容 LangChain4j

`https://ws-xxxx.cn-beijing.maas.aliyuncs.com/compatible-mode/v1`（专属工作空间网关）虽然名为 compatible-mode，
但**同步与流式均返回非标准格式**：

```json
{"finish_reason":"stop","text":"..."}
```

而 LangChain4j 期望标准 OpenAI 格式（`choices[0].message.content` / SSE `delta.content`），
导致同步调用报 `Chat completion failed: no choices returned in response`、流式收不到 token。

**解法**：改用阿里云百炼标准 OpenAI 兼容端点 `https://dashscope.aliyuncs.com/compatible-mode/v1`（同一 API Key 可用），返回标准格式。

---

## 3. Embedding 模型

| 场景 | 配置 | 说明 |
|------|------|------|
| **本地默认** | `http://localhost:11434/v1` + `bge-m3:latest` | 1024 维，与 Milvus 集合匹配，本地推理无费用 |
| 在线备选 | `https://dashscope.aliyuncs.com/compatible-mode/v1` + `text-embedding-v3` | 1024 维，需外网与 API Key；切换后旧向量失效，需重新向量化已有文档 |

`deploy/.env` 使用本地 `bge-m3:latest`（1024 维）：

```
EMBEDDING_BASE_URL=http://localhost:11434/v1
EMBEDDING_API_KEY=ollama
EMBEDDING_MODEL=bge-m3:latest
EMBEDDING_DIMENSION=1024
```

> `EMBEDDING_DIMENSION` 同时用于 `ai.embedding.dimension` 与 `ai.milvus.dimension`，必须一致，否则 Milvus 集合维度不匹配。

---

## 4. 超时配置

| 配置 | 默认 | 说明 |
|------|:---:|------|
| `ai.llm.stream-timeout` | 600 | 流式 SSE 最长保持时间（秒）；deepseek-r1 本地推理需调大 |
| `ai.embedding.timeout-seconds` | 180 | 批量向量化大文档超时（本地 CPU 慢） |

---

## 5. 验证方式

```bash
# 检查 Ollama 已拉取的模型
curl http://localhost:11434/v1/models

# 直连验证 OpenAI 兼容协议（标准端点）
curl -s https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions \
  -H "Authorization: Bearer $LLM_API_KEY" -H "Content-Type: application/json" \
  -d '{"model":"qwen-max","messages":[{"role":"user","content":"你好"}]}'

# 后端健康检查（就绪后可联调）
curl http://localhost:8080/api/actuator/health
```
