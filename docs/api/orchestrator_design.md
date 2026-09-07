# Agent 编排引擎设计文档（v3-4）

> 模块：`knowledge-agent` / 包：`com.knowledge.agent.orchestrator`
> 实现：Agent 注册、图编排、状态机、重试、Saga 补偿回滚、硬超时

## 1. 定位

与现有两层执行引擎并列共存，补齐 **重试 + 回滚 + 硬超时** 三大能力：

| 层 | 类 | 模型 | 重试 | 回滚 | 超时 |
|---|---|---|---|---|---|
| `engine` | `AgentExecutor` | 固定 4 步线性 | ✗ | ✗ | 步间软校验 |
| `workflow` | `WorkflowExecutor` | 声明式 TOOL/LLM 节点 | ✓ | ✗ | ✗ |
| **`orchestrator`** | **`AgentExecutor`** | **Agent 图（顺序+分支）** | **✓** | **✓ Saga** | **✓ 硬超时** |

复用 `engine.Agent`/`AgentContext`/`AgentResult`/`AgentTaskManager` 与 `agent_task` 表；新增 `agent_node_run` + `agent_compensation` 两表。

## 2. 类图

```mermaid
classDiagram
    direction LR
    class Agent {
        <<interface, engine>>
        +AgentType type()
        +AgentResult execute(AgentContext ctx)
    }
    class Compensable {
        <<interface, orchestrator>>
        +Compensation compensation(AgentContext ctx)
    }
    class Compensation {
        <<interface, orchestrator>>
        +String description()
        +void execute(AgentContext ctx)
    }
    class AgentNode {
        +String nodeId
        +AgentType agentType
        +String name
        +String next
        +int maxRetries
        +long retryBackoffMs
        +long timeoutMs
        +String outputArtifactKey
        +String resolveArtifactKey()
    }
    class AgentGraph {
        +String code
        +List~AgentNode~ nodes
        +String startNodeId
        +Map indexById()
        +AgentNode startNode()
        +AgentNode nextOf(AgentNode)
        +void validate()
    }
    class AgentExecutor {
        -Map~AgentType,Agent~ agentMap
        -AgentGraph defaultGraph
        -OrchestratorTaskManager taskManager
        -ExecutorService orchestratorPool
        +AgentTask execute(Long taskId)
        +AgentTask execute(Long taskId, AgentGraph)
        +void executeAsync(Long, Long)
        +void cancel(Long, String)
        +void registerGraph(String, AgentGraph)
    }
    class AgentScheduler {
        -AgentExecutor executor
        -AgentTaskManager taskManager
        +AgentTask createTask(...)
        +void submit(Long, Long)
        +AgentTask execute(Long)
        +void registerGraph(String, AgentGraph)
    }
    class OrchestratorTaskManager {
        -AgentTaskManager delegate
        +AgentTask create(...)
        +void updateStatus(Long, OrchestratorStatus)
        +AgentNodeRun startNodeRun(...)
        +void successNodeRun / retryNodeRun / timeoutNodeRun / failNodeRun / skipNodeRun
        +AgentCompensation startCompensation(...)
        +void successCompensation / failCompensation
    }
    class OrchestratorStatus {
        <<enum>>
        CREATED
        RUNNING
        ROLLING_BACK
        COMPLETED
        FAILED
        CANCELED
    }
    class NodeRunStatus {
        <<enum>>
        PENDING
        RUNNING
        SUCCESS
        RETRYING
        FAILED
        TIMEOUT
        SKIPPED
        CANCELED
    }

    Agent <|.. Compensable : 副作用 Agent 实现
    AgentNode --> AgentType
    AgentGraph --> AgentNode
    AgentExecutor --> AgentGraph : 遍历
    AgentExecutor --> OrchestratorTaskManager : 持久化
    AgentExecutor ..> Compensable : instanceof 取补偿
    AgentScheduler --> AgentExecutor : 单向委托
    OrchestratorTaskManager --> AgentTaskManager : 任务级委托
    AgentExecutor --> OrchestratorStatus
    AgentExecutor --> NodeRunStatus
```

**依赖方向**：`orchestrator → engine`（单向）。补偿通过 `Compensable` 标记接口 + `instanceof` 判定，**不修改 `engine.Agent`**，保持 engine 零侵入。`AgentScheduler → AgentExecutor` 单向，避免循环依赖（默认图由 `AgentExecutor.@PostConstruct` 从注入的 `List<Agent>` 自动构建）。

## 3. 状态机

### 3.1 任务级（`OrchestratorStatus`，写入 `agent_task.status`）

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> RUNNING : execute()
    RUNNING --> COMPLETED : 全部节点 SUCCESS
    RUNNING --> ROLLING_BACK : 节点终态失败 且 rollbackEnabled
    RUNNING --> FAILED : 节点失败 且 rollback 关闭/无已成功节点
    ROLLING_BACK --> FAILED : 补偿链执行完毕(best-effort)
    RUNNING --> CANCELED : cancel()
    COMPLETED --> [*]
    FAILED --> [*]
    CANCELED --> [*]
