# ERAG 简历工作内容 — 概念原理深度讲解（结合代码）

> 本文档逐条解释简历中每个技术名词的原理，结合项目实际代码和示意图，帮你真正理解系统设计。

---

## 1）文档解析与智能切片流水线

### 1.1 整体流程图

```
用户上传 PDF/Word/Markdown
         │
         ▼
   ┌─────────────┐
   │  MinIO 存储   │  ← 文件持久化
   └──────┬──────┘
          │
          ▼
   ┌─────────────┐
   │ 发布解析事件   │  ← Spring ApplicationEvent（异步）
   └──────┬──────┘
          │
          ▼
   ┌─────────────────────────────────────┐
   │ SmartDocumentParser（智能解析器）      │
   │  ├─ PDF → MinerU (magic-pdf) 转 Markdown │
   │  └─ 失败 → 降级 Apache Tika           │
   └──────┬──────────────────────────────┘
          │ Markdown 文本
          ▼
   ┌─────────────────────────────────────┐
   │ MarkdownTextCleaner（清洗）           │
   │  → 去除多余空行/乱码/格式残留          │
   └──────┬──────────────────────────────┘
          │
          ▼
   ┌─────────────────────────────────────┐
   │ DocumentSplitter（按标题层级切片）     │
   │  → H1/H2/H3 为切分边界               │
   │  → 每片附加面包屑元数据                │
   │  → 表格保持完整不切割                  │
   └──────┬──────────────────────────────┘
          │ List<TextSegment>
          ▼
   ┌──────────────┐    ┌──────────────────┐
   │ EmbeddingModel│    │                  │
   │ (bge-m3)     │    │  双路写入          │
   │ 向量化        │───▶│  ├─ Milvus (向量)  │
   │ 1024 维       │    │  └─ ES (全文索引)  │
   └──────────────┘    └──────────────────┘
```

### 1.2 Embedding 向量化 — 到底在做什么？

**一句话**：把文本变成一组数字（向量），让计算机能计算"两段话有多像"。

```
"公司年假制度" → [0.12, -0.34, 0.56, 0.78, ... ]  ← 1024 个浮点数
"员工休假规定" → [0.11, -0.33, 0.55, 0.80, ... ]  ← 很接近！语义相似
"今天天气不错" → [0.91, 0.22, -0.15, 0.03, ... ]  ← 差很远，语义无关
```

**原理**：Embedding 模型（如 bge-m3）是一个训练好的神经网络，输入文本，输出固定长度的向量。训练目标是：**语义相似的文本，向量距离近；语义不同的文本，向量距离远**。

**相似度计算**：通常用余弦相似度（Cosine Similarity）：
```
similarity = cos(θ) = (A · B) / (|A| × |B|)
```
- 值域 [-1, 1]，越接近 1 越相似
- 向量检索的本质：在 Milvus 中找到与查询向量余弦相似度最高的 topK 个向量

### 1.3 为什么要同时写 Milvus 和 Elasticsearch？

| | Milvus（向量检索） | Elasticsearch（BM25 全文检索） |
|---|---|---|
| **擅长** | 语义理解（同义词、近义词） | 精确匹配（编号、人名、专业术语） |
| **不擅长** | 精确编号（"第3.2.1条"） | 同义词（"休假" vs "假期"） |
| **原理** | 计算向量余弦相似度 | 统计词频/逆文档频率（TF-IDF 变体） |
| **举例** | "请假怎么算" 能匹配 "年假制度" | "3.2.1" 能精确命中包含该编号的片段 |

两者互补，所以需要**双路存储、混合检索**。

### 1.4 BM25 是什么？

BM25（Best Matching 25）是 Elasticsearch 默认的相关性评分算法，本质是 TF-IDF 的改进版：

```
BM25_score(q, d) = Σ IDF(qi) × [ f(qi, d) × (k1 + 1) ] / [ f(qi, d) + k1 × (1 - b + b × |d|/avgdl) ]
```

- `f(qi, d)`：词 qi 在文档 d 中出现的频率（TF）
- `IDF(qi)`：逆文档频率，越稀有的词权重越高（"的" 权重低，"EDID" 权重高）
- `k1`、`b`：可调参数，控制词频饱和度和文档长度归一化
- **核心直觉**：一个词在当前文档出现越多、在其他文档出现越少，这个词对当前文档越重要

