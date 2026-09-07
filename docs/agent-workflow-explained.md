# 多 Agent 与工作流 — 彻底搞懂

> 本文档用"大白话 + 代码 + 图"把多 Agent 协作和工作流引擎讲透。

---

## 一、先搞清楚：Agent 到底是什么？

**Agent = 一个有特定角色的"AI 工人"**。

你可以把它理解为公司里的一个岗位：
- PlannerAgent = 项目经理（负责拆解任务）
- KnowledgeAgent = 资料搜集员（负责找资料）
- AnalysisAgent = 分析师（负责分析资料）
- ReportAgent = 写手（负责写报告）

每个 Agent 都是**无状态的 Spring 单例**，意思是它自己不记住任何东西，所有信息都通过"工单"（AgentContext）传递。

```java
// Agent 接口非常简单：接收工单，返回结果
public interface Agent {
    AgentType type();                      // 我是谁（角色）
    AgentResult execute(AgentContext ctx);  // 我要干什么（执行）
}
```

---

## 二、多 Agent 协作 — 线性流水线模式

### 2.1 整体流程：像工厂流水线

```
用户提交任务："分析公司2024年HR政策变化"
         │
         ▼
    ┌─────────────────────────────────────────────────────────────┐
    │                    AgentExecutor（流水线调度器）                │
    │                                                             │
    │   步骤1        步骤2         步骤3         步骤4             │
    │  ┌─────┐    ┌─────┐      ┌─────┐      ┌─────┐             │
    │  │Planner│──▶│Knowledge│──▶│Analysis│──▶│Report │             │
    │  │规划者  │   │检索者    │   │分析者   │   │撰写者  │             │
    │  └─────┘    └─────┘      └─────┘      └─────┘             │
    │     │           │            │            │                 │
    │     ▼           ▼            ▼            ▼                 │
    │   产出 PLAN   产出 EVIDENCES  产出 ANALYSIS  产出 REPORT      │
    │     │           │            │            │                 │
    │     └───────────┴────────────┴────────────┘                 │
    │                    全部写入 AgentContext                      │
    └─────────────────────────────────────────────────────────────┘
```

### 2.2 AgentContext — "工单"在流水线上传递

```java
public class AgentContext {
    private final Long taskId;       // 任务ID
    private final Long tenantId;     // 租户ID
    private final Long userId;       // 用户ID
    private final Long kbId;         // 知识库ID
    private final String goal;       // 用户目标（如"分析HR政策变化"）

    // 关键：产物传递 Map
    // key = 产物类型名（"PLAN" / "EVIDENCES" / "ANALYSIS" / "REPORT"）
    // value = 产物内容（Plan对象 / List<Evidence> / String）
    private final Map<String, Object> artifacts = new LinkedHashMap<>();
}
```

**数据怎么流动？** 看这个具体例子：

```
步骤1: PlannerAgent.execute(ctx)
  ├── 输入：ctx.getGoal() = "分析HR政策变化"
  ├── 调用 LLM 生成检索计划
  └── 输出：ctx.putArtifact("PLAN", plan对象)
        ↓ 此时 ctx 中有：{PLAN: {searchQueries: ["HR政策2024", "HR政策变化对比", ...]}}

步骤2: KnowledgeAgent.execute(ctx)
  ├── 输入：ctx.getArtifact("PLAN")  ← 读取 Planner 的产物！
  ├── 按 plan.searchQueries 逐条检索知识库
  └── 输出：ctx.putArtifact("EVIDENCES", evidence列表)
        ↓ 此时 ctx 中有：{PLAN: ..., EVIDENCES: [证据1, 证据2, ...]}

步骤3: AnalysisAgent.execute(ctx)
  ├── 输入：ctx.getArtifact("EVIDENCES")  ← 读取 Knowledge 的产物！
  ├── 调用 LLM 分析证据
  └── 输出：ctx.putArtifact("ANALYSIS", 分析文本)
        ↓ 此时 ctx 中有：{PLAN: ..., EVIDENCES: ..., ANALYSIS: "分析结论..."}

步骤4: ReportAgent.execute(ctx)
  ├── 输入：ctx.getArtifact("ANALYSIS")  ← 读取 Analysis 的产物！
  ├── 调用 LLM 生成报告
  └── 输出：ctx.putArtifact("REPORT", 报告文本)
        ↓ 最终 ctx 中有：{PLAN: ..., EVIDENCES: ..., ANALYSIS: ..., REPORT: "最终报告..."}
```

