# ERAG 系统改造方案

> 针对架构审查发现的 12 个问题，按优先级给出详细改造方案。纯设计层面，不涉及代码改写。

---

## P0：必须立即处理

### #9 MinerU 解析服务无熔断

**现状问题**：
MinerU 是独立的 Python FastAPI 服务，通过 HTTP 调用。当前调用方（SmartDocumentParser）没有熔断保护。如果 MinerU 服务挂了、响应慢、或 OOM，RabbitMQ 消费线程会阻塞等待，最终：
- 消费线程被占满，其他解析任务排队
- 消费超时后触发重试，但 MinerU 还是挂的，反复重试浪费资源
- 死信队列堆积，补偿任务扫描到后又入队，形成"重试风暴"

**改造方案**：

```
改造前：
  RabbitMQ Consumer → SmartDocumentParser → HTTP 调用 MinerU
                                              │
                                         挂了就一直等

改造后：
  RabbitMQ Consumer → SmartDocumentParser → Sentinel 熔断器 → HTTP 调用 MinerU
                                              │                    │
                                         熔断后快速失败         正常调用
                                              │
                                         降级：走 Apache Tika
```

具体措施：
1. 对 MinerU HTTP 调用加 `@SentinelResource` 注解，复用已有的 Sentinel 基础设施
2. 熔断参数：错误率 > 50% 或慢调用（>10s）率 > 60% 时触发熔断，冷却 30 秒后进入半开
3. 熔断后降级策略：自动切换到 Apache Tika 解析（已有 Tika fallback 逻辑，只需保证降级路径通畅）
4. 加 Grafana 告警：MinerU 熔断触发时通知运维
5. 补偿任务扫描时，检查目标任务是否因 MinerU 熔断失败，如果是，延迟更长时间再重试（避免重试风暴）

**涉及模块**：knowledge-ai（SmartDocumentParser）、deploy（Sentinel 配置）

---

### #10 RabbitMQ 消费与补偿任务竞态

**现状问题**：
补偿定时任务（ParseTaskCompensationJob）扫描超时未完成的解析任务，将其重新入队。但如果原消费者只是执行慢（还没超时但接近超时），可能出现：
- 补偿任务把任务重新入队
- 原消费者还在执行（慢但没失败）
- 两个消费者同时处理同一个 documentId
- 重复写入向量和索引，数据重复

**改造方案**：

```
补偿任务扫描到超时任务
         │
         ▼
   尝试获取分布式锁（Redis SETNX task:{taskId} EX 300）
         │
    ┌────┴────┐
    ▼         ▼
  获取成功   获取失败（说明原消费者还在执行）
    │         │
    ▼         ▼
  重新入队   跳过，记录日志
```

具体措施：
1. 消费端：消费前获取分布式锁 `SETNX parse:lock:{documentId} EX 600`，消费完成释放锁
2. 补偿端：扫描到超时任务后，先尝试获取同一把锁，获取失败说明消费者还在执行，跳过
3. 锁的 TTL 设为任务最大执行时间的 2 倍（如最大执行 5 分钟，锁 TTL 10 分钟）
4. 消费完成后的清理动作：先写入结果，再删除锁，最后 ACK 消息（保证原子性）
5. 加监控：锁获取失败次数过多时告警（可能是锁泄漏）

**涉及模块**：knowledge-kb（ParseTaskCompensationJob、RabbitMQ Consumer）、knowledge-common（Redis 工具类）

---

## P1：影响用户体验和运维效率

### #4 查询改写/扩展增加额外 LLM 调用延迟

**现状问题**：
用户提问后的调用链：
```
用户提问 → LLM 查询改写（~1s）→ LLM 查询扩展（~1s）→ 向量检索 + BM25（~0.3s）→ RRF + Rerank（~0.5s）→ LLM 生成回答（~3s）
```
总计 ~6 秒，其中改写+扩展占了 2 秒，但这两个步骤对简单问题（如"年假几天"）完全多余。

**改造方案**：

方案 A：合并改写+扩展为一次调用（推荐）
```
改造前：2 次 LLM 调用（改写 + 扩展）≈ 2s
改造后：1 次 LLM 调用，一个 Prompt 同时输出 {primary, subQueries, keywordQuery} ≈ 1s
```
- 用一个 Prompt 同时完成改写和扩展，输出结构化 JSON
- 减少一次 LLM 调用，延迟降低 ~50%