---

## 2）混合检索与 RRF 融合精排

### 2.1 完整检索流程图

```
用户提问："公司年假怎么算？"
         │
         ▼
   ┌──────────────────┐
   │  查询改写/扩展     │  ← 第 3 节详述
   └──────┬───────────┘
          │
    ┌─────┴─────┐
    ▼           ▼
┌────────┐  ┌────────────┐
│ Milvus │  │Elasticsearch│
│ 向量检索│  │ BM25 检索   │
│ topK=50│  │  topK=50   │
└───┬────┘  └─────┬──────┘
    │              │
    │  向量结果     │  关键词结果
    │  (带余弦分)   │  (带BM25分)
    │              │
    └──────┬───────┘
           ▼
   ┌───────────────┐
   │ RRF 融合排序    │  ← 核心！下面详解
   │ score=Σ1/(k+r) │
   └───────┬───────┘
           ▼
   ┌───────────────┐
   │ Cross-Encoder  │  ← 精排，下面详解
   │ (BGE-Reranker) │
   └───────┬───────┘
           ▼
      topN 结果（如 5 条）
```

### 2.2 RRF（Reciprocal Rank Fusion）— 核心代码解读

**问题**：向量检索返回的分数是余弦相似度（0~1），BM25 返回的分数是 BM25 分数（0~+∞），**量纲不同，不能直接加**。

**RRF 的巧妙之处**：不看分数，只看**排名**。

```
RRF_score(document) = Σ 1 / (k + rank_i(document))
```

- `k = 60`（论文推荐的经验值，防止排名第 1 的权重过大）
- `rank_i` = 文档在第 i 路检索中的排名（第 1 名 rank=1，第 2 名 rank=2...）

**举例**：

```
文档 A：向量路排名第 1，BM25 路排名第 3
文档 B：向量路排名第 5，BM25 路排名第 1
文档 C：向量路排名第 2，BM25 路排名第 2

k = 60

文档 A 的 RRF 分 = 1/(60+1) + 1/(60+3) = 0.01639 + 0.01587 = 0.03226
文档 B 的 RRF 分 = 1/(60+5) + 1/(60+1) = 0.01538 + 0.01639 = 0.03177
文档 C 的 RRF 分 = 1/(60+2) + 1/(60+2) = 0.01613 + 0.01613 = 0.03226  ← 两路都靠前，最高！
```

**核心代码**（`ResultFusion.java`）：

```java
// 每个文档按排名累加 RRF 分数
private static void accumulateWeighted(List<RetrievalResult> results, int k, double weight,
                                       Map<String, RetrievalResult> repr,
                                       Map<String, Double> scores) {
    int rank = 1;
    for (RetrievalResult r : results) {
        String id = (r.getChunkId() != null && !r.getChunkId().isBlank())
                ? r.getChunkId()
                : (r.getDocumentId() + "_" + r.getChunkIndex());
        repr.putIfAbsent(id, r);           // 记录文档内容（只记第一次出现的）
        scores.merge(id, weight / (k + rank), Double::sum);  // 累加 RRF 分数
        rank++;
    }
}
```

**关键设计**：
- `scores.merge(..., Double::sum)`：如果同一个文档在两路都出现了，分数**叠加**（两路共识 → 更可信）
- `putIfAbsent`：文档内容只保留一份，避免重复
- 最终按 `score` 降序排列

### 2.3 Cross-Encoder 精排 — 为什么需要第二轮排序？

**RRF 融合后的问题**：RRF 只看排名，丢失了语义细节。可能有几个排名接近但实际不太相关的文档混入。

**Cross-Encoder 的原理**：

```
┌─────────────────────────────────────────────┐
│           Bi-Encoder（Embedding 模型）         │
│                                              │
│  query  ──→ [Encoder A] ──→ vec_q            │
│  doc    ──→ [Encoder B] ──→ vec_d            │
│                                              │
│  similarity = cosine(vec_q, vec_d)           │
│                                              │
│  特点：两侧独立编码，速度快，可预建索引          │
│  缺点：query 和 doc 没有交互，精度有限          │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│        Cross-Encoder（BGE-Reranker）          │
│                                              │
│  [query, doc] ──→ [同一个 Encoder] ──→ score  │
│                                              │
│  特点：query 和 doc 在每一层 Transformer       │
│        都有 attention 交互，精度高              │
│  缺点：必须在线逐对计算，无法预建索引            │
└─────────────────────────────────────────────┘
```

