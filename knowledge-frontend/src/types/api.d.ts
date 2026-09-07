/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

/**
 * 后端统一响应结构（对应 Java 端 Result<T>）
 */
interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 登录请求参数 */
interface LoginForm {
  username: string
  password: string
  captcha?: string
}

/** 登录响应 */
interface LoginResult {
  token: string
}

/** 用户信息 */
interface UserInfo {
  id: number | string
  username: string
  nickname: string
  avatar: string
  email?: string
  phone?: string
  roles: string[]
  permissions: string[]
}

/** 分页结果（对应后端 IPage<T>） */
interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

/** 检索来源 */
interface RetrievalResult {
  text: string
  score: number
  source: string
  documentId: number | string
  chunkIndex: number
}

/** 知识库 */
interface KbItem {
  id: number | string
  name: string
  description: string
  ownerId: number | string
  docCount: number
  status: number
  createTime: string
}

/** 知识库成员 */
interface KbMember {
  id: number | string
  kbId: number | string
  userId: number | string
  username: string
  nickname: string
  role: string
  createTime: string
}

/** 文档 */
interface KbDocument {
  id: number | string
  kbId: number | string
  kbName?: string
  originalName: string
  fileSize: number
  fileType: string
  status: number
  chunkCount: number
  creatorId: number | string
  createTime: string
}

/** 解析任务 */
interface ParseTask {
  id: number
  documentId: number
  kbId: number
  originalName: string
  status: number
  errorMsg: string
  startTime: string
  endTime: string
  createTime: string
}

/** 聊天会话 */
interface ChatSessionVo {
  id: number
  kbId: number
  /** 关联知识库名称（普通对话为 null） */
  kbName?: string | null
  userId: number
  title: string
  createTime: string
  updateTime: string
}

/** 聊天消息 */
interface ChatMessageVo {
  id: number
  sessionId: number
  role: string
  content: string
  sources: RetrievalResult[]
  createTime: string
  /** 前端临时状态：流式生成中是否处于思考占位（首个 token 到达前） */
  thinking?: boolean
  /** 前端临时状态：思考占位文案（如"正在检索知识库…"/"正在生成回答…"） */
  thinkingText?: string
}

/** Prompt 模板（版本化：同 promptCode 多版本，status 为 DRAFT/PUBLISHED/ARCHIVED） */
interface PromptTemplate {
  id: number
  tenantId: number
  promptCode: string
  name: string
  type: string
  version: number
  content: string
  variables: string
  status: string
  remark: string
  creatorId: number
  deleted: number
  createTime: string
  updateTime: string
}

/** Prompt 模板新增/编辑请求 */
interface PromptTemplateRequest {
  id?: number
  promptCode: string
  name: string
  type: string
  content: string
  variables?: string
  remark?: string
}

/** Prompt 模板测试请求 */
interface PromptTestRequest {
  promptCode?: string
  content?: string
  variables?: Record<string, string>
}

/** Prompt 模板测试结果 */
interface PromptTestResult {
  renderedPrompt: string
  output: string
  tokens: number
}

/** 操作日志 */
interface OperLog {
  id: number
  title: string
  businessType: number
  method: string
  requestUrl: string
  requestParam: string
  responseResult: string
  status: number
  errorMsg: string
  operIp: string
  operUser: string
  costTime: number
  createTime: string
}

// ==================== Agent / Workflow ====================

/** Agent 任务状态（对应后端 AgentStatus 枚举） */
type AgentStatusType = 'CREATED' | 'EXECUTING' | 'COMPLETED' | 'FAILED' | 'CANCELED'

/** Workflow 任务状态（对应后端 WorkflowStatus 枚举） */
type WorkflowStatusType =
  | 'CREATED'
  | 'RUNNING'
  | 'WAITING_HUMAN'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELED'

/** Agent 启动请求 */
interface AgentStartRequest {
  kbId: number | string
  goal: string
  sessionId?: number | string
}

/** Agent 执行步骤（对应 AgentTaskVo.StepVo） */
interface AgentStepVo {
  stepIndex: number
  agentType: string
  status: string
  outputSummary: string
  durationMs: number
  startedAt: string
  finishedAt: string
  errorMsg: string
}

/** Agent 产物（对应 AgentTaskVo.ArtifactVo） */
interface AgentArtifactVo {
  artifactType: string
  payload: string
}

