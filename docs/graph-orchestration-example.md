# 图编排模式 — 实例讲解

> 先搞清楚什么时候用线性模式，什么时候才需要图编排。

---

## 一、TV 日志分析 → JIRA：线性模式就够了

### 流程分析

```
TV 上报日志 → 解析日志 → 搜索知识库 → AI 分析根因 → 评论到 JIRA
```

这就是一条直线，没有分支、没有人工审批、不需要断点恢复。**用线性 AgentExecutor 完全够用**。

### 方案：Agent 流水线 + 后置工具调用

```
┌─────────────────────────────────────────────────────────────┐
│                 AgentExecutor（线性流水线）                    │
│                                                             │
│  PlannerAgent    KnowledgeAgent    AnalysisAgent   ReportAgent
│  (规划者)         (检索者)           (分析者)        (撰写者)
│     │                │                 │              │
│     ▼                ▼                 ▼              ▼
│   生成检索计划     搜索知识库          分析根因        生成分析报告
│     │                │                 │              │
│     └────────────────┴─────────────────┴──────────────┘
│                        产物通过 Context 传递
└─────────────────────────────────────────────────────────────┘
                                                         │
                                                         ▼
                                              ┌──────────────────┐
                                              │  JiraCommentTool  │
                                              │  把报告评论到JIRA  │
                                              └──────────────────┘
```

**核心思路**：Agent 负责"分析"，工具负责"执行动作"。分析完成后，调用工具把结果贴到 JIRA。

### 具体执行过程

```
用户提交任务："分析 TV 日志 [HDMI CEC timeout] 并贴到 JIRA TV-1234"
         │
         ▼
[步骤1] PlannerAgent (@Order(1))
  ├── 输入：goal = "分析 TV 日志 [HDMI CEC timeout] 并贴到 JIRA TV-1234"
  ├── LLM 生成计划：
  │   - understanding: "用户需要分析 HDMI CEC 超时日志的根因"
  │   - searchQueries: ["HDMI CEC timeout 解决方案", "PHY 信号抖动"]
  │   - analysisApproach: "从硬件 PHY 层和协议层两个角度分析"
  └── 产物：ctx.putArtifact("PLAN", plan)

[步骤2] KnowledgeAgent (@Order(2))
  ├── 输入：ctx.getArtifact("PLAN").searchQueries
  ├── 逐条检索知识库（混合检索 + 权限过滤）
  └── 产物：ctx.putArtifact("EVIDENCES", [证据1, 证据2, ...])

[步骤3] AnalysisAgent (@Order(3))
  ├── 输入：ctx.getArtifact("EVIDENCES") + ctx.getGoal()
  ├── LLM 分析根因
  └── 产物：ctx.putArtifact("ANALYSIS", "根因：设备0x5000的HDMI PHY在4K@60Hz下信号抖动...")

[步骤4] ReportAgent (@Order(4))
  ├── 输入：ctx.getArtifact("ANALYSIS") + ctx.getArtifact("EVIDENCES")
  ├── LLM 生成结构化报告（带引用标注）
  └── 产物：ctx.putArtifact("REPORT", "# HDMI CEC 超时分析报告\n\n## 根因\n...")

[步骤5] 后置动作（在 AgentExecutor.execute() 返回后执行）
  ├── 从 ctx 取出 REPORT
  ├── 调用 JiraCommentTool.execute(ctx, {"issue_key": "TV-1234", "comment": report})
  └── 完成
```

### 后置动作的实现方式

在调用 AgentExecutor 的上层代码中处理：

```java
// Controller 或 Service 层
public void analyzeAndPost(Long taskId, String jiraIssueKey) {
    // 1. Agent 流水线执行分析
    AgentTask task = agentExecutor.execute(taskId);

    // 2. 分析完成后，调用工具贴到 JIRA
    if ("COMPLETED".equals(task.getStatus())) {
        String report = task.getReport();  // 最终报告
        jiraCommentTool.execute(toolContext, Map.of(
            "issue_key", jiraIssueKey,
            "comment", report
        ));
    }
}
```

**简单、清晰、够用。**

---

## 二、什么时候才需要图编排？

线性模式搞不定的场景：

### 场景 1：条件分支

```
分析 JIRA Issue → 判断严重程度
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
     P0/P1 严重           P2/P3 一般
          │                   │
    自动创建WarRoom      自动分配给对应模块负责人
    通知所有相关人员      设置SLA提醒
```