**本项目的策略**：
1. 先用 Bi-Encoder（Embedding 模型）快速召回 50 条候选（毫秒级）
2. 再用 Cross-Encoder（BGE-Reranker）对 50 条精排，输出 top 5（秒级）

**代码中的降级策略**（`HttpRerankService.java`）：
```java
@SentinelResource(value = "rerank:http", blockHandler = "rerankBlockHandler")
public List<RetrievalResult> rerank(String question, List<RetrievalResult> candidates, int topN) {
    // ... 调用 Rerank API ...
    // 失败时降级为顺序截断（不阻断检索）
}

// 熔断降级处理
public List<RetrievalResult> rerankBlockHandler(..., BlockException ex) {
    log.warn("[Rerank-HTTP] 限流/熔断降级，返回顺序截断 topN={}", topN);
    return truncate(candidates, topN);  // 直接取前 N 个，不调用 Rerank
}
```

### 2.4 降级策略总览

```
hybrid.enabled = false  →  纯向量检索（零影响回退）
向量路异常               →  仅用关键词路
ES 路异常                →  仅用向量路
Rerank 调用失败          →  顺序截断 topN
两路均空                 →  返回空（上层走"无资料"提示）
```

**设计哲学**：任何一路异常都不阻断整体检索，保证**可用性优先**。

---

## 3）RAG 优化流水线

### 3.1 查询改写（Query Rewriting）

**问题**：用户问的是口语化问题，直接拿去检索效果差。

```
用户："请假怎么算"          →  改写后："公司员工请假制度及薪资计算规则"
用户："那个政策怎么说的"     →  改写后："公司相关政策文件内容"
```

**实现**：用 LLM 改写，Prompt 模板引导：
```
请将以下用户问题改写为更适合文档检索的查询。
要求：去除口语化表达，补充隐含语义，输出 JSON：
{ "primary": "改写后的主查询", "subQueries": ["子查询1", "子查询2"] }

用户问题：{question}
对话历史：{history}
```

**代码设计**（`QueryContext` record）：
```java
// 三查询解耦：不同检索环节用不同形式的查询
public record QueryContext(
    String primaryQuery,    // 向量路用：语义精准的改写查询
    String keywordQuery,    // BM25 路用：含同义词扩展的查询
    String rerankQuery,     // Rerank 用：恒为原始问题（避免改写漂移）
    List<String> subQueries // Multi-Query 子查询列表
) {}
```

**为什么要三查询解耦？**
- 向量路用 `primaryQuery`（改写后，语义更精准）
- BM25 路用 `keywordQuery`（扩展了同义词，提升召回率）
- Rerank 用 `rerankQuery`（原始问题，避免改写后的查询偏离用户真实意图）

### 3.2 Multi-Query 扩展

**问题**：单一查询只能从一个角度检索，可能遗漏相关文档。

```
原始问题："如何申请年假"

扩展为 3 个子查询：
  → "年假申请流程"      （角度：流程）
  → "请假审批规定"      （角度：审批）
  → "假期天数计算规则"  （角度：天数）
```

**原理**：每个子查询独立做混合检索（向量 + BM25），得到各自的候选集，然后跨查询 RRF 融合：

```
子查询1 → 混合召回 → 候选集1 ─┐
子查询2 → 混合召回 → 候选集2 ─┼→ rrfMulti() 融合 → 统一 Rerank → topN
子查询3 → 混合召回 → 候选集3 ─┘
```

**代码**（`ResultFusion.rrfMulti`）：
```java
// 多查询融合：每个查询的排名列表独立累加到同一个 score map
public static List<RetrievalResult> rrfMulti(List<List<RetrievalResult>> rankedLists, int k) {
    Map<String, RetrievalResult> repr = new LinkedHashMap<>();
    Map<String, Double> scores = new HashMap<>();
    for (List<RetrievalResult> list : rankedLists) {
        accumulate(list, k, repr, scores);  // 每个查询的排名累加
    }
    return buildFused(repr, scores);
}
```