### 2.3 核心代码：AgentExecutor.execute()

```java
public AgentTask execute(Long taskId) {
    AgentContext ctx = new AgentContext(task, props);  // 创建工单

    // 按 @Order(1)→(2)→(3)→(4) 顺序遍历所有 Agent
    int idx = 0;
    for (Agent agent : agents) {
        idx++;

        // 预算检查：步数、时长、Token
        if (idx > props.getMaxSteps()) { failTask(...); return; }
        if (超时) { failTask(...); return; }

        // 执行当前 Agent
        AgentResult result = agent.execute(ctx);

        // 失败 → 立即终止（fail-fast）
        if (!result.isSuccess()) {
            failTask(taskId, "STEP_FAILED", agent.type() + ":" + result.getErrorMessage());
            return;
        }

        // 成功 → 产物写入 Context + 持久化到数据库
        if (result.getArtifactKey() != null) {
            ctx.putArtifact(result.getArtifactKey(), result.getArtifact());
            taskManager.recordArtifact(...);  // 写入 agent_artifact 表
        }

        // Token 预算累加
        ctx.addTokens(result.getTokensUsed());
    }

    // 全部完成 → 取出 REPORT 作为最终结果
    taskManager.completeTask(taskId, ctx.getArtifact("REPORT"));
}
```

### 2.4 四个 Agent 各自干了什么？

#### PlannerAgent（@Order(1)）— 规划者

```java
@Component
@Order(1)
public class PlannerAgent implements Agent {
    @Override
    public AgentResult execute(AgentContext ctx) {
        // 1. 加载用户历史记忆（如果有 sessionId）
        String memoryText = loadMemoryBestEffort(ctx);

        // 2. 调用 LLM 生成结构化检索计划
        Plan plan = llmCaller.callEntity(
            SYSTEM_PROMPT,      // "你是企业知识库任务规划者..."
            buildUserPrompt(),  // "用户目标：分析HR政策变化\n请生成检索计划。"
            Plan.class          // LLM 输出 JSON → 自动解析为 Plan 对象
        );

        // 3. Plan 包含：
        //    - understanding: "用户想了解2024年HR政策的变化"
        //    - searchQueries: ["HR政策2024", "HR政策变化对比", "人力资源新规"]
        //    - analysisApproach: "按年度对比关键条款变化"

        return AgentResult.success("PLAN", plan, "查询数:3");
    }
}
```

#### KnowledgeAgent（@Order(2)）— 检索者

```java
@Component
@Order(2)
public class KnowledgeAgent implements Agent {
    @Override
    public AgentResult execute(AgentContext ctx) {
        // 1. 从 Context 读取 Planner 的产物
        Plan plan = ctx.getArtifact("PLAN");

        // 2. 逐条执行检索
        Map<String, Evidence> dedup = new LinkedHashMap<>();
        for (String query : plan.getSearchQueries()) {
            // 调用 KnowledgeSearchTool（混合检索 + 权限过滤）
            List<Evidence> hits = knowledgeSearchTool.search(query, kbId, userId, topK);
            for (Evidence e : hits) {
                dedup.putIfAbsent(e.getChunkId(), e);  // 按 chunkId 去重
            }
        }

        // 3. 返回去重后的证据列表
        return AgentResult.success("EVIDENCES", new ArrayList<>(dedup.values()));
    }
}
```

**注意**：KnowledgeAgent **不调用 LLM**！它是确定性检索，只调用 RAG 检索服务。

#### AnalysisAgent（@Order(3)）— 分析者

