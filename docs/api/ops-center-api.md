# AI 运维中心接口文档

> 模块：`knowledge-agent`（接口层）+ `knowledge-ai`（核心实现）
> 基路径：`/api/ops`（`server.servlet.context-path=/api`）
> 认证：所有接口需在请求头携带 `Authorization: Bearer <JWT>`，JWT 中 `tenantId` 为多租户隔离权威来源。

---

## 1. 架构概览

AI 运维中心由四大组件协同，覆盖**采集 → 追踪 → 分析 → 告警**全链路可观测：

```
                     ┌─────────────────┐
   RAG/Agent/Workflow│  MetricsCollector │  AOP @InfraMetric + MQ 手工埋点
   LLM/Milvus/ES/MQ ─▶  (指标采集)       │  异步落库 infra_metric
                     └────────┬────────┘
                              │
                     ┌────────▼────────┐
                     │  TraceService    │  Span(AutoCloseable) + TraceContext(TTL)
                     │  (链路追踪)       │  父子 span 栈 + MDC，采样率可控
                     └────────┬────────┘  异步落库 ops_trace
                              │
            ┌─────────────────┼─────────────────┐
            ▼                 ▼                 ▼
   ┌────────────────┐ ┌───────────────┐ ┌────────────────┐
   │  CostAnalyzer   │ │ OpsDashboard  │ │ OpsAlertEvaluator│
   │  (费用分析)      │ │  (7指标看板)  │ │  (阈值告警)      │
   │  预算/预测/占比  │ │  聚合查询     │ │  @Scheduled 5min │
   └────────────────┘ └───────────────┘ └────────┬───────┘
                                                │ 超阈值
                                       ┌────────▼────────┐
                                       │  AlertService    │  冷却去重 + Webhook
                                       │  (告警发送)       │  复用既有告警能力
                                       └─────────────────┘
```

### 7 项监控指标

| # | 指标 | 数据来源 | 说明 |
|---|------|----------|------|
| 1 | 模型调用次数 | `ai_call_log` | LLM 调用计数（含成功/失败） |
| 2 | Token 消耗 | `ai_call_log` | prompt + completion token 累计 |
| 3 | Agent 耗时 | `agent_task` | 已完成任务平均执行耗时（ms） |
| 4 | Milvus 查询 | `infra_metric` | 向量检索调用次数 / 平均耗时 |
| 5 | ES 查询 | `infra_metric` | BM25 词法检索调用次数 / 平均耗时 |
| 6 | RabbitMQ 延迟 | `infra_metric` | MQ 消费延迟（now - enqueueTime，ms） |
| 7 | Workflow 成功率 | `workflow_task` | COMPLETED / 总数 × 100% |

### 核心数据表

| 表 | 用途 | 写入方 | 特性 |
|----|------|--------|------|
| `infra_metric` | 基础设施指标（Milvus/ES/MQ） | MetricsCollector 异步 | 审计型 append-only，无软删 |
| `ops_trace` | 分布式链路 span | TraceService 异步 | traceId + spanId 栈，按采样写 |
| `alert_rule` | 告警阈值规则 | AlertRuleController CRUD | 有软删，支持启停切换 |

> DDL 见 [doc/sql/ops_schema.sql](sql/ops_schema.sql)，与 `deploy/init.sql` 同步。

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
| 1001 | 参数校验失败（@Valid） |
| 1002 | 业务异常 |
| 5000 | 系统异常 |

---

## 3. 运维看板接口

### 3.1 运维看板（7 指标聚合）

`GET /ops/dashboard`

聚合 7 项指标卡片 + 模型调用趋势（30 天）+ 费用趋势（30 天）+ 资源延迟（7 天），一次请求渲染整个看板。

**响应** `OpsDashboardVo`：