**为什么不在每个子查询上各做一次 Rerank？**
- N 个子查询 = N 次 Rerank 调用，成本高
- 不同子查询的 Rerank 分数不可比（query 不同，score 量纲不同）
- 正确做法：先融合，再统一 Rerank 一次

### 3.3 上下文压缩（Context Compression）

**问题**：检索到的片段可能包含大量与问题无关的信息，塞进 Prompt 会浪费 Token 且干扰 LLM。

```
原始片段（500 字）：
  "公司年假制度，第一章总则，本制度适用于公司全体正式员工。
   第二章年假天数，工作满1年不满10年的，年假5天；
   满10年不满20年的，年假10天；满20年以上的，年假15天。
   第三条年假申请，员工需提前3天..."

压缩后（100 字）：
  "工作满1年：5天，满10年：10天，满20年：15天。需提前3天申请。"
```

**实现**：用 LLM 对检索结果进行压缩，只保留与问题相关的核心信息。

### 3.4 语义缓存（Semantic Caching）

**问题**：很多用户问的问题本质相同，每次都走检索 + LLM 太浪费。

```
Q1: "公司年假几天？"     →  检索 + LLM → 回答（耗时 3 秒，消耗 Token）
Q2: "年假有多少天？"     →  语义缓存命中！直接返回 Q1 的回答（耗时 0.1 秒）
```

**原理**：
1. 每次问答后，将问题向量化存入缓存（Redis / Milvus）
2. 新问题先向量检索缓存，相似度 > 阈值（如 0.95）直接命中
3. 命中 → 跳过检索 + LLM，直接返回缓存回答

---

## 4）多 Agent 协作与声明式工作流引擎

### 4.1 多 Agent 协作 — DAG 编排

```
用户提交任务："分析公司2024年HR政策变化并生成报告"
                    │
                    ▼
            ┌───────────────┐
            │  PlannerAgent  │  ← 规划：分解为 DAG
            │  (规划者)       │
            └───────┬───────┘
                    │ 计划：[搜索HR政策] → [对比变化] → [生成报告]
          ┌─────────┴─────────┐
          ▼                   ▼
  ┌───────────────┐   ┌───────────────┐
  │KnowledgeAgent │   │KnowledgeAgent │  ← 检索：并行搜索
  │ 搜索2023政策   │   │ 搜索2024政策   │
  └───────┬───────┘   └───────┬───────┘
          │                   │
          └─────────┬─────────┘
                    ▼
            ┌───────────────┐
            │ AnalysisAgent  │  ← 分析：综合对比
            │ (分析者)       │
            └───────┬───────┘
                    ▼
            ┌───────────────┐
            │  ReportAgent   │  ← 报告：生成结构化报告
            │  (报告者)      │
            └───────────────┘
```

**Agent 无状态设计**：
```java
public interface Agent {
    AgentType type();                    // 角色类型
    AgentResult execute(AgentContext ctx); // 执行逻辑，所有状态通过 Context 传递
}
```

- Agent 是 Spring 单例（无状态，线程安全）
- 所有执行态通过 `AgentContext` 传递（任务信息、共享产物、配置）
- Agent 只读写 Context 中的 `artifact`（产物），不直接操作数据库
- 持久化由 `AgentExecutor` 统一负责

### 4.2 工具 SPI 机制

**什么是 SPI？** Service Provider Interface，服务提供者接口。简单说就是**插件化**。

```java
// 工具接口定义
public interface Tool {
    String name();           // 工具名称，如 "knowledge_search"
    JsonSchema parameters(); // 参数 JSON Schema（用于 LLM 理解如何调用）
    ToolResult execute(Map<String, Object> params, ToolContext ctx); // 执行
}
```

**自动注册原理**：
```java
// Spring 启动时，自动扫描所有 Tool 实现类
@Component
public class KnowledgeSearchTool implements Tool {
    @Override
    public String name() { return "knowledge_search"; }
    // ...
}

// ToolRegistry 自动收集所有 Tool bean
@Component
public class ToolRegistry {
    @Autowired
    public ToolRegistry(List<Tool> tools) {  // Spring 自动注入所有 Tool 实现
        tools.forEach(t -> registry.put(t.name(), t));
    }
}
```

**JSON Schema 参数校验**：每个 Tool 声明自己的参数格式，LLM 输出的工具调用参数会经过 JSON Schema 校验，不合法则拒绝执行。

### 4.3 声明式工作流引擎