```java
@Component
@Order(3)
public class AnalysisAgent implements Agent {
    @Override
    public AgentResult execute(AgentContext ctx) {
        // 1. 读取上游产物
        List<Evidence> evidences = ctx.getArtifact("EVIDENCES");
        Plan plan = ctx.getArtifact("PLAN");

        // 2. 把证据格式化成文本
        String evidenceBlock = formatEvidence(evidences);
        // [1] 文件:HR政策2024.pdf (chunk=abc123, score=0.92)
        //     内容: 公司年假制度规定...
        // ---
        // [2] 文件:HR政策变化说明.docx (chunk=def456, score=0.88)
        //     内容: 2024年HR政策主要变化...

        // 3. 调用 LLM 分析
        LlmResult llm = llmCaller.call(
            "你是企业知识库分析者。基于检索到的资料，进行对比、归纳、推理...",
            "用户目标：分析HR政策变化\n分析方向：按年度对比\n\n检索资料：\n" + evidenceBlock
        );

        return AgentResult.success("ANALYSIS", llm.text(), "分析长度:" + llm.text().length());
    }
}
```

#### ReportAgent（@Order(4)）— 撰写者

```java
@Component
@Order(4)
public class ReportAgent implements Agent {
    @Override
    public AgentResult execute(AgentContext ctx) {
        // 1. 读取分析结论和证据（用于标注引用来源）
        String analysis = ctx.getArtifact("ANALYSIS");
        List<Evidence> evidences = ctx.getArtifact("EVIDENCES");

        // 2. 调用 LLM 生成报告（要求标注引用）
        LlmResult llm = llmCaller.call(
            "你是报告撰写者。基于分析结果生成报告，每个结论标注 [来源:文件名#chunkId]...",
            "分析结果：\n" + analysis + "\n\n引用源：\n" + formatCitations(evidences)
        );

        return AgentResult.success("REPORT", llm.text());
    }
}
```

---

## 三、多 Agent 协作 — 图编排模式（进阶）

线性流水线是固定的 4 步，但有些任务需要**更复杂的编排**（如分支、跳转）。这就是图编排模式。

### 3.1 AgentGraph — 用图描述执行顺序

```
默认图（和流水线一样）：
  START → planner → knowledge → analysis → report → END

自定义图（可以有分支跳转）：
  START → planner → knowledge → analysis → (分析充分?)
                                              │
                                    ┌─── 是 ──┴── 否 ───┐
                                    ▼                    ▼
                                  report        knowledge(补充检索)
                                    │                    │
                                    ▼                    ▼
                                   END              analysis
```

### 3.2 图编排的核心代码

```java
// orchestrator/AgentExecutor.java 的核心循环
AgentNode node = graph.startNode();  // 从起始节点开始

while (node != null) {
    // 带重试 + 硬超时执行节点
    AgentResult result = executeWithRetry(node, ctx, ...);

    if (!result.isSuccess()) {
        // 失败 → 触发 Saga 补偿回滚！
        finishFail(taskId, ctx, executed, ...);
        return;
    }

    // 成功 → 记录已执行节点（回滚用）+ 产物写入 Context
    executed.push(new ExecutedNode(nodeId, agentType, runId));
    ctx.putArtifact(result.getArtifactKey(), result.getArtifact());

    // 推进到下一个节点
    node = graph.nextOf(node);
}
```

### 3.3 节点级重试 + 硬超时

```java
private AgentResult executeWithRetry(AgentNode node, AgentContext ctx, ...) {
    int maxRetries = node.getMaxRetries();    // 如 2
    long timeoutMs = node.getTimeoutMs();      // 如 30000ms

    int attempt = 0;
    while (true) {
        attempt++;

        if (timeoutMs > 0) {
            // 提交到线程池，带超时等待
            Future<AgentResult> future = pool.submit(() -> agent.execute(ctx));
            try {
                result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);  // 超时 → 中断执行
                failMsg = "节点硬超时 " + timeoutMs + "ms";
            }
        }

        if (result.isSuccess()) {
            return result;  // 成功，退出
        }

        if (attempt <= maxRetries) {
            sleep(backoff * attempt);  // 线性退避：第1次等1s，第2次等2s
            continue;                  // 重试
        }

        return failure;  // 重试耗尽，返回失败
    }
}
```

### 3.4 Saga 补偿回滚 — 为什么需要？

**场景**：任务执行到步骤3失败了，但步骤1和2已经产生了副作用（如发送了邮件、创建了报告）。

```
步骤1(创建报告) ✅ → 步骤2(发送邮件) ✅ → 步骤3(更新状态) ❌ 失败！
                                                          │
                                                触发 Saga 补偿回滚
                                                          │
                            补偿2(撤回邮件) ← 补偿1(标记报告无效) ← 逆序执行
```

