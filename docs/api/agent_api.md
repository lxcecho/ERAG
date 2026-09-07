# Agent 工作流接口文档

> 模块：`knowledge-agent`
> 基路径：`/api/agent`（`server.servlet.context-path=/api`）
> 认证：所有接口需在请求头携带 `Authorization: Bearer <JWT>`，JWT 中 `tenantId` 为多租户隔离权威来源。

---

## 1. 架构概览

```
用户目标
   │
   ▼
┌───────────┐     ┌──────────────┐     ┌──────────────┐     ┌────────────┐
│PlannerAgent│────▶│KnowledgeAgent │────▶│AnalysisAgent │────▶│ReportAgent │
│  任务拆解   │     │  知识检索      │     │  分析推理     │     │  报告生成   │
└───────────┘     └──────────────┘     └──────────────┘     └────────────┘
   产物: Plan         产物: Evidences       产物: Analysis       产物: Report
```

| Agent | 职责 | 是否调用 LLM | 产物 key |
|-------|------|:---:|----------|
| PlannerAgent | 理解目标，拆解为 2-5 条检索查询 | ✅ | `PLAN` |
| KnowledgeAgent | 按计划逐条检索，权限后过滤，chunk 去重 | ❌ | `EVIDENCES` |
| AnalysisAgent | 基于证据对比/归纳/推理，不足标注 `[资料不足]` | ✅ | `ANALYSIS` |
| ReportAgent | 生成结构化 Markdown 报告，结论带 `[来源:文件名#chunkId]` | ✅ | `REPORT` |

### 执行预算（防止资源耗尽）

| 预算维度 | 配置项 | 默认值 | 超限行为 |
|---------|--------|:---:|---------|
| 最大步数 | `agent.max-steps` | 12 | 任务标记 `FAILED`，错误码 `MAX_STEPS` |
| 工具调用次数 | `agent.max-tool-calls` | 30 | KnowledgeAgent 截断检索查询数 |
| token 累计 | `agent.max-tokens-per-task` | 60000 | 任务标记 `FAILED`，错误码 `TOKEN_BUDGET_EXCEEDED` |
| 任务时长 | `agent.task-timeout-seconds` | 300 | 任务标记 `FAILED`，错误码 `TIMEOUT` |

### 权限模型

- **KB 级**：启动任务前校验 `viewer` 权限（`KbPermissionService.checkViewer`），无权限返回 403。
- **文档级**：检索时由 `KnowledgeSearchTool` 内部 `DocPermissionService.filterDocIds` 做后过滤，与 RAG 对话走相同权限链路，Agent 无法越权。
- **租户隔离**：所有表含 `tenant_id`，MyBatis-Plus 拦截器自动注入；异步线程通过 `TransmittableThreadLocal` 传播。

---

## 2. 统一响应格式

所有接口返回 `Result<T>`：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { },
  "timestamp": 1785559664000
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 403 | 无权限（KB viewer 校验不通过） |
| 1001 | 参数校验失败（@Valid） |
| 1002 | 业务异常 |
| 5000 | 系统异常 |

---

## 3. 接口列表

### 3.1 启动 Agent 任务（异步）

```
POST /agent/start
```

**描述**：校验 KB viewer 权限后创建任务并异步执行，立即返回任务 ID。执行在后台线程池进行，前端通过轮询详情或 SSE 获取进度。

**请求体**（`AgentStartRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `kbId` | Long | ✅ | 知识库 ID（检索范围） |
| `goal` | String | ✅ | 用户目标（自然语言描述分析诉求） |
| `sessionId` | Long | ❌ | 关联会话 ID（便于关联对话上下文） |

**请求示例**：

```bash
curl -X POST http://localhost:8080/api/agent/start \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "kbId": 100,
    "goal": "分析2025销售政策相比2024的变化",
    "sessionId": 2001
  }'
```

**成功响应**（200）：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": 1785559664123456,
  "timestamp": 1785559664000
}
```

**权限不足**（`data` 为 null，`code=403`）：

```json
{
  "code": 403,
  "message": "无访问该知识库的权限",
  "timestamp": 1785559664000
}
```

**参数校验失败**（`code=1001`）：

```json
{
  "code": 1001,
  "message": "goal:任务目标不能为空",
  "timestamp": 1785559664000
}
```

---

### 3.2 任务详情（含步骤与产物）

```
GET /agent/{id}
```