方案 B：加智能开关
```
简单问题（长度 < 10 字、无歧义）→ 跳过改写/扩展，直接检索
复杂问题（长度 > 20 字、含指代、多意图）→ 走改写/扩展
```
- 用规则判断（正则 + 关键词），不需要 LLM
- 80% 的简单问题可以省掉 2 秒

方案 C：语义缓存前置
```
改造前：改写 → 扩展 → 检索 → 生成 → 查缓存
改造后：先查缓存 → 命中直接返回 → 未命中再走改写+扩展+检索+生成
```
- 缓存命中率高的场景（如客服系统），可以省掉全部开销

**建议三个方案组合使用**：C 优先 → B 兜底 → A 优化剩余场景。

**涉及模块**：knowledge-ai（RagServiceImpl、QueryRewriter、QueryExpander）

---

### #11 RAG 全链路缺少端到端 Trace

**现状问题**：
当前 AI Ops 有 Trace/Span 模型，但 RAG 流水线的每一步（改写→扩展→向量检索→BM25→RRF→Rerank→LLM 生成）没有统一的 trace_id 串联。线上出问题时：
- 用户说"回答不准确"，无法定位是检索差还是 LLM 幻觉
- 用户说"回答太慢"，无法定位是哪一步慢
- 只能看日志手动关联，效率低

**改造方案**：

```
用户提问
  │
  │ trace_id: abc123
  │
  ├── Span 1: 查询改写 (1200ms)
  │   └── 输入: "请假怎么算"
  │   └── 输出: "员工请假制度及薪资计算规则"
  │
  ├── Span 2: 向量检索 (180ms)
  │   └── 输入: "员工请假制度及薪资计算规则"
  │   └── 输出: 50 条候选，top1 score=0.92
  │
  ├── Span 3: BM25 检索 (120ms)
  │   └── 输入: "请假怎么算 年假 薪资"
  │   └── 输出: 50 条候选，top1 score=8.7
  │
  ├── Span 4: RRF 融合 (5ms)
  │   └── 输入: 向量 50 条 + BM25 50 条
  │   └── 输出: 融合后 80 条
  │
  ├── Span 5: Rerank (400ms)
  │   └── 输入: 80 条候选
  │   └── 输出: top 5，score=0.95
  │
  ├── Span 6: Prompt 组装 (2ms)
  │   └── 模板ID、注入的 context 长度、历史轮数
  │
  └── Span 7: LLM 生成 (2800ms)
      └── 输入 token 数、输出 token 数
      └── 模型名、Provider
```

具体措施：
1. 在 RagServiceImpl.ask() 入口生成 trace_id，通过 TraceContext（已有，用 TTL）传播到所有异步线程
2. 每个 Span 记录：输入摘要、输出摘要、耗时、token 消耗（如涉及 LLM）
3. Span 之间用 parent_id 关联，形成树形结构
4. 存储到已有的 agent_trace / agent_span 表（或新建 rag_trace 表）
5. 前端在"AI Ops"页面增加 RAG Trace 查看入口，输入 trace_id 可回放完整链路
6. 每个 Span 记录检索分数，便于分析"检索到了但分数低"或"分数高但不相关"的问题

**涉及模块**：knowledge-ai（RagServiceImpl、SearchService、RerankService）、knowledge-common（TraceContext）

---

## P2：影响架构可维护性和扩展性

### #2 图编排的"分支"是静态的

**现状问题**：
`AgentNode.next` 是构建时写死的，执行时不管 Agent 输出什么结果都按 next 跳转。Agent 无法根据分析结果自主决定下一步。

**改造方案**：

在 AgentResult 中增加路由建议字段：
```
AgentResult
  ├── success: boolean
  ├── artifactKey: String
  ├── artifact: Object
  ├── summary: String
  ├── tokensUsed: int
  └── suggestedNextNodeId: String    ← 新增：Agent 建议的下一个节点
```

执行器的推进逻辑改为：
```
1. 检查 result.suggestedNextNodeId 是否非空
   ├── 非空 → 验证该 nodeId 是否在图中存在且可达
   │         ├── 存在 → 跳转到该节点
   │         └── 不存在 → 记录警告，走默认 nextOf()
   └── 空 → 走默认 nextOf()
```

**安全性保障**：
- Agent 只能跳转到图中已注册的节点，不能跳出图外
- 执行器校验跳转合法性（防环路、防跳到已执行节点）
- 如果 Agent 返回的 suggestedNextNodeId 非法，降级为默认顺序