**代码**：

```java
private void rollback(Long taskId, Deque<ExecutedNode> executed, AgentContext ctx) {
    // 逆序遍历已成功的节点
    while (!executed.isEmpty()) {
        ExecutedNode en = executed.pop();  // 后进先出（逆序）

        Agent agent = agentMap.get(en.agentType);

        // 如果 Agent 实现了 Compensable 接口，执行补偿
        if (agent instanceof Compensable cb) {
            Compensation comp = cb.compensation(ctx);
            comp.execute(ctx);  // 执行补偿操作
        }
        // 没实现 Compensable 的 Agent（只读操作如 KnowledgeAgent），
        // 记一条空补偿，保证审计完整
    }
}
```

**哪些 Agent 需要补偿？**
- KnowledgeAgent：纯检索，无副作用 → **不需要补偿**
- AnalysisAgent：纯 LLM 分析，无副作用 → **不需要补偿**
- 如果有 Agent 会"发邮件"、"写数据库" → **需要实现 Compensable 接口**

---

## 四、声明式工作流引擎 — 和多 Agent 有什么区别？

### 4.1 核心区别

| | 多 Agent（AgentExecutor） | 工作流（WorkflowExecutor） |
|---|---|---|
| **定义方式** | 代码写死 4 步 | JSON 声明节点序列 |
| **节点类型** | 只有 Agent | TOOL / LLM / HUMAN / START / END |
| **灵活性** | 固定流水线 | 任意组合 |
| **人工介入** | 不支持 | HUMAN 节点暂停等待审批 |
| **状态持久化** | 任务级 | **节点级**（每步存 context_json，崩溃可恢复） |

### 4.2 工作流的节点类型

```
┌──────────────────────────────────────────────────────────────┐
│                     工作流节点类型                              │
├──────────┬───────────────────────────────────────────────────┤
│ START    │ 流程起点，不做任何事                                │
│ TOOL     │ 调用一个工具（如 knowledge_search、email_send）     │
│ LLM      │ 调用大模型生成文本                                  │
│ HUMAN    │ 暂停流程，等待人工审批后继续                         │
│ END      │ 流程终点，输出最终结果                               │
└──────────┴───────────────────────────────────────────────────┘
```

### 4.3 工作流执行流程图

```
JSON 定义的工作流：
{
  "nodes": [
    {"id": "n1", "type": "TOOL",   "toolName": "knowledge_search", "arguments": {"query": "${goal}"}, "outputKey": "docs"},
    {"id": "n2", "type": "LLM",    "promptTemplate": "基于以下文档分析：${docs}", "outputKey": "analysis"},
    {"id": "n3", "type": "HUMAN",  "name": "人工审批", "outputKey": "approval"},
    {"id": "n4", "type": "LLM",    "promptTemplate": "根据审批意见 ${approval} 生成最终报告：${analysis}", "outputKey": "report"}
  ]
}

执行过程：
  ┌─────┐     ┌─────┐     ┌─────┐     ┌─────┐
  │TOOL │────▶│ LLM │────▶│HUMAN│────▶│ LLM │
  │检索  │     │分析  │     │审批  │     │报告  │
  └─────┘     └─────┘     └─────┘     └─────┘
    │           │           │           │
    ▼           ▼           ▼           ▼
  docs="..."  analysis="..." approved    report="..."
  写入ctx     写入ctx      写入ctx      写入ctx
              存context_json 存context_json 存context_json
```

### 4.4 核心代码：WorkflowExecutor.runFrom()