```json
{
  "cards": [
    { "key": "modelCalls", "title": "模型调用次数", "value": 1280, "unit": "次", "sub": "成功 1265 / 失败 15" },
    { "key": "tokenUsage", "title": "Token 消耗", "value": 580000, "unit": "tok", "sub": "输入 420000 / 输出 160000" },
    { "key": "agentDuration", "title": "Agent 平均耗时", "value": 8600, "unit": "ms", "sub": "已完成 42 个" },
    { "key": "milvusQueries", "title": "Milvus 查询", "value": 960, "unit": "次", "sub": "平均 120ms" },
    { "key": "esQueries", "title": "ES 查询", "value": 320, "unit": "次", "sub": "平均 45ms" },
    { "key": "mqLatency", "title": "MQ 延迟", "value": 210, "unit": "ms", "sub": "消费 80 条" },
    { "key": "workflowSuccessRate", "title": "Workflow 成功率", "value": 96.5, "unit": "%", "sub": "完成 77 / 总 80" }
  ],
  "callTrend": [ { "day": "2026-08-01", "calls": 120, "tokens": 50000, "cost": 0.85 } ],
  "costTrend": [ { "day": "2026-08-01", "calls": 120, "tokens": 50000, "cost": 0.85 } ],
  "latencyByResource": [
    { "resource": "milvus:search", "calls": 960, "avgDurationMs": 120, "failedCount": 3 },
    { "resource": "es:search", "calls": 320, "avgDurationMs": 45, "failedCount": 0 }
  ]
}
```

---

## 4. 基础设施指标接口

### 4.1 基础设施指标（Milvus/ES/MQ 概览 + 时序）

`GET /ops/metrics/infra`

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| start | String | 否 | 起始时间 `yyyy-MM-dd HH:mm:ss`，默认最近 7 天 |
| end | String | 否 | 结束时间 |

**响应** `InfraOverview`：

```json
{
  "overview": [
    { "resource": "milvus:search", "calls": 960, "successCount": 957, "failedCount": 3, "avgDurationMs": 120, "maxDurationMs": 850 },
    { "resource": "mq:parse", "calls": 80, "successCount": 80, "failedCount": 0, "avgDurationMs": 210, "maxDurationMs": 500 }
  ],
  "series": [ { "day": "2026-08-01", "resource": "milvus:search", "calls": 120, "avgDurationMs": 115 } ]
}
```

### 4.2 Agent 耗时统计

`GET /ops/metrics/agent`

**响应** `AgentStatsVo`：

```json
{
  "total": 50, "completed": 42, "failed": 8,
  "avgDurationMs": 8600, "maxDurationMs": 25000, "tokens": 320000,
  "dailyTrend": [ { "day": "2026-08-01", "total": 5, "completed": 4, "avgDurationMs": 8200 } ]
}
```

### 4.3 Workflow 成功率统计

`GET /ops/metrics/workflow`

**响应** `WorkflowStatsVo`：

```json
{
  "total": 80, "completed": 77, "failed": 3, "successRate": 96.25, "tokens": 580000,
  "dailyTrend": [ { "day": "2026-08-01", "total": 10, "completed": 9, "successRate": 90.0 } ]
}
```

---

## 5. 费用分析接口

### 5.1 费用趋势（按日）

`GET /ops/cost/trend`

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| start | String | 否 | 起始时间，默认最近 30 天 |
| end | String | 否 | 结束时间 |

**响应** `List<CostDailyPoint>`：

```json
[ { "day": "2026-08-01", "calls": 120, "tokens": 50000, "cost": 0.85 } ]
```

### 5.2 按模型费用占比

`GET /ops/cost/by-model`

**响应** `List<CostModelPoint>`：

```json
[ { "model": "deepseek-chat", "calls": 800, "tokens": 400000, "cost": 6.40 },
  { "model": "qwen-plus", "calls": 480, "tokens": 180000, "cost": 4.50 } ]
```

### 5.3 预算校验

`GET /ops/cost/budget`

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| budget | BigDecimal | 否 | 预算阈值（元），缺省取 `ops.alert.monthly-budget` |

**响应** `CostBudgetStatus`：

```json
{ "monthCost": 10.90, "budget": 100, "exceeded": false, "usedPercent": 10.9 }
```

### 5.4 月度费用预测

`GET /ops/cost/forecast`

按最近 7 天日均外推至月末。**响应** `CostForecast`：

```json
{
  "monthToDate": 10.90,
  "dailyAvg7d": 0.55,
  "remainingDays": 18,
  "forecast": 20.80,
  "note": "基于近7天日均外推"
}
```

---

## 6. 链路追踪接口

### 6.1 链路分页查询

`GET /ops/traces/page`

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| pageNo | Integer | 否 | 页码，默认 1 |
| pageSize | Integer | 否 | 每页条数，默认 20 |
| traceId | String | 否 | 精确匹配 traceId |
| spanType | String | 否 | span 类型：ROOT/SEARCH/LLM/TOOL/MQ |
| start | String | 否 | 起始时间 |
| end | String | 否 | 结束时间 |

