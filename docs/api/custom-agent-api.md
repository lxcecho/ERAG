# 自定义 Agent 接口文档

> 模块：`knowledge-agent`（`com.knowledge.agent.custom`）
> 基路径：`/api/custom-agent`（`server.servlet.context-path=/api`）
> 认证：所有接口需在请求头携带 `Authorization: Bearer <JWT>`；定义仅创建者本人可管理，运行仅创建者可发起。
> 状态：已通过前后端联调脚本验证（PASS=22/22）。

---

## 1. 核心概念

### 1.1 执行模式（execMode）

| 模式 | 说明 | 返回方式 |
|------|------|---------|
| `single` | 单步流式对话：上下文注入提示词 → LLM 流式生成 → 落库 | SSE（`status→token×N→done`） |
| `multi` | 多步骤流程：按 `steps` 定义逐步执行，上一步产物可传入下一步 | 立即返回 `runId`，SSE 订阅进度 |

### 1.2 数据源模式（sourceMode / inputType）

| 模式 | 说明 | 运行请求字段 |
|------|------|------------|
| `kb` | 知识库检索（复用 RAG 检索 + 文档级权限过滤） | `kbId`（可覆盖定义绑定） |
| `content` | 自定义内容全文注入（≤ `custom-agent.content-limit` 截断） | `content` |
| `log` | 上传日志/文档分析 | `fileRef`（来自 `/upload`） |
| `plain` | 纯模型回答，不注入任何上下文（对话框关闭「知识库检索」开关时） | 无 |

> **数据源配置已简化**：定义不再配置「数据源/绑定知识库」（`sourceMode` 缺省 `kb`），
> 完全由对话框交互决定——**知识库检索开关始终显示**：开 → 下拉选择知识库（`kb`+`kbId`）；
> 关 → `plain`（纯模型回答）；拖入文件 → `log`+`fileRef`（优先于检索）。

### 1.3 日志上传两种注入模式

| 模式 | 判定 | 运行阶段行为 |
|------|------|------------|
| `full` | 文本 ≤ `full-text-limit`（默认 8000 字符） | 全文注入上下文 |
| `chunked` | 文本 > 阈值 | 切片 → 向量化 → Milvus（metadata `agentUploadId=fileRef`），按问题语义召回 TopK 切片 |

### 1.4 运行状态机

`CREATED`（待执行）→ `RUNNING`（执行中）→ `COMPLETED`（成功）/ `FAILED`（失败，带 `errorMsg`）。

---

## 2. 统一响应

所有非 SSE 接口返回 `Result<T>`：

```json
{ "code": 200, "message": "操作成功", "data": {}, "timestamp": 1785693932615, "success": true }
```

> 注意：雪花 ID（19 位）在 `data` 中为**字符串**（避免前端 JS 精度丢失），如 `"data":"2083981332129132545"`。

---

## 3. 定义管理

### 3.1 创建定义

```
POST /custom-agent
```

请求体（`AgentDefinitionRequest`）：

```json
{
  "name": "日志根因分析助手",
  "description": "上传日志定位根因",
  "systemPrompt": "你是日志分析专家。请基于以下日志分析问题根因：\n【日志】\n{context}\n【问题】\n{question}",
  "sourceMode": "log",
  "execMode": "single"
}
```

| 字段 | 必填 | 说明 |
|------|:---:|------|
| `name` | ✅ | 名称（≤64） |
| `description` | | 描述（≤512） |
| `avatar` | | 图标 URL |
| `systemPrompt` | ✅ | 系统提示词，支持 `{context}`/`{question}` 占位符 |
| `sourceMode` | ✅ | `kb`/`content`/`log` |
| `kbId` | sourceMode=kb | 绑定知识库 |
| `execMode` | ✅ | `single`/`multi` |
| `steps` | multi | 步骤定义 JSON 字符串（见 3.2） |

响应：`data` = 定义 ID（字符串）。

### 3.2 多步流程 steps 格式

`steps` 为 JSON **字符串**（示例即联调脚本实测通过）：

```json
[
  {
    "stepName": "摘要",
    "prompt": "请对以下内容做摘要，输出不超过100字：{context}",
    "inputFrom": "context",
    "outputKey": "summary"
  },
  {
    "stepName": "结论",
    "prompt": "基于摘要给出最终结论：{summary}",
    "inputFrom": "prev",
    "outputKey": "conclusion"
  }
]
```

| 字段 | 说明 |
|------|------|
| `stepName` | 步骤名 |
| `prompt` | 步骤提示词，`{context}` = 上下文，`{outputKey}` = 上一步产物 |
| `inputFrom` | `context`（使用全局上下文）/ `prev`（使用上一步产物） |
| `outputKey` | 本步骤产物 key，供后续步骤引用 |

### 3.3 编辑定义

```
PUT /custom-agent/{id}
```

请求体同创建（`id` 放入 Path）。**已发布（PUBLISHED）定义编辑后自动回落 DRAFT**，需重新发布。

### 3.4 发布 / 删除

```
POST /custom-agent/{id}/publish
DELETE /custom-agent/{id}       # 软删
```

### 3.5 分页查询

```
GET /custom-agent/page?current=1&size=10
```

返回当前用户的全部定义 + 租户内已发布定义，按创建时间倒序。

### 3.6 定义详情

```
GET /custom-agent/{id}
```