```java
private void runFrom(WorkflowTask task, WorkflowDefinitionModel model,
                     String startNodeId, ApprovalInput approval) {

    // 1. 构建/恢复上下文（崩溃恢复：从数据库读 context_json）
    WorkflowContext ctx = new WorkflowContext(task);
    ctx.deserializeVariables(objectMapper, task.getContextJson());

    NodeDefinition node = resolveStartNode(model, startNodeId);

    while (node != null) {
        // END → 收尾
        if (node.getType() == NodeType.END) {
            taskManager.completeTask(taskId, resolveResult(ctx, node));
            return;
        }

        // START → 跳过
        if (node.getType() == NodeType.START) {
            node = model.nextOf(node);
            continue;
        }

        // HUMAN → 人工审批门
        if (node.getType() == NodeType.HUMAN) {
            if (approval != null) {
                // 有审批结果 → 回写并继续
                if (approval.approved()) {
                    node = model.nextOf(node);    // 通过 → 下一个节点
                } else {
                    node = idx.get(node.getRejectNext());  // 驳回 → 跳到驳回节点
                }
                continue;
            } else {
                // 无审批结果 → 暂停！
                taskManager.pauseForHuman(taskId, node.getId());  // 状态改为 WAITING_HUMAN
                saveProgress(task, ctx, node.getId());             // 保存当前进度到数据库
                return;  // 方法返回，流程暂停，等待人工调用 resume()
            }
        }

        // TOOL / LLM → 带重试执行
        NodeExecutionResult r = executeWithRetry(node, ctx, task);
        if (!r.isSuccess()) {
            taskManager.failTask(taskId, "NODE_FAILED", ...);
            return;
        }

        // 产物写入上下文
        ctx.setVariable(node.getOutputKey(), r.getOutput());

        // 每步执行后都保存进度（崩溃可恢复）
        saveProgress(task, ctx, node.getId());

        // 推进到下一个节点
        node = model.nextOf(node);
    }
}
```

### 4.5 HUMAN 节点 — 人工审批门的详细流程

```
执行到 HUMAN 节点：
  │
  ├── 1. 创建 node_run 记录（status=WAITING_HUMAN）
  ├── 2. 更新 task 状态为 WAITING_HUMAN
  ├── 3. 保存 context_json 到数据库（当前所有变量）
  └── 4. return（方法返回，流程暂停）

  ... 此时前端显示"等待审批" ...

审批人操作：
  │
  ├── approve(comment="同意，请发布")
  │   └── WorkflowExecutor.resume(taskId, true, userId, "同意，请发布")
  │       ├── 从数据库恢复 context_json
  │       ├── ctx.setVariable("approval", "同意，请发布")
  │       ├── 继续执行下一个节点
  │       └── ...
  │
  └── reject(comment="内容需要修改")
      └── WorkflowExecutor.resume(taskId, false, userId, "内容需要修改")
          ├── 跳到 rejectNext 节点（如果有）
          └── 没有 rejectNext → 取消流程
```

### 4.6 TOOL 节点 — 怎么调用工具？

```java
// ToolNodeHandler.java
public NodeExecutionResult handle(NodeDefinition node, WorkflowContext ctx) {
    // 1. 解析参数中的变量引用 ${var} → 实际值
    var resolvedArgs = variableResolver.resolveArgs(node.getArguments(), ctx);
    // 如 {"query": "${goal}"} → {"query": "分析HR政策变化"}

    // 2. 构建身份上下文
    ToolContext toolCtx = ToolContext.builder()
            .tenantId(ctx.getTenantId())
            .userId(ctx.getUserId())
            .kbId(ctx.getKbId())
            .build();

    // 3. 委托 ToolExecutor 执行（含权限校验、审计、异常兜底）
    ToolResult result = toolExecutor.execute(node.getToolName(), toolCtx, resolvedArgs);

    return NodeExecutionResult.success(result.getData(), result.getTokensUsed());
}
```

### 4.7 LLM 节点 — 怎么调用大模型？

```java
// LlmNodeHandler.java
public NodeExecutionResult handle(NodeDefinition node, WorkflowContext ctx) {
    // 1. 变量插值：${docs} → 实际的文档内容
    String userPrompt = variableResolver.resolveString(node.getPromptTemplate(), ctx);

    // 2. 调用 LLM
    LlmResult llm = llmCaller.call(systemPrompt, userPrompt, "workflow", identity);

    // 3. 返回生成的文本
    return NodeExecutionResult.success(llm.text(), llm.totalTokens());
}
```

---

## 五、工具 SPI 机制 — 插件化设计

### 5.1 什么是 SPI？

SPI = Service Provider Interface，**服务提供者接口**。简单说就是：**你实现一个接口，Spring 自动发现并注册你**。