/** Agent 任务视图（列表为轻量不含 steps/artifacts，详情含） */
interface AgentTaskVo {
  id: number | string
  kbId: number | string
  goal: string
  status: AgentStatusType
  result: string
  stepCount: number
  tokenUsage: number
  errorCode: string
  errorMsg: string
  createTime: string
  finishedTime: string
  steps?: AgentStepVo[]
  artifacts?: AgentArtifactVo[]
}

/** Workflow 流程定义（对应 WorkflowDefinition 实体） */
interface WorkflowDefinition {
  id: number
  tenantId: number
  code: string
  name: string
  version: number
  status: 'ENABLED' | 'DISABLED'
  /** 流程定义 JSON（WorkflowDefinitionModel 序列化） */
  definition: string
  description: string
  createTime: string
  updateTime: string
}

/** Workflow 启动请求 */
interface WorkflowStartRequest {
  definitionId?: number | string
  code?: string
  kbId?: number | string
  businessKey?: string
  goal: string
}

/** Workflow 审批请求 */
interface ApproveRequest {
  approved: boolean
  comment?: string
}

/** Workflow 节点执行记录（对应 WorkflowTaskVo.NodeRunVo） */
interface WorkflowNodeRunVo {
  id: number
  nodeId: string
  nodeName: string
  nodeType: 'START' | 'TOOL' | 'LLM' | 'HUMAN' | 'END'
  runIndex: number
  attempt: number
  status: string
  inputJson: string
  outputJson: string
  approved: number
  approverUserId: number
  approvalComment: string
  tokenUsage: number
  durationMs: number
  errorMsg: string
  startedAt: string
  finishedAt: string
}

/** Workflow 流程任务视图（列表不含 nodeRuns，详情含） */
interface WorkflowTaskVo {
  id: number | string
  tenantId: number
  userId: number | string
  definitionId: number | string
  definitionCode: string
  kbId: number | string
  businessKey: string
  goal: string
  status: WorkflowStatusType
  currentNode: string
  result: string
  nodeCount: number
  retryCount: number
  tokenUsage: number
  errorCode: string
  errorMsg: string
  finishedTime: string
  createTime: string
  nodeRuns?: WorkflowNodeRunVo[]
}

// ==================== AI 运营管理 ====================

/** AI 模型调用日志（对应 AiCallLog 实体，审计型日志无软删） */
interface AiCallLog {
  id: number
  tenantId: number
  userId: number | null
  username: string
  /** 业务模块 rag_chat/agent/workflow/prompt_test/embedding/document_compare/report_generate */
  module: string
  /** 调用类型 CHAT/EMBEDDING */
  bizType: string
  modelName: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  /** 耗时（毫秒） */
  durationMs: number
  /** 费用（元） */
  cost: number
  /** SUCCESS/FAILED */
  status: string
  errorMsg: string | null
  createTime: string
}

/** AI 调用日志分页查询条件 */
interface AiCallLogQuery {
  pageNo?: number
  pageSize?: number
  module?: string
  bizType?: string
  modelName?: string
  status?: string
  userId?: number
  /** yyyy-MM-dd HH:mm:ss */
  startTime?: string
  /** yyyy-MM-dd HH:mm:ss */
  endTime?: string
}

/** AI 调用统计查询条件（时间范围，默认最近 7 天） */
interface AiCallStatsQuery {
  startTime?: string
  endTime?: string
}

/** 维度统计项（模型/用户通用，对应 AiCallStatsVo.ItemStat） */
interface AiCallStatItem {
  /** 维度名：模型名 或 用户名 */
  key: string
  /** 用户ID（byUser 用） */
  userId: number | null
  calls: number
  tokens: number
  cost: number
}

/** 日期统计项（对应 AiCallStatsVo.DailyStat） */
interface AiCallDailyStat {
  /** 日期 yyyy-MM-dd */
  day: string
  calls: number
  tokens: number
  cost: number
}

/** AI 调用统计结果（概览 + 按模型/用户/日期排行） */
interface AiCallStatsVo {
  totalCalls: number
  successCount: number
  failedCount: number
  totalTokens: number
  promptTokens: number
  completionTokens: number
  /** 总费用（元） */
  totalCost: number
  /** 平均耗时（ms） */
  avgDurationMs: number
  /** 按模型统计（费用倒序） */
  byModel: AiCallStatItem[]
  /** 按用户统计（费用倒序，Top 10） */
  byUser: AiCallStatItem[]
  /** 按日期统计（最近 30 天，日期倒序） */
  byDay: AiCallDailyStat[]
}