**响应** `IPage<OpsTrace>`：

```json
{
  "records": [
    {
      "id": 1001, "tenantId": 1, "traceId": "a1b2c3...", "spanId": "d4e5f6...",
      "parentSpanId": null, "spanName": "rag.ask", "spanType": "ROOT",
      "startTime": "2026-08-02 14:30:00", "durationMs": 3200, "status": "OK",
      "attributesJson": "{\"kbId\":1}", "createTime": "2026-08-02 14:30:03"
    }
  ],
  "total": 150, "current": 1, "size": 20
}
```

### 6.2 单 trace 的 span 树

`GET /ops/traces/{traceId}`

返回递归 span 树（ROOT 为根，children 为子 span），前端用 `el-tree` 渲染调用链路。

**响应** `TraceTreeVo`：

```json
{
  "traceId": "a1b2c3...", "spanId": "d4e5f6...", "parentSpanId": null,
  "spanName": "rag.ask", "spanType": "ROOT",
  "startTime": "2026-08-02 14:30:00", "durationMs": 3200, "status": "OK",
  "attributesJson": "{\"kbId\":1}",
  "children": [
    {
      "traceId": "a1b2c3...", "spanId": "g7h8i9...", "parentSpanId": "d4e5f6...",
      "spanName": "milvus.search", "spanType": "SEARCH",
      "startTime": "2026-08-02 14:30:01", "durationMs": 120, "status": "OK",
      "attributesJson": null, "children": []
    }
  ]
}
```

> **span 类型**：ROOT（链路根）/ SEARCH（Milvus/ES 检索）/ LLM（模型调用）/ TOOL（工具调用）/ MQ（消息消费）。

---

## 7. 告警规则管理接口

`@RequestMapping /ops/alert-rules`，写操作加 `@OperLog` 审计。

### 7.1 规则列表

`GET /ops/alert-rules`

**响应** `List<AlertRule>`：

```json
[ {
  "id": 1, "tenantId": 1, "name": "Milvus 慢查询",
  "resource": "milvus:search", "metric": "latency_p95", "operator": "GT",
  "threshold": 500, "windowMinutes": 5, "level": "WARN", "enabled": 1,
  "createTime": "2026-08-02 10:00:00", "updateTime": "2026-08-02 10:00:00"
} ]
```

### 7.2 新建规则

`POST /ops/alert-rules`

**请求体** `AlertRuleRequest`：