**描述**：查询任务详情，包含步骤列表与结构化产物（plan / evidences / analysis / report）。

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 任务 ID |

**成功响应**（`AgentTaskVo`）：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1785559664123456,
    "kbId": 100,
    "goal": "分析2025销售政策相比2024的变化",
    "status": "COMPLETED",
    "result": "# 2025销售政策变化分析报告\n\n...",
    "stepCount": 4,
    "tokenUsage": 8200,
    "errorCode": null,
    "errorMsg": null,
    "createTime": "2026-08-01 20:00:00",
    "finishedTime": "2026-08-01 20:01:30",
    "steps": [
      {
        "stepIndex": 1,
        "agentType": "PLANNER",
        "status": "SUCCESS",
        "outputSummary": "理解:对比2025与2024销售政策 | 查询数:3 | 方向:按年度对比关键条款",
        "durationMs": 2100,
        "startedAt": "2026-08-01 20:00:00",
        "finishedAt": "2026-08-01 20:00:02",
        "errorMsg": null
      },
      {
        "stepIndex": 2,
        "agentType": "KNOWLEDGE",
        "status": "SUCCESS",
        "outputSummary": "召回证据 8 条（来自 3 个文档，查询 3 条）",
        "durationMs": 1500,
        "startedAt": "2026-08-01 20:00:02",
        "finishedAt": "2026-08-01 20:00:04",
        "errorMsg": null
      },
      {
        "stepIndex": 3,
        "agentType": "ANALYSIS",
        "status": "SUCCESS",
        "outputSummary": "分析完成，长度 1200",
        "durationMs": 45000,
        "startedAt": "2026-08-01 20:00:04",
        "finishedAt": "2026-08-01 20:00:49",
        "errorMsg": null
      },
      {
        "stepIndex": 4,
        "agentType": "REPORT",
        "status": "SUCCESS",
        "outputSummary": "报告生成完成，长度 2800",
        "durationMs": 41000,
        "startedAt": "2026-08-01 20:00:49",
        "finishedAt": "2026-08-01 20:01:30",
        "errorMsg": null
      }
    ],
    "artifacts": [
      { "artifactType": "PLAN", "payload": "{\"understanding\":\"...\",\"searchQueries\":[...]}" },
      { "artifactType": "EVIDENCES", "payload": "[{\"documentId\":1,\"source\":\"2025.pdf\",...}]" },
      { "artifactType": "ANALYSIS", "payload": "对比结论：..." },
      { "artifactType": "REPORT", "payload": "# 2025销售政策变化分析报告\n\n..." }
    ]
  },
  "timestamp": 1785559664000
}
```

**任务状态（`status`）枚举**：

| 状态 | 说明 | 是否终态 |
|------|------|:---:|
| `CREATED` | 已创建，待执行 | ❌ |
| `EXECUTING` | 执行中 | ❌ |
| `COMPLETED` | 全部步骤成功完成 | ✅ |
| `FAILED` | 步骤失败/异常/超预算 | ✅ |
| `CANCELED` | 用户取消 | ✅ |

**失败任务示例**（`status=FAILED`）：

```json
{
  "code": 200,
  "data": {
    "status": "FAILED",
    "errorCode": "TOKEN_BUDGET_EXCEEDED",
    "errorMsg": "token 累计 61200 超过预算 60000",
    "tokenUsage": 61200,
    "steps": [...]
  }
}
```

---

### 3.3 分页查询当前用户任务列表

```
GET /agent/page?current=1&size=10
```

**描述**：分页查询当前登录用户的 Agent 任务列表（轻量 VO，不含步骤/产物，避免 N+1）。

**查询参数**：

| 参数 | 类型 | 默认 | 说明 |
|------|------|:---:|------|
| `current` | long | 1 | 页码，从 1 开始 |
| `size` | long | 10 | 每页条数 |

**成功响应**：

```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1785559664123456,
        "kbId": 100,
        "goal": "分析2025销售政策相比2024的变化",
        "status": "COMPLETED",
        "stepCount": 4,
        "tokenUsage": 8200,
        "createTime": "2026-08-01 20:00:00",
        "finishedTime": "2026-08-01 20:01:30"
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

---

### 3.4 SSE 流式推送任务进度

```
GET /agent/{id}/stream
```

**描述**：前端通过 `EventSource` 订阅任务进度。每 1.5s 推送一次 `progress` 事件；任务进入终态后推送 `complete` 事件并关闭连接。