// ==================== AI 运维中心（v3-7） ====================

/** 运维看板 7 指标卡片 */
interface OpsMetricCard {
  /** 指标键 modelCalls/tokenUsage/agentDuration/milvusQueries/esQueries/mqLatency/workflowSuccessRate */
  key: string
  title: string
  value: number
  unit: string
  sub: string | null
}

/** 模型调用趋势点 */
interface OpsCallTrendPoint {
  day: string
  calls: number
  tokens: number
  cost: number
}

/** 资源延迟（柱状图） */
interface OpsResourceLatency {
  resource: string
  calls: number
  avgDurationMs: number
  failedCount: number
}

/** 运维看板聚合结果（GET /ops/dashboard） */
interface OpsDashboardVo {
  cards: OpsMetricCard[]
  callTrend: OpsCallTrendPoint[]
  costTrend: CostDailyPoint[]
  latencyByResource: OpsResourceLatency[]
}

/** 单日费用点（趋势折线图） */
interface CostDailyPoint {
  day: string
  calls: number
  tokens: number
  cost: number
}

/** 单模型费用点（占比饼图） */
interface CostModelPoint {
  model: string
  calls: number
  tokens: number
  cost: number
}

/** 预算校验结果 */
interface CostBudgetStatus {
  monthCost: number
  budget: number
  exceeded: boolean
  usedPercent: number
}

/** 月度费用预测 */
interface CostForecast {
  monthToDate: number
  dailyAvg7d: number
  remainingDays: number
  forecast: number
  note: string
}

/** 基础设施资源统计概览 */
interface InfraResourceStats {
  resource: string
  calls: number
  successCount: number
  failedCount: number
  avgDurationMs: number
  maxDurationMs: number
}

/** 基础设施时序点（按日 × 资源） */
interface InfraMetricPoint {
  day: string
  resource: string
  calls: number
  avgDurationMs: number
}

/** 基础设施指标聚合（概览 + 时序） */
interface InfraOverview {
  overview: InfraResourceStats[]
  series: InfraMetricPoint[]
}

/** Agent 按日趋势点 */
interface AgentDailyPoint {
  day: string
  total: number
  completed: number
  avgDurationMs: number
}

/** Agent 耗时统计（指标 #3） */
interface AgentStatsVo {
  total: number
  completed: number
  failed: number
  avgDurationMs: number
  maxDurationMs: number
  tokens: number
  dailyTrend: AgentDailyPoint[]
}

/** Workflow 按日趋势点 */
interface WorkflowDailyPoint {
  day: string
  total: number
  completed: number
  successRate: number
}

/** Workflow 成功率统计（指标 #7） */
interface WorkflowStatsVo {
  total: number
  completed: number
  failed: number
  successRate: number
  tokens: number
  dailyTrend: WorkflowDailyPoint[]
}

/** 分布式追踪 span 实体（对应 OpsTrace） */
interface OpsTrace {
  id: number
  tenantId: number
  traceId: string
  spanId: string
  parentSpanId: string | null
  spanName: string
  /** ROOT/SEARCH/LLM/TOOL/MQ */
  spanType: string
  startTime: string
  durationMs: number
  /** OK/ERROR */
  status: string
  attributesJson: string | null
  createTime: string
}

/** 链路追踪查询条件 */
interface TraceQuery {
  pageNo?: number
  pageSize?: number
  traceId?: string
  spanType?: string
  start?: string
  end?: string
}

/** 链路 span 树节点（递归） */
interface TraceTreeVo {
  traceId: string
  spanId: string
  parentSpanId: string | null
  spanName: string
  spanType: string
  startTime: string
  durationMs: number
  status: string
  attributesJson: string | null
  children: TraceTreeVo[]
}

/** 告警规则实体 */
interface AlertRule {
  id: number
  tenantId: number
  name: string
  resource: string
  metric: string
  operator: string
  threshold: number
  windowMinutes: number
  level: string
  enabled: number
  createTime: string
  updateTime: string
}