```json
{
  "name": "Milvus 慢查询",
  "resource": "milvus:search",
  "metric": "latency_p95",
  "operator": "GT",
  "threshold": 500,
  "windowMinutes": 5,
  "level": "WARN",
  "enabled": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| name | String | 是 | 规则名称 |
| resource | String | 是 | 资源名，对齐 AlertService.source（见下表） |
| metric | String | 是 | 指标名（见下表） |
| operator | String | 是 | 比较符：GT / GTE / LT / LTE |
| threshold | BigDecimal | 是 | 阈值 |
| windowMinutes | Integer | 否 | 统计窗口（分钟），默认 5 |
| level | String | 否 | 触发级别：INFO / WARN / CRITICAL，默认 WARN |
| enabled | Integer | 否 | 1 启用 / 0 停用，默认 1 |

### 7.3 更新规则

`PUT /ops/alert-rules/{id}`

请求体同 7.2。

### 7.4 删除规则

`DELETE /ops/alert-rules/{id}`

软删（`deleted=1`）。

### 7.5 启停切换

`PUT /ops/alert-rules/{id}/enabled?enabled=true`

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| enabled | Boolean | 是 | true 启用 / false 停用 |

---

## 8. 告警规则配置参考

### 8.1 资源 × 指标路由

评估器按 `resource` 前缀路由取数，按 `metric` 选择统计方式：

| resource 前缀 | 取数来源 | 支持的 metric | 说明 |
|---------------|----------|--------------|------|
| `llm:`（如 `llm:chat`） | `ai_call_log` | `calls` / `token_usage` | 窗口内调用次数 / Token 求和 |
| `llm:` | `AiCallHealthTracker` | `error_rate` | 内存滑动窗口错误率（%，1 分钟窗口，零 SQL） |
| `milvus:` / `es:` / `mq:` | `infra_metric` | `calls` / `error_rate` / `latency_p95` | 窗口内调用次数 / 错误率 / P95 耗时 |

> 不支持的 `resource+metric` 组合返回 NaN，**NaN 永不触发告警**。

### 8.2 比较符语义

| operator | 语义 | 触发条件 |
|----------|------|----------|
| GT | 大于 | actual > threshold |
| GTE | 大于等于 | actual >= threshold |
| LT | 小于 | actual < threshold |
| LTE | 小于等于 | actual <= threshold |

### 8.3 规则示例

| 场景 | resource | metric | operator | threshold | window | level |
|------|----------|--------|----------|-----------|--------|-------|
| Milvus 慢查询 | `milvus:search` | `latency_p95` | GT | 500 | 5 | WARN |
| ES 高错误率 | `es:search` | `error_rate` | GTE | 10 | 5 | WARN |
| LLM 调用激增 | `llm:chat` | `calls` | GT | 1000 | 5 | WARN |
| LLM 错误率告警 | `llm:chat` | `error_rate` | GTE | 30 | 1 | CRITICAL |
| MQ 消费积压延迟 | `mq:parse` | `latency_p95` | GT | 5000 | 5 | WARN |

### 8.4 评估机制

- **定时评估**：`@Scheduled(cron = "${ops.alert.eval-cron:0 */5 * * * ?}")`，默认每 5 分钟扫描所有启用规则。
- **异常隔离**：单规则评估抛异常仅 warn，不影响其他规则。
- **告警发送**：复用 `AlertService.alert()`，同 `source+title` 在冷却时间内（默认 60s）去重，避免告警风暴。
- **级别过滤**：低于 `alert.min-level`（默认 WARN）的告警直接忽略。

---

## 9. 配置项

### 9.1 运维中心配置（`ops.*`）

```yaml
ops:
  trace:
    enabled: ${OPS_TRACE_ENABLED:true}        # 链路追踪总开关
    sample-rate: ${OPS_TRACE_SAMPLE_RATE:1.0} # 采样率 [0,1]，1.0=全采
  alert:
    eval-cron: ${OPS_ALERT_EVAL_CRON:0 */5 * * * ?}  # 告警评估周期
    monthly-budget: ${OPS_MONTHLY_BUDGET:100} # 月度预算阈值（元）
```

### 9.2 告警服务配置（`alert.*`）

```yaml
alert:
  enabled: ${ALERT_ENABLED:true}              # 告警总开关
  webhook-enabled: ${ALERT_WEBHOOK_ENABLED:false}  # Webhook 推送开关
  webhook-url: ${ALERT_WEBHOOK_URL:}          # Feishu/DingTalk 机器人地址
  cooldown-seconds: ${ALERT_COOLDOWN_SECONDS:60}   # 同源同标题冷却时间
  min-level: WARN                             # 触发告警的最小级别
```

### 9.3 埋点接入说明

| 组件 | 埋点方式 | 落库表 |
|------|----------|--------|
| Milvus/ES 检索 | `@InfraMetric(resource=MILVUS_SEARCH/ES_SEARCH)` AOP 自动计时 | `infra_metric` |
| MQ 消费延迟 | `MetricsCollector.recordMqLatency(resource, enqueueTime, success, err)` 手工埋点 | `infra_metric` |
| RAG 入口 | `traceService.startRoot("rag.ask")` 建 ROOT span | `ops_trace` |
| LLM 调用 | `traceService.startSpan("llm.chat", SpanType.LLM)` 子 span | `ops_trace` |
| 检索调用 | `traceService.startSpan("milvus.search", SpanType.SEARCH)` 子 span | `ops_trace` |

> **AOP 与 Trace 解耦**：`@InfraMetric` AOP 计指标（高频，每次外部调用都写 `infra_metric`）；TraceService 计链路（按需采样写 `ops_trace`）。两者并存，互不影响。

---

## 10. 前端页面

| 菜单 | 路由 | 说明 |
|------|------|------|
| 运维看板 | `/aiops/dashboard` | 7 指标卡片 + ECharts 趋势/延迟图 |
| 链路追踪 | `/aiops/trace` | span 分页列表 + trace 树（el-tree） |
| 告警规则 | `/aiops/alert` | 规则 CRUD + 启停切换 |
| 调用日志 | `/aiops/log` | AI 调用日志分页（既有） |
| 调用统计 | `/aiops/stats` | 调用统计排行（既有） |

> 前端 API 客户端 `src/api/ops.ts`，类型定义在 `src/types/api.d.ts`（`Ops*` / `Cost*` / `AlertRule*` 系列）。