**线性模式做不了**：Planner → Knowledge → Analysis → Report 是固定的，无法根据分析结果走不同分支。

### 场景 2：人工审批门

```
分析 JIRA Issue → 生成修复方案 → 人工审批方案 → 自动执行修复
                                         │
                                    等待审批人操作
                                    （可能等几小时）
```

**线性模式做不了**：流水线不支持暂停等人。

### 场景 3：多轮循环

```
分析 JIRA Issue → 执行修复 → 验证修复结果 → 通过？
                                              │
                                    ┌── 是 ───┴── 否 ───┐
                                    ▼                    ▼
                                   完成              重新分析 → 再次修复 → 再次验证
                                                        ↑                    │
                                                        └────────────────────┘
```

**线性模式做不了**：流水线是单向的，不支持回环。

### 场景 4：并行执行后汇聚

```
                ┌── 搜索知识库 ──┐
分析 JIRA Issue ┤                ├── 综合分析 → 生成报告
                └── 搜索JIRA历史 ─┘
```

**线性模式做不了**：流水线是串行的，不支持并行。

---

## 三、图编排的真正实例：JIRA Bug 智能分诊

### 场景

收到一个 JIRA Bug，需要：
1. 分析 Bug 描述，判断严重程度
2. **如果 P0/P1**：创建 WarRoom，通知所有人，等待人工确认处理方案，然后执行
3. **如果 P2/P3**：自动分配给模块负责人，设置 SLA 提醒

```
┌─────────┐
│  START   │
└────┬─────┘
     ▼
┌──────────────┐
│ analyze_bug  │  ← LLM：分析 Bug 描述，判断严重程度和所属模块
│ 分析Bug      │
└──────┬───────┘
       │ outputKey = "severity" (P0/P1/P2/P3)
       │ outputKey = "module" (HDMI/CEC/EDID/...)
       │ outputKey = "root_cause_guess"
       ▼
  ┌────┴────┐
  │severity?│
  └────┬────┘
       │
  ┌────┴────────────────────┐
  │                         │
  P0/P1                   P2/P3
  │                         │
  ▼                         ▼
┌──────────────┐    ┌──────────────┐
│create_warroom│    │assign_owner  │  ← TOOL：自动分配
│ 创建WarRoom   │    │ 分配负责人    │
└──────┬───────┘    └──────┬───────┘
       │                   │
       ▼                   ▼
┌──────────────┐    ┌──────────────┐
│notify_all    │    │set_sla       │  ← TOOL：设置SLA
│ 通知所有人    │    │ 设置SLA提醒   │
└──────┬───────┘    └──────┬───────┘
       │                   │
       ▼                   │
┌──────────────┐           │
│human_plan    │           │
│ 人工审批方案   │           │
└──────┬───────┘           │
       │                   │
  ┌────┴────┐              │
  │approved?│              │
  └────┬────┘              │
       │                   │
  ┌────┴────┐              │
  │         │              │
  yes       no             │
  │         │              │
  ▼         ▼              │
┌──────┐  ┌──────┐         │
│execute│  │reject │         │
│执行修复│  │驳回通知│         │
└──┬───┘  └──┬───┘         │
   │         │              │
   └────┬────┘              │
        │                   │
        ▼                   ▼
   ┌──────────┐
   │notify_end│
   │ 通知完成  │
   └────┬─────┘
        ▼
      END
```

### JSON 定义