**示例**：
```
图定义：planner → knowledge → analysis → report

AnalysisAgent 执行后：
  情况1: 分析充分 → suggestedNextNodeId = null → 默认跳到 report
  情况2: 信息不足 → suggestedNextNodeId = "knowledge" → 跳回检索
  情况3: 信息严重不足 → suggestedNextNodeId = "planner" → 重新规划
```

**涉及模块**：knowledge-agent（AgentResult、AgentExecutor）

---

### #3 WorkflowExecutor 和 Agent 系统完全割裂

**现状问题**：
两套系统各自独立，不共享执行器、上下文、持久化、工具调用链路。维护两套代码成本高，功能重复。

**改造方案**：分三步走，渐进式统一。

**第一步：统一工具调用层（低风险）**
```
当前：
  Agent → KnowledgeSearchTool.search()     ← 直接调用
  Workflow → ToolExecutor.execute()        ← 通过 ToolExecutor

统一后：
  Agent → ToolExecutor.execute()           ← 统一走 ToolExecutor
  Workflow → ToolExecutor.execute()        ← 不变
```
- Agent 的工具调用统一走 ToolExecutor，复用权限校验、审计、参数校验
- KnowledgeSearchTool 保留 search() 方法做向后兼容，但内部委托给 ToolExecutor

**第二步：统一上下文传递（中风险）**
```
当前：
  AgentContext { artifacts: Map<String, Object> }
  WorkflowContext { variables: Map<String, Object> }

统一后：
  SharedContext { variables: Map<String, Object> }
    ├── Agent 模式下叫 artifacts
    ├── Workflow 模式下叫 variables
    └── 底层是同一个 Map
```

**第三步：Agent 作为 Workflow 节点类型（高风险，长期目标）**
```
当前 Workflow 节点类型：TOOL / LLM / HUMAN
新增节点类型：AGENT

AGENT 节点 = 调用一个完整的 Agent（Planner/Knowledge/Analysis/Report）

用户可以在一个工作流中混合使用：
  START → TOOL(日志解析) → AGENT(分析Agent) → HUMAN(审批) → TOOL(贴JIRA) → END
```
- Agent 变成 Workflow 的一种特殊节点
- 复杂分析用 AGENT 节点，简单操作用 TOOL 节点，人工审批用 HUMAN 节点
- 一套系统取代两套

**涉及模块**：knowledge-agent（全局）

---

### #8 KnowledgeAgent 绕过 ToolExecutor 直接调用工具

**现状问题**：
KnowledgeAgent 直接调用 `knowledgeSearchTool.search()`，绕过了 ToolExecutor 的权限校验、审计、参数校验链路。虽然 KnowledgeSearchTool 内部做了权限过滤，但和 ToolExecutor 的链路是两条独立路径。

**改造方案**：

在第一步（统一工具调用层）中一并解决：
1. KnowledgeAgent 改为通过 ToolExecutor 调用 knowledge_search 工具
2. ToolExecutor 负责：参数校验 → 权限校验 → 审计记录 → 执行 → 结果返回
3. KnowledgeSearchTool 的 search() 方法标记为 @Deprecated，保留向后兼容
4. 确保 Agent 层和 Workflow 层走完全相同的权限校验链路

**涉及模块**：knowledge-agent（KnowledgeAgent、ToolExecutor）

---

## P3：影响质量和开发体验

### #5 语义缓存阈值需要精细调优

**现状问题**：
相似度阈值是固定值，对所有问题一视同仁。但不同问题的缓存价值不同：
- "年假几天" → 高频问题，缓存价值高，阈值可以低一点（0.90）
- "2024年Q3 HDMI出货量" → 低频问题，缓存价值低，阈值应该高一点（0.98）

**改造方案**：

分层缓存策略：
```
第一层：精确匹配（MD5）
  问题完全一样 → 直接命中，无需向量计算
  适用：用户重复提问

第二层：高频问题缓存（阈值 0.92）
  问题高度相似 → 命中缓存
  适用：FAQ 类问题，命中率高

第三层：长尾问题不缓存
  问题太具体或太长 → 不缓存，直接走检索
  适用：定制化问题
```

具体措施：
1. 引入问题热度统计：每次提问记录问题向量和命中次数
2. 热度 > 阈值的问题自动进入缓存（低阈值 0.92）
3. 热度低的问题不缓存或用高阈值（0.98）
4. 知识库文档更新时，关联的缓存向量自动失效（通过 document_id 关联）
5. 监控缓存命中率和误命中率，持续调优

**涉及模块**：knowledge-ai（语义缓存服务）

---

### #6 RRF 的 k=60 是经验值，没有针对场景调优