```
┌─────────────────────────────────────────────────────────┐
│                    ToolRegistry（工具注册中心）             │
│                                                         │
│   Spring 启动时自动扫描所有 @Component 的 Tool 实现        │
│                                                         │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│   │KnowledgeSearch│  │DocumentRead  │  │EmailSend     │  │
│   │Tool           │  │Tool          │  │Tool          │  │
│   │name:"knowledge│  │name:"document│  │name:"email   │  │
│   │_search"       │  │_read"        │  │_send"        │  │
│   └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                         │
│   registry = {                                           │
│     "knowledge_search" → KnowledgeSearchTool,            │
│     "document_read" → DocumentReadTool,                  │
│     "email_send" → EmailSendTool,                        │
│     ...                                                  │
│   }                                                      │
└─────────────────────────────────────────────────────────┘
```

### 5.2 Tool 接口定义

```java
public interface Tool {
    String name();                    // 工具名，如 "knowledge_search"
    String description();             // 描述，供 LLM 理解用途
    String parametersJsonSchema();    // 参数 JSON Schema，供 LLM 生成参数 + 框架校验
    boolean authRequired();           // 是否需要权限校验
    ToolResult execute(ToolContext ctx, Map<String, Object> arguments);  // 执行
}
```

### 5.3 KnowledgeSearchTool — 一个具体的工具实现

```java
@Component
public class KnowledgeSearchTool implements Tool {

    @Override
    public String name() { return "knowledge_search"; }

    @Override
    public String description() {
        return "在企业知识库中执行混合检索，返回权限过滤后的证据片段。";
    }

    @Override
    public String parametersJsonSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "query": {"type": "string", "description": "检索查询"},
                "topK": {"type": "integer", "description": "返回条数", "default": 8}
              },
              "required": ["query"]
            }""";
    }

    @Override
    public boolean authRequired() { return true; }  // 需要 KB 权限校验

    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        // 调用 RAG 检索（向量 + BM25 + 权限过滤）
        List<Evidence> evidences = search(query, ctx.getKbId(), ctx.getUserId(), topK);
        return ToolResult.success(evidences);
    }
}
```

### 5.4 新增工具有多简单？

只需 3 步：
1. 创建一个类，实现 `Tool` 接口
2. 加上 `@Component` 注解
3. 实现 5 个方法

```java
@Component
public class EmailSendTool implements Tool {
    @Override public String name() { return "email_send"; }
    @Override public String description() { return "发送邮件通知"; }
    @Override public String parametersJsonSchema() { return "{...}"; }
    @Override public boolean authRequired() { return false; }

    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args) {
        String to = (String) args.get("to");
        String subject = (String) args.get("subject");
        // 发送邮件...
        return ToolResult.success("邮件已发送");
    }
}
```

**零配置**，Spring 自动扫描，自动注册到 ToolRegistry。Agent 和 Workflow 都能直接调用。

---

## 六、总结对比

```
┌─────────────────────────────────────────────────────────────────┐
│                        ERAG 的两种编排方式                        │
├─────────────────────────┬───────────────────────────────────────┤
│    多 Agent 协作         │       声明式工作流                      │
│    (AgentExecutor)       │       (WorkflowExecutor)              │
├─────────────────────────┼───────────────────────────────────────┤
│  4 个固定角色 Agent      │  任意组合 TOOL/LLM/HUMAN 节点          │
│  代码定义执行顺序        │  JSON 声明节点序列                      │
│  产物通过 Context 传递   │  变量通过 context_json 传递             │
│  任务级持久化            │  节点级持久化（崩溃可恢复）              │
│  fail-fast（失败即停）   │  节点级重试 + 手动重试                  │
│  Saga 补偿回滚           │  HUMAN 节点人工审批                    │
│  适合：复杂分析任务      │  适合：标准化业务流程                   │
└─────────────────────────┴───────────────────────────────────────┘
```

### 一句话总结

- **多 Agent**：4 个 AI 工人按固定顺序流水线作业，每个人完成自己的活，把产物传给下一个人。
- **工作流**：用 JSON 定义一个流程图，里面可以调工具、调大模型、等人审批，每一步都存盘，崩了能恢复。
- **工具 SPI**：定义一个接口，加个注解，就能被 Agent 和工作流自动发现并调用——插件化设计。