```json
{
  "code": "jira_bug_triage",
  "name": "JIRA Bug 智能分诊",
  "nodes": [
    {"id": "start", "type": "START"},
    {
      "id": "analyze_bug",
      "type": "LLM",
      "name": "分析Bug",
      "systemPrompt": "你是软件Bug分诊专家。分析Bug描述，输出JSON：{\"severity\":\"P0/P1/P2/P3\",\"module\":\"模块名\",\"root_cause_guess\":\"初步根因猜测\"}",
      "promptTemplate": "Bug标题：${bug_summary}\nBug描述：${bug_description}\n\n请分析。",
      "outputKey": "analysis"
    },
    {
      "id": "create_warroom",
      "type": "TOOL",
      "toolName": "warroom_create",
      "arguments": {"title": "P0/P1 Bug: ${bug_summary}", "severity": "${severity}"},
      "outputKey": "warroom_id"
    },
    {
      "id": "notify_all",
      "type": "TOOL",
      "toolName": "send_notification",
      "arguments": {"channel": "all", "message": "⚠️ P0/P1 Bug: ${bug_summary}, WarRoom: ${warroom_id}"}
    },
    {
      "id": "human_plan",
      "type": "HUMAN",
      "name": "人工确认处理方案",
      "outputKey": "plan_approved"
    },
    {
      "id": "execute_fix",
      "type": "TOOL",
      "toolName": "jira_transition",
      "arguments": {"issue_key": "${jira_key}", "status": "In Progress", "comment": "方案已批准，开始修复"}
    },
    {
      "id": "reject_notify",
      "type": "TOOL",
      "toolName": "send_notification",
      "arguments": {"channel": "all", "message": "Bug ${jira_key} 处理方案被驳回"}
    },
    {
      "id": "assign_owner",
      "type": "TOOL",
      "toolName": "jira_assign",
      "arguments": {"issue_key": "${jira_key}", "module": "${module}"}
    },
    {
      "id": "set_sla",
      "type": "TOOL",
      "toolName": "jira_set_sla",
      "arguments": {"issue_key": "${jira_key}", "severity": "${severity}"}
    },
    {
      "id": "notify_end",
      "type": "TOOL",
      "toolName": "send_notification",
      "arguments": {"channel": "dingtalk", "message": "Bug ${jira_key} 分诊完成"}
    },
    {"id": "end", "type": "END"}
  ]
}
```

### 执行过程（P0 场景）

```
[0s]    analyze_bug (LLM)
          输入：bug_summary = "HDMI CEC 设备发现失败"
          输出：analysis = {"severity": "P0", "module": "CEC", "root_cause_guess": "PHY初始化异常"}
          → ctx.analysis = {...}

[2s]    create_warroom (TOOL)
          输入：title = "P0 Bug: HDMI CEC 设备发现失败"
          输出：warroom_id = "WR-2024-001"
          → ctx.warroom_id = "WR-2024-001"

[2.5s]  notify_all (TOOL)
          发送通知到所有渠道

[3s]    human_plan (HUMAN)
          → 暂停！等待审批人确认处理方案
          → 存盘：context_json = {"analysis":{...}, "warroom_id":"WR-2024-001"}

          ... 等待 30 分钟 ...

[30min] 审批人："同意，按方案执行"
          → resume(approved=true)

[30min+1s] execute_fix (TOOL)
             调用 JIRA API，将 Issue 状态改为 "In Progress"

[30min+2s] notify_end (TOOL)
             通知完成

[30min+3s] END
```

### 执行过程（P3 场景）

```
[0s]    analyze_bug (LLM)
          输出：severity = "P3", module = "EDID"
          → ctx.severity = "P3"

[2s]    assign_owner (TOOL)
          根据 module="EDID" 自动分配给 EDID 模块负责人

[2.5s]  set_sla (TOOL)
          设置 SLA：P3 级别，7 天内修复

[3s]    notify_end (TOOL)
          通知分诊完成

[3.5s]  END

          ← P3 场景走了一条完全不同的路径，没有 WarRoom，没有人工审批
```

---

## 四、总结：什么时候用什么

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  线性模式 (AgentExecutor)                                        │
│  ─────────────────────                                          │
│  适用：A → B → C → D 一条直线                                    │
│  例子：                                                         │
│    - TV 日志分析 → 贴 JIRA                                      │
│    - 用户提问 → 检索 → 分析 → 生成报告                            │
│    - 收集数据 → 生成周报                                         │
│                                                                 │
│  特点：简单、可靠、够用                                           │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  图编排 (orchestrator.AgentExecutor)                             │
│  ────────────────────────────                                   │
│  适用：需要分支、循环、人工审批、并行的复杂流程                       │
│  例子：                                                         │
│    - Bug 分诊：P0 走 WarRoom，P3 走自动分配                       │
│    - 方案审批：生成方案 → 人工审批 → 执行或驳回                     │
│    - 修复验证：执行修复 → 验证 → 失败则重新分析                     │
│                                                                 │
│  特点：灵活、强大，但复杂度高                                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

选择原则：
  能用线性解决 → 用线性（简单可靠）
  线性搞不定   → 用图编排（灵活强大）
```

之前的 TV 日志分析例子用图编排是过度设计了，你的判断是对的。