**节点类型**：
```
START → TOOL(调用工具) → LLM(调用大模型) → HUMAN(人工审批) → END
```

**状态持久化**：每个节点执行后，将状态和上下文写入数据库：
```java
// WorkflowTask 实体
@Data
@TableName("workflow_task")
public class WorkflowTask {
    private Long id;
    private String status;        // CREATED / RUNNING / WAITING_HUMAN / COMPLETED / FAILED
    private String currentNode;   // 当前执行到哪个节点
    private String contextJson;   // 跨节点变量传递（JSON 序列化）
    // ...
}
```

**HUMAN 节点 — 人工审批门**：
```
... → LLM节点(生成报告) → HUMAN节点(暂停) → ...
                              │
                         等待审批人操作
                              │
                     approve / reject
                              │
                              ▼
                         END / 回退
```

**Saga 补偿**：工作流执行到一半失败时，已完成的步骤需要回滚：
```
步骤1(创建报告) → 步骤2(发送邮件) → 步骤3(更新状态) ← 失败！
                                              │
                                    触发补偿：逆序执行
                                              │
                            补偿3(撤销状态) → 补偿2(撤回邮件) → 补偿1(标记报告无效)
```

---

## 5）知识治理体系

### 5.1 SimHash 近似去重

**问题**：MD5 只能检测完全相同的文档，但实际场景中"高度相似"的文档也需要去重（如不同版本的同一文件）。

**SimHash 原理**（5 步）：

```
步骤1：分词
  "公司年假制度规定" → ["公司", "年假", "制度", "规定"]

步骤2：每个词计算 hash（如 FNV-1a）
  "公司" → 0x3a7f (64位)
  "年假" → 0x8b2c (64位)
  ...

步骤3：加权合并（每一位分别处理）
  hash 第 i 位为 1 → +weight
  hash 第 i 位为 0 → -weight
  所有词累加 → 64 维向量

步骤4：二值化
  向量每一位 > 0 → 1
  向量每一位 ≤ 0 → 0
  → 得到 64 位 SimHash 指纹

步骤5：比较
  两个文档的 SimHash 汉明距离（不同的位数）≤ 3 → 判定近似重复
```

**代码核心**（`SimHashCalculator.java`）：
```java
// 64 位 SimHash，FNV-1a 哈希，CJK 单字分词 + Latin 词分词
public long compute(String text) {
    long[] bits = new long[64];
    // 分词并计算每个 token 的 hash
    for (String token : tokenize(text)) {
        long hash = fnv1a(token);
        for (int i = 0; i < 64; i++) {
            bits[i] += ((hash >> i) & 1) == 1 ? 1 : -1;
        }
    }
    // 二值化
    long fingerprint = 0;
    for (int i = 0; i < 64; i++) {
        if (bits[i] > 0) fingerprint |= (1L << i);
    }
    return fingerprint;
}

// 汉明距离
public int hammingDistance(long a, long b) {
    return Long.bitCount(a ^ b);  // XOR 后数 1 的个数
}
```

**为什么 SimHash 快？**
- 比较两个 64 位整数的汉明距离 = O(1)
- 适合大规模文档库（百万级）的去重扫描

### 5.2 生命周期状态机

```
                    submit
  DRAFT(草稿) ──────────────▶ REVIEW(审核中)
      ▲                          │
      │                    approve │ reject
      │                          ▼
      │                    PUBLISHED(已发布)
      │                          │
      │                    auto-archive（超过 N 天）
      │                          ▼
      └────────────────── ARCHIVED(已归档)
              reopen
```

每个状态转换：
- 有前置条件检查（如 REVIEW → PUBLISHED 需要审批人权限）
- 有后置动作（如 PUBLISHED 会触发知识库索引更新）
- 记录审计日志（谁、什么时间、什么操作）

### 5.3 文档 ACL 权限

```java
// 权限类型
public enum DocPermission {
    VIEW, EDIT, DELETE, DOWNLOAD, SHARE
}

// ACL 实体
@Data
@TableName("kb_doc_acl")
public class KbDocAcl {
    private Long documentId;
    private String subjectType;  // USER / ROLE
    private Long subjectId;
    private String permission;   // VIEW / EDIT / ...
    private String effect;       // ALLOW / DENY
    private LocalDateTime expireTime;
}
```