/** 告警规则 CRUD 请求 */
interface AlertRuleRequest {
  name: string
  resource: string
  metric: string
  operator: string
  threshold: number
  windowMinutes?: number
  level?: string
  enabled?: number
}

// ==================== 工作台统计（v3-8） ====================

/** 工作台统计结果（GET /dashboard/stats） */
interface DashboardStats {
  /** 知识库数量 */
  kbCount: number
  /** 文档数量 */
  docCount: number
  /** 切片数量 */
  chunkCount: number
  /** 今日对话数 */
  todayChatCount: number
}

// ==================== 自定义 Agent（v3-9） ====================

/** 数据源模式：kb 知识库 / content 自定义内容 / log 上传日志 / plain 纯模型回答（不检索上下文） */
type AgentSourceMode = 'kb' | 'content' | 'log' | 'plain'

/** 执行模型：single 单步流式 / multi 多步骤流程 */
type AgentExecMode = 'single' | 'multi'

/** 多步骤流程单步定义（agent_definition.steps JSON 数组元素） */
interface StepDefinition {
  stepName: string
  prompt: string
  /** 输入来源：context=外部注入上下文 / prev=上一步输出 */
  inputFrom?: 'context' | 'prev'
  outputKey: string
}

/** 自定义 Agent 定义请求（创建/编辑） */
interface AgentDefinitionRequest {
  id?: number | string
  name: string
  description?: string
  avatar?: string
  systemPrompt: string
  /** 数据源模式，缺省 kb（后端默认），运行时由对话框 inputType 覆盖 */
  sourceMode?: AgentSourceMode
  kbId?: number | string
  execMode: AgentExecMode
  /** execMode=multi 时为 JSON 字符串（StepDefinition[] 序列化） */
  steps?: string
}

/** 自定义 Agent 定义（出参） */
interface AgentDefinitionVo {
  id: number | string
  tenantId: number | string
  userId: number | string
  name: string
  description: string
  avatar: string
  systemPrompt: string
  sourceMode: AgentSourceMode
  kbId: number | string | null
  kbName: string
  execMode: AgentExecMode
  steps: string
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  createTime: string
  updateTime: string
}

/** 运行请求（单步 chat / 多步 run 共用） */
interface ChatStartRequest {
  question: string
  /** 会话ID（首轮可空，后端生成后经 session 事件回传，后续轮次携带以延续记忆） */
  sessionId?: number | string
  /** 输入类型，默认按定义 sourceMode */
  inputType?: AgentSourceMode
  /** inputType=kb 时使用，默认取定义绑定 */
  kbId?: number | string
  /** inputType=content 时必填 */
  content?: string
  /** inputType=log 时必填（来自上传接口 fileRef） */
  fileRef?: string
}

/** 日志/文档上传结果 */
interface UploadLogResult {
  fileRef: string
  originalName: string
  /** 注入模式：full 全文注入 / chunked 切片检索 */
  mode: 'full' | 'chunked'
  charCount: number
  chunkCount: number
}

/** 多步流程单步运行状态（SSE progress） */
interface AgentStepRunVo {
  stepName: string
  outputKey: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  output: string
}

/** 自定义 Agent 运行记录（出参） */
interface AgentRunVo {
  id: number | string
  tenantId: number | string
  userId: number | string
  agentId: number | string
  agentName: string
  sessionId: number | string | null
  inputType: AgentSourceMode
  kbId: number | string | null
  question: string
  contextRef: string
  result: string
  /** 多步各步输出 JSON（multi 模式） */
  stepsResult: string
  tokenUsage: number
  status: 'CREATED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELED'
  errorMsg: string
  createTime: string
  updateTime: string
  finishedTime: string
}

/** 我的任务聚合条目（AGENT=自主式 Agent 任务 / CUSTOM_AGENT=自定义 Agent 运行） */
interface MyTaskVo {
  taskType: 'AGENT' | 'CUSTOM_AGENT'
  taskId: number | string
  agentId: number | string | null
  agentName: string
  title: string
  status: string
  tokenUsage: number
  errorMsg: string
  createTime: string
  finishedTime: string
}

// ==================== 账户模块（v3-9） ====================

/** 更新资料请求 */
interface ProfileRequest {
  nickname?: string
  avatar?: string
  email?: string
  phone?: string
}

/** 修改密码请求 */
interface PasswordRequest {
  oldPassword: string
  newPassword: string
}