**现状问题**：
RRF 公式中的 k 值对所有知识库、所有查询一视同仁。

**改造方案**：

1. 建立离线评测数据集：
   - 人工标注 100-200 组 query → 相关文档对
   - 覆盖不同查询类型：精确查询、模糊查询、多意图查询

2. 参数搜索：
   - 固定其他参数，k 从 10 到 100 以 10 为步长遍历
   - 用评测集计算 Recall@5、MRR、NDCG
   - 选最优 k 值

3. 分知识库配置：
   - 不同知识库的文档量和查询特征不同，k 值可能不同
   - 在 AiProperties 中支持 per-KB 的 k 值配置

4. 持续监控：
   - 每次问答记录 RRF 融合前后的排名变化
   - 如果发现"向量路排名靠前但 RRF 后排名大幅下降"的案例，分析是否 k 值不合理

**涉及模块**：knowledge-ai（ResultFusion、AiProperties）、新增评测工具

---

### #7 AgentContext 产物传递是弱类型的

**现状问题**：
`ctx.getArtifact("PLAN")` 返回 Object，需要强转。如果 key 写错或类型不匹配，运行时才报错。

**改造方案**：

给 AgentContext 增加类型安全的访问方法：
```
AgentContext
  ├── getArtifact(String key) → Object        ← 保留，向后兼容
  ├── getPlan() → Plan                         ← 新增
  ├── getEvidences() → List<Evidence>          ← 新增
  ├── getAnalysis() → String                   ← 新增
  ├── getReport() → String                     ← 新增
  └── putPlan(Plan plan)                       ← 新增
  └── putEvidences(List<Evidence> evidences)   ← 新增
```

底层还是同一个 Map，只是加了类型安全的 Wrapper。编译期就能发现类型错误。

**涉及模块**：knowledge-agent（AgentContext）

---

### #12 检索质量没有量化评估

**现状问题**：
无法衡量 RAG 优化（RRF、Rerank、查询改写等）的实际效果。参数调整后不知道变好了还是变差了。

**改造方案**：

建立评测体系：

```
评测数据集（人工标注）
  ├── query_1 → [doc_A, doc_B, doc_C]（相关文档）
  ├── query_2 → [doc_D, doc_E]
  └── ...

离线评测脚本
  ├── 输入：评测数据集 + 当前 RAG 配置
  ├── 输出：Recall@5, Recall@10, MRR, NDCG
  └── 对比：不同配置下的指标变化
```

具体措施：
1. 构建评测数据集：从线上日志中抽取高频 query，人工标注相关文档
2. 离线评测脚本：批量执行 query，记录检索结果，计算指标
3. CI 集成：每次 RAG 参数变更（RRF k 值、Rerank topN、Embedding 模型等）前，跑评测集对比
4. 线上指标：每次问答后，如果用户点了"有用"，记录为正样本；点了"没用"，记录为负样本
5. 定期（每周）汇总线上指标趋势

**涉及模块**：新增评测模块（scripts/ 或独立服务）

---

## 改造路线图

```
第 1 周：P0 修复
  ├── #9 MinerU 熔断（1-2 天）
  └── #10 MQ 竞态锁（1 天）

第 2-3 周：P1 优化
  ├── #4 合并改写+扩展（2 天）
  ├── #4 语义缓存前置（1 天）
  └── #11 RAG Trace（3-5 天）

第 4-6 周：P2 架构改进
  ├── #8 统一工具调用层（2 天）
  ├── #2 AgentResult 路由建议（2 天）
  ├── #7 类型安全的 Context（1 天）
  └── #3 统一上下文（3 天）

第 7-8 周：P3 质量提升
  ├── #5 语义缓存分层策略（2 天）
  ├── #6 RRF k 值调优（2 天）
  └── #12 检索评测体系（3 天）

长期（1-3 个月）：
  └── #3 Agent 作为 Workflow 节点类型（2-3 周）
```

---

## 总结

12 个问题的本质是三个层面：

| 层面 | 问题 | 核心矛盾 |
|------|------|---------|
| **可靠性** | #9 #10 | 外部依赖没有兜底，异常会传导 |
| **性能** | #4 #5 #6 | 调用链太长，参数未调优 |
| **架构** | #2 #3 #7 #8 | 两套系统割裂，类型安全缺失 |
| **可观测性** | #11 #12 | 出了问题定位慢，优化效果无法量化 |

改造的核心原则：**先堵漏洞（P0），再优化体验（P1），最后重构架构（P2/P3）**。