**DENY 优先原则**：
```
用户 A 对文档 X：
  - 角色 ACL：VIEW = ALLOW
  - 用户 ACL：VIEW = DENY
  → 最终结果：DENY（显式拒绝优先）
```

---

## 6）多租户隔离与多模型路由

### 6.1 多租户行级隔离

```
┌─────────────────────────────────────────────┐
│                请求进入                       │
│  Header: X-Tenant-Code = "company_a"        │
└──────────────┬──────────────────────────────┘
               ▼
      ┌─────────────────┐
      │ TenantInterceptor│  ← Spring 拦截器
      │ 解析租户码 → ID   │
      │ TenantContext.set│
      └────────┬────────┘
               ▼
      ┌─────────────────┐
      │ MyBatis-Plus     │  ← SQL 拦截器
      │ 自动追加           │
      │ WHERE tenant_id=? │
      └────────┬────────┘
               ▼
      实际 SQL：SELECT * FROM kb_document WHERE tenant_id = 123 AND ...
```

### 6.2 TransmittableThreadLocal — 为什么需要它？

**问题场景**：
```java
// 主线程
TenantContext.setTenantId(123L);  // 设置租户

// 异步任务
executor.submit(() -> {
    Long tenantId = TenantContext.getTenantId();  // null！丢失了！
});
```

**原因**：普通 `ThreadLocal` 绑定当前线程，线程池中的子线程是另一个线程，拿不到父线程的值。

**解决方案 — TransmittableThreadLocal（阿里 TTL）**：

```
普通 ThreadLocal：
  主线程 set → 子线程 get → null（丢失）

TransmittableThreadLocal：
  主线程 set → 提交任务时自动捕获 → 子线程执行时自动回放 → get 正常！
```

**代码**（`TenantContext.java`）：
```java
public final class TenantContext {
    // 使用 TTL 替代普通 ThreadLocal
    private static final TransmittableThreadLocal<Long> TENANT = new TransmittableThreadLocal<>();

    public static void setTenantId(Long tenantId) { TENANT.set(tenantId); }
    public static Long getTenantId() { return TENANT.get(); }
    public static void clear() { TENANT.remove(); }
}
```

**线程池也需要包装**（`AsyncConfig.java`）：
```java
@Bean("docAsyncExecutor")
public Executor docAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // ...
    return TtlExecutors.getTtlExecutor(executor);  // 包装！保证 TTL 传播
}
```

### 6.3 多模型路由

```
用户请求 → ModelRouter → 选择 Provider
                │
    ┌───────────┼───────────┬───────────┐
    ▼           ▼           ▼           ▼
┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
│OpenAI  │ │DeepSeek│ │Claude  │ │Ollama  │
│(云端)   │ │(便宜)   │ │(强推理) │ │(本地)   │
└────────┘ └────────┘ └────────┘ └────────┘
```

**路由策略**：
- **按名称**：`model = "deepseek-chat"` → 直接选 DeepSeek
- **按成本**：比较各 Provider 单价，选最便宜的
- **按 Token 用量**：选剩余配额最多的（避免某个 Provider 超限）
- **按租户**：不同租户配置不同默认模型

**代码**（`ModelRouterImpl.java`）：
```java
public ModelResponse route(ModelRequest req, ModelContext ctx) {
    // 1. 收集可用 Provider
    List<ModelProvider> available = availableProviders();
    // 2. 过滤掉熔断中的 Provider
    available = available.stream()
        .filter(p -> !circuitBreaker.isOpen(p.type()))
        .toList();
    // 3. 按策略选择
    ModelProvider selected = strategy.select(available, req, ctx);
    // 4. 调用，失败则 failover 重试其他 Provider
    for (int i = 0; i <= failoverRetries; i++) {
        try {
            return selected.call(req);
        } catch (Exception e) {
            circuitBreaker.recordFailure(selected.type());
            selected = nextAvailable(available, selected);
        }
    }
}
```

### 6.4 Sentinel 熔断器 — 三状态机

```
        失败率 > 阈值                    冷却时间到
  CLOSED ──────────────▶ OPEN ──────────────▶ HALF_OPEN
    ▲                      │                     │
    │                      │                  试探请求
    │                      │                     │
    │               快速失败（不调用）         成功 → CLOSED
    │                                      失败 → OPEN
    └──────────────────────────────────────────────┘
```