```

### 3.2 节点级（`NodeRunStatus`，写入 `agent_node_run.status`）

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> RUNNING : 开始执行
    RUNNING --> SUCCESS : AgentResult.success
    RUNNING --> RETRYING : 失败/超时 且 attempt ≤ maxRetries
    RETRYING --> RUNNING : backoff 后重试
    RUNNING --> TIMEOUT : Future.get 超时
    TIMEOUT --> RETRYING : attempt ≤ maxRetries
    RUNNING --> FAILED : 重试耗尽
    TIMEOUT --> FAILED : 重试耗尽
    FAILED --> [*] : 触发上游补偿
    SUCCESS --> [*]
    SKIPPED --> [*] : 回滚跳过未执行下游
```

### 3.3 补偿（写入 `agent_compensation.status`）

```mermaid
stateDiagram-v2
    [*] --> RUNNING : startCompensation
    RUNNING --> SUCCESS : 补偿成功 / 无补偿动作(只读)
    RUNNING --> FAILED : 补偿抛异常(best-effort,不阻断链)
    SUCCESS --> [*]
    FAILED --> [*]
```

## 4. 执行流程

```mermaid
sequenceDiagram
    participant S as AgentScheduler
    participant E as AgentExecutor
    participant T as OrchestratorTaskManager
    participant P as orchestratorPool
    participant A as Agent(节点)

    S->>E: execute(taskId) / executeAsync
    E->>T: getById → updateStatus(RUNNING)
    loop 遍历 AgentGraph 节点
        E->>T: startNodeRun(attempt)
        E->>P: submit(() -> A.execute(ctx))
        E->>E: Future.get(timeoutMs)
        alt 成功
            E->>T: successNodeRun + recordArtifact
            E->>E: executed.push(node)
        else 失败/超时 且 attempt ≤ maxRetries
            E->>T: retryNodeRun
            E->>E: sleep(backoff*attempt) → 重试
        else 重试耗尽
            E->>T: failNodeRun / timeoutNodeRun
            E->>E: finishFail
            opt rollbackEnabled
                E->>T: updateStatus(ROLLING_BACK)
                loop 逆序 executed 栈
                    E->>A: instanceof Compensable → compensation.execute
                    E->>T: successCompensation / failCompensation
                end
            end
            loop 下游未执行节点
                E->>T: skipNodeRun(SKIPPED)
            end
            E->>T: failTask(NODE_FAILED)
        end
    end
    E->>T: completeTask(REPORT 产物)
```

## 5. 核心决策

1. **共存不替换**：新建 `orchestrator` 包，不动 `engine.AgentExecutor`/`workflow.WorkflowExecutor`。`AgentExecutor` 同名不同包（`com.knowledge.agent.orchestrator`），FQN 区分。
2. **engine 零侵入**：补偿通过 `Compensable` 接口（`instanceof`）扩展，不修改 `engine.Agent`，依赖方向 `orchestrator → engine`。
3. **图模型=顺序+分支**：`AgentNode.next` 显式跳转实现分支，无并行调度（避免线程安全复杂度）。默认图由 `List<Agent>` 按 `@Order` 自动构建为 Planner→Knowledge→Analysis→Report→END。
4. **回滚=Saga 补偿链**：逆序执行已 SUCCESS 节点的 `Compensation`，best-effort 不阻断链；无副作用 Agent 记空补偿 SUCCESS 保证审计完整。
5. **持久化=新增两表**：`agent_node_run`（含 attempt/timeout/retry/skip 状态）+ `agent_compensation`，复用 `agent_task` 做任务级。
6. **超时=Future.cancel(true)**：硬中断 best-effort；LLM 阻塞调用响应中断取决于底层 HTTP 客户端，无可中断时退化为下节点前 wall-clock 软校验兜底。
7. **任务状态字符串共存**：`agent_task.status` 为 VARCHAR，编排层写 `OrchestratorStatus`（RUNNING/ROLLING_BACK 等），与 engine 层 EXECUTING 不冲突（一任务仅经一层执行）。

## 6. 配置

```yaml
agent:
  orchestrator:
    default-max-retries: 1          # 节点默认额外重试次数（不含首次）
    default-retry-backoff-ms: 500   # 重试退避（实际等待 = backoff * attempt）
    default-node-timeout-ms: 120000 # 节点默认硬超时（0=不限）
    executor-pool-size: 4           # 编排执行线程池大小
    rollback-enabled: true          # 是否启用补偿回滚
```

节点级策略优先取自 `AgentNode`，默认图构建时套用配置默认值；自定义图节点值按字面值生效（`maxRetries=0` 不重试，`timeoutMs=0` 不限）。

## 7. 验证

- 单元测试 25 个全绿：`AgentGraphTest`(11) + `AgentSchedulerTest`(7) + `AgentExecutorTest`(7，覆盖全成功/重试后成功/重试耗尽回滚/硬超时/下游跳过/回滚关闭/补偿链 best-effort)。
- 全模块 139 个测试零回归。
- `orchestrator_schema.sql` 可在 MySQL8 独立建表。