> **注意**：浏览器 `EventSource` API 无法设置自定义请求头，JWT token 需通过 query 参数传递。需将 `/agent/*/stream` 加入 `jwt.allow-query-token-paths` 白名单（或使用通用 SSE 路径匹配）。

**前端接入示例**：

```javascript
const es = new EventSource('/api/agent/1785559664123456/stream?token=<jwt>');

// 步骤进度（每 1.5s 一次，含完整任务 VO）
es.addEventListener('progress', (e) => {
  const task = JSON.parse(e.data);
  console.log(`状态: ${task.status}, 步骤数: ${task.stepCount}`);
  updateUI(task);
});

// 终态结果（COMPLETED / FAILED / CANCELED）
es.addEventListener('complete', (e) => {
  const task = JSON.parse(e.data);
  console.log('任务结束:', task.status);
  es.close();
});

es.onerror = (e) => {
  console.error('SSE 连接异常');
  es.close();
};
```

**事件格式**：

| 事件名 | data 内容 | 触发时机 |
|--------|----------|---------|
| `progress` | `AgentTaskVo`（含步骤+产物） | 每 1.5s 轮询，任务未终态时 |
| `complete` | `AgentTaskVo`（终态快照） | 任务进入 COMPLETED/FAILED/CANCELED |

**SSE 原始数据流示例**：

```
event:progress
data:{"id":1785559664123456,"status":"EXECUTING","stepCount":2,...}

event:progress
data:{"id":1785559664123456,"status":"EXECUTING","stepCount":3,...}

event:complete
data:{"id":1785559664123456,"status":"COMPLETED","result":"# 报告...","stepCount":4,...}
```

---

## 4. 错误码说明

| errorCode | 触发场景 | 说明 |
|-----------|---------|------|
| `MAX_STEPS` | 执行步数超过 `agent.max-steps` | 防止无限循环 |
| `TIMEOUT` | 任务 wall-clock 超过 `agent.task-timeout-seconds` | 单步内长耗时无法中断，下一轮校验兜底 |
| `TOKEN_BUDGET_EXCEEDED` | token 累计超过 `agent.max-tokens-per-task` | 防止 LLM 资源耗尽 |
| `STEP_FAILED` | 某 Agent 返回 `failure`（如未检索到资料） | 含 Agent 类型前缀，如 `KNOWLEDGE:未检索到任何资料` |
| `EXCEPTION` | Agent 执行抛出未预期异常 | 记录异常 message |
| `ASYNC_EXCEPTION` | 异步线程池执行异常 | 兜底保护 |

---

## 5. 数据库表结构

详见 [agent_schema.sql](sql/agent_schema.sql)，4 张表均含 `tenant_id` 实现多租户行级隔离：

| 表 | 说明 | 关键索引 |
|----|------|---------|
| `agent_task` | 任务主表（一次工作流 = 1 行） | `idx_tenant_user` / `idx_kb` / `idx_status` |
| `agent_step` | 步骤记录（审计/回放） | `idx_tenant_task` / `idx_task_index` |
| `agent_artifact` | 结构化产物（plan/evidences/analysis/report） | `idx_tenant_task` |
| `agent_message` | LLM/Tool 完整 IO（审计） | `idx_tenant_task` |

---

## 6. 配置参考

```yaml
# application.yml
agent:
  max-steps: 12                    # 单任务最大步数
  max-tool-calls: 30               # 单任务最大工具调用次数
  max-tokens-per-task: 60000       # 单任务 token 预算封顶
  search-top-k: 8                  # KnowledgeAgent 每轮返回证据条数
  task-timeout-seconds: 300        # 任务 wall-clock 超时
  retrieval:
    over-fetch-multiplier: 4       # 初召放大倍数（给权限后过滤留余量）
    min-over-fetch: 20             # 最小初召条数保底

# LLM 统一配置（LangChain4j，OpenAI 兼容协议；与 RAG 层共用）
ai:
  llm:
    base-url: ${LLM_BASE_URL:https://api.deepseek.com/v1}
    api-key: ${LLM_API_KEY:}
    model-name: ${LLM_MODEL:deepseek-chat}
```

> **注意**：未配置 `LLM_API_KEY` 时 Agent 接口调用会返回错误，但不影响其他模块功能（各模块共用同一 LLM 通道）。