**代码**（`ProviderCircuitBreaker.java`）：
```java
public class ProviderCircuitBreaker {
    // 三状态
    enum State { CLOSED, OPEN, HALF_OPEN }

    public boolean isOpen(ProviderType type) {
        State state = getState(type);
        if (state == State.CLOSED) return false;
        if (state == State.OPEN) {
            // 检查冷却时间，到了则转 HALF_OPEN
            if (cooldownElapsed(type)) {
                setState(type, State.HALF_OPEN);
                return false;  // 允许一个试探请求
            }
            return true;  // 仍然熔断
        }
        return false;  // HALF_OPEN 允许试探
    }

    public void recordFailure(ProviderType type) {
        failures.increment(type);
        if (failures.count(type) >= threshold) {
            setState(type, State.OPEN);  // 触发熔断
        }
    }

    public void recordSuccess(ProviderType type) {
        setState(type, State.CLOSED);  // 恢复正常
    }
}
```

---

## 7）AI Ops 运维与可观测性

### 7.1 分布式链路追踪 — Trace/Span 模型

```
用户提问 "公司年假政策？"
│
├── Trace (trace_id: abc123) ── 一次完整请求
│   │
│   ├── Span 1: 查询改写 (parent: root)
│   │   └── Span 2: LLM 调用 (parent: span1)
│   │
│   ├── Span 3: 向量检索 (parent: root)
│   ├── Span 4: BM25 检索 (parent: root)
│   │
│   ├── Span 5: RRF 融合 (parent: root)
│   ├── Span 6: Rerank (parent: root)
│   │
│   └── Span 7: LLM 生成回答 (parent: root)
```

**代码**（`TraceContext.java`）：
```java
public class TraceContext {
    // 使用 TTL 保证异步任务也能拿到 traceId
    private static final TransmittableThreadLocal<String> TRACE_ID = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<Deque<String>> SPAN_STACK = new TransmittableThreadLocal<>();

    public static String currentTraceId() { return TRACE_ID.get(); }
    public static String currentSpanId() {
        Deque<String> stack = SPAN_STACK.get();
        return stack == null || stack.isEmpty() ? null : stack.peek();
    }
}
```

### 7.2 AOP 指标采集

```java
@Aspect
@Component
public class InfraMetricAspect {
    @Around("@annotation(aiCall)")  // 拦截所有 @AiCall 注解的方法
    public Object around(ProceedingJoinPoint pjp, AiCall aiCall) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            recordSuccess(aiCall.provider(), System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            recordFailure(aiCall.provider(), System.currentTimeMillis() - start);
            throw e;
        }
    }
}
```

**采集的指标**：
- 调用次数（按模型/租户/用户）
- Token 消耗（输入 + 输出）
- 响应时间（P50 / P95 / P99）
- 错误率

### 7.3 告警规则引擎

```
规则示例：
  IF 某模型 error_rate > 50% 持续 5 分钟
  THEN 触发 CRITICAL 告警 → 通知运维 + 自动切换备用模型

  IF 某租户 token_usage > 本月配额 80%
  THEN 触发 WARNING 告警 → 通知租户管理员
```

---

## 附：简历工作内容与技术点速查表

| 简历条目 | 核心技术点 | 面试关键词 |
|---------|-----------|-----------|
| 文档解析与切片 | Embedding 向量化、BM25、双路存储 | 余弦相似度、TF-IDF、语义 vs 关键词 |
| 混合检索与 RRF | RRF 融合、Cross-Encoder 精排 | 排名融合 vs 分数融合、Bi-Encoder vs Cross-Encoder |
| RAG 优化流水线 | 查询改写、Multi-Query、上下文压缩、语义缓存 | 三查询解耦、召回率 vs 精确度 |
| 多 Agent 协作 | DAG 编排、SPI 自动注册、工作流引擎 | 无状态设计、Saga 补偿、Human-in-the-Loop |
| 知识治理 | SimHash、状态机、ACL | 汉明距离、DENY 优先、生命周期管理 |
| 多租户与多模型 | TTL、Sentinel 熔断、路由策略 | ThreadLocal 传播、三状态机、failover |
| AI Ops | Trace/Span、AOP 采集、告警引擎 | 链路追踪、P95、阈值告警 |