---

## 4. 日志/文档上传

```
POST /custom-agent/upload
Content-Type: multipart/form-data
参数：file（单文件，≤ custom-agent.max-upload-bytes 默认 10MB）
```

支持文本类后缀：`txt/log/md/json/csv/xml/yml/yaml/properties/conf/ini/html`。

响应（`UploadResultVo`）：

```json
{
  "fileRef": "2026/08/03/e5a0b2f0....log",
  "originalName": "app.log",
  "mode": "full",
  "charCount": 186,
  "chunkCount": 0
}
```

| 字段 | 说明 |
|------|------|
| `fileRef` | 运行请求 `fileRef` 使用（相对存储路径） |
| `mode` | `full` 全文 / `chunked` 切片检索 |
| `charCount` | 解析后文本字符数 |
| `chunkCount` | 切片数（chunked > 0） |

> 存储：独立目录 `custom-agent.data-path`（默认 `./data/agent-uploads`），不依赖知识库存储白名单（pdf/doc/docx/md），由 `CustomAgentStorageService` 提供。

---

## 5. 对话与执行

### 5.1 单步流式对话（SSE）

```
POST /custom-agent/{id}/chat
Content-Type: application/json
```

请求体（`ChatStartRequest`）：

```json
{ "inputType": "log", "question": "日志中数据库连接超时的原因是什么？", "fileRef": "2026/08/03/xxx.log" }
```

| 字段 | 必填 | 说明 |
|------|:---:|------|
| `question` | ✅ | 用户问题 |
| `inputType` | | `kb`/`content`/`log`/`plain`，缺省按定义 sourceMode；`plain` 为关闭检索的纯模型回答 |
| `kbId` | kb | 知识库 ID（可覆盖定义绑定） |
| `content` | content | 自定义内容文本 |
| `fileRef` | log | 上传文件引用 |
| `sessionId` | | 会话 ID（跨轮记忆）：首轮为空由后端生成，经 SSE `session` 事件回传；**后续轮次必须携带**，同会话多轮共享上下文 |

SSE 事件流：

```
event:session
data:2084128439163039745

event:status
data:generating

event:token
data:内容片段

...（token 事件 × N，逐片推送）

event:done
data:"<runId>"     ← 完整回答已落库，runId 为字符串
```

| 事件 | 数据 | 说明 |
|------|------|------|
| `session` | sessionId（纯文本，**首轮首个事件**） | 回传后端生成的会话 ID，前端须持久化并在后续轮次回传 |
| `status` | `generating` | 开始生成 |
| `token` | 文本片段 | 流式增量 |
| `done` | runId（字符串） | 回答完成并落库 |
| `error` | 错误信息 | 生成失败 |

### 5.1.1 跨轮记忆机制

- 首轮：请求不携带 `sessionId` → 后端生成雪花 ID，SSE `session` 事件最先回传；前端本地持久化（localStorage）。
- 每轮：调用记忆中心 `loadContext(sessionId, ...)` 装配历史记忆（近期对话 + 摘要 + 长期事实 + 向量召回），拼入 systemPrompt（`【用户历史记忆】` 段）。
- 每轮结束：`recordInteraction` 在 LLM 流式完成回调中记录 user/assistant 消息（`chat_message` 表），达阈值（每 10 轮）异步触发摘要与事实抽取。
- 多步（`multi`）：`run` 启动即创建/复用 `sessionId`，可通过 `GET /custom-agent/runs/{runId}` 回读 `sessionId` 用于后续轮次。

### 5.2 多步异步执行

```
POST /custom-agent/{id}/run
Content-Type: application/json
```

请求体同 5.1。立即返回 `data` = runId（字符串），后台线程池异步执行（显式租户上下文，`custom-agent.executor-pool-size` 线程池）。

### 5.3 多步执行进度（SSE）

```
GET /custom-agent/runs/{runId}/stream
```

| 事件 | 数据 | 说明 |
|------|------|------|
| `progress` | `AgentRunVo` JSON | 执行中状态（RUNNING）轮询推送（1.5s 间隔） |
| `complete` | `AgentRunVo` JSON | 最终状态（COMPLETED/FAILED） |
| `error` | 错误信息 | 异常 |

### 5.4 运行记录

```
GET /custom-agent/runs/page?current=1&size=10      # 当前用户分页
GET /custom-agent/runs/{runId}                     # 详情
```

`AgentRunVo` 关键字段：`id/agentId/agentName/inputType/question/contextRef/result/stepsResult/tokenUsage/status/errorMsg/createTime/finishedTime`。

---

## 6. 运行配置（custom-agent.*）

| 配置 | 默认值 | 说明 |
|------|:---:|------|
| `custom-agent.kb-top-k` | 5 | 知识库模式检索 TopK |
| `custom-agent.log-top-k` | 8 | 大文件切片检索 TopK |
| `custom-agent.full-text-limit` | 8000 | 全文注入阈值（字符） |
| `custom-agent.content-limit` | 8000 | content 模式最大注入字符 |
| `custom-agent.max-upload-bytes` | 10MB | 单文件上传上限 |
| `custom-agent.data-path` | `./data/agent-uploads` | 上传文件存储根目录 |
| `custom-agent.max-steps` | 5 | 多步流程步骤数上限 |
| `custom-agent.executor-pool-size` | 4 | 多步执行线程池大小 |
