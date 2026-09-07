<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <div class="detail-page">
    <!-- 顶部返回 + 摘要 -->
    <el-card shadow="never" class="summary-card">
      <template #header>
        <div class="summary-header">
          <div class="header-left">
            <el-button :icon="ArrowLeft" link @click="goBack">返回</el-button>
            <span class="title">执行过程查看</span>
            <el-tag :type="taskType === 'agent' ? 'warning' : 'success'" size="small">
              {{ taskType === 'agent' ? '自主 Agent' : '可控 Workflow' }}
            </el-tag>
            <StatusTag v-if="summary.status" :status="summary.status" />
            <el-tag v-if="live" type="warning" size="small" effect="dark">
              <span class="dot" /> 实时追踪中
            </el-tag>
          </div>
          <el-button :icon="Refresh" link @click="fetchDetail">刷新</el-button>
        </div>
      </template>

      <el-descriptions :column="3" border size="small" v-loading="loading">
        <el-descriptions-item label="任务ID">{{ summary.id || '—' }}</el-descriptions-item>
        <el-descriptions-item label="任务目标" :span="2">{{ summary.goal || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Token 消耗">{{ summary.tokenUsage ?? 0 }}</el-descriptions-item>
        <el-descriptions-item :label="taskType === 'agent' ? '步骤数' : '节点数'">
          {{ summary.stepCount ?? summary.nodeCount ?? 0 }}
        </el-descriptions-item>
        <el-descriptions-item v-if="summary.retryCount !== undefined" label="重试次数">
          {{ summary.retryCount }}
        </el-descriptions-item>
        <el-descriptions-item v-if="summary.currentNode" label="当前节点" :span="2">
          {{ summary.currentNode }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ summary.createTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ summary.finishedTime || '—' }}</el-descriptions-item>
        <el-descriptions-item v-if="summary.errorMsg" label="错误信息" :span="3">
          <span class="error-text">{{ summary.errorMsg }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 执行时间线 -->
    <el-card shadow="never" class="timeline-card">
      <template #header>
        <span class="card-title">{{ taskType === 'agent' ? '执行步骤' : '节点执行记录' }}</span>
      </template>

      <el-empty v-if="!timeline.length" description="暂无执行记录" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="(item, idx) in timeline"
          :key="idx"
          :type="timelineItemType(item.status)"
          :timestamp="item.timestamp"
          placement="top"
        >
          <!-- Agent 步骤 -->
          <div v-if="taskType === 'agent'" class="step-box">
            <div class="step-head">
              <span class="step-index">步骤 {{ item.stepIndex }}</span>
              <el-tag size="small" type="info">{{ item.agentType }}</el-tag>
              <StatusTag :status="item.status" />
              <span v-if="item.durationMs" class="duration">耗时 {{ formatMs(item.durationMs) }}</span>
            </div>
            <MarkdownView v-if="item.outputSummary" class="step-body" :content="item.outputSummary" />
            <div v-if="item.errorMsg" class="step-error">{{ item.errorMsg }}</div>
          </div>

          <!-- Workflow 节点 -->
          <div v-else class="step-box">
            <div class="step-head">
              <span class="step-index">{{ item.nodeName || item.nodeId }}</span>
              <el-tag size="small" :type="nodeTagType(item.nodeType)">{{ item.nodeType }}</el-tag>
              <StatusTag :status="item.status" />
              <span v-if="(item.attempt ?? 0) > 1" class="duration">第 {{ item.attempt }} 次尝试</span>
              <span v-if="item.durationMs" class="duration">耗时 {{ formatMs(item.durationMs) }}</span>
              <span v-if="item.tokenUsage" class="duration">Token {{ item.tokenUsage }}</span>
            </div>

            <!-- 审批信息 -->
            <div v-if="item.nodeType === 'HUMAN' && item.approved !== null && item.approved !== undefined" class="step-approval">
              <el-tag :type="item.approved === 1 ? 'success' : 'danger'" size="small">
                {{ item.approved === 1 ? '已通过' : '已驳回' }}
              </el-tag>
              <span v-if="item.approvalComment" class="approval-comment">{{ item.approvalComment }}</span>
            </div>

            <!-- 输入/输出 JSON 折叠 -->
            <el-collapse v-if="item.inputJson || item.outputJson" class="json-collapse">
              <el-collapse-item v-if="item.inputJson" title="输入" :name="`in-${idx}`">
                <pre class="json-block">{{ prettyJson(item.inputJson) }}</pre>
              </el-collapse-item>
              <el-collapse-item v-if="item.outputJson" title="输出" :name="`out-${idx}`">
                <pre class="json-block">{{ prettyJson(item.outputJson) }}</pre>
              </el-collapse-item>
            </el-collapse>

            <div v-if="item.errorMsg" class="step-error">{{ item.errorMsg }}</div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- 结果与产物 -->
    <el-card v-if="summary.result || artifacts.length" shadow="never" class="result-card">
      <template #header>
        <span class="card-title">最终结果与产物</span>
      </template>
      <MarkdownView v-if="summary.result" class="result-text" :content="summary.result" />
      <div v-for="(a, i) in artifacts" :key="i" class="artifact-item">
        <el-tag size="small" type="warning">{{ a.artifactType }}</el-tag>
        <pre class="json-block">{{ prettyJson(a.payload) }}</pre>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import StatusTag from '@/views/agent/components/StatusTag.vue'
import MarkdownView from '@/components/MarkdownView/index.vue'
import { getAgentApi, subscribeAgentStream } from '@/api/agent'
import { getWorkflowApi } from '@/api/workflow'

interface TimelineItem {
  // 通用
  status: string
  timestamp: string
  // agent
  stepIndex?: number
  agentType?: string
  outputSummary?: string
  durationMs?: number
  errorMsg?: string
  // workflow
  nodeId?: string
  nodeName?: string
  nodeType?: string
  attempt?: number
  inputJson?: string
  outputJson?: string
  approved?: number
  approvalComment?: string
  tokenUsage?: number
}

const route = useRoute()
const router = useRouter()

const taskType = computed<'agent' | 'workflow'>(() =>
  String(route.query.type) === 'workflow' ? 'workflow' : 'agent'
)
// 雪花ID超出 JS Number.MAX_SAFE_INTEGER 精度，必须以字符串形式传递给后端，
// 不能用 Number() 转换（会导致末尾几位被截断，如 ...87618 → ...87600，后端查不到任务）
const taskId = computed(() => String(route.query.id ?? ''))

const loading = ref(false)
const live = ref(false)
let eventSource: EventSource | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null

// 摘要（agent + workflow 字段并集，按需展示）
const summary = reactive<{
  id?: number | string
  goal?: string
  status?: string
  tokenUsage?: number
  stepCount?: number
  nodeCount?: number
  retryCount?: number
  currentNode?: string
  result?: string
  errorMsg?: string
  createTime?: string
  finishedTime?: string
}>({})

const timeline = ref<TimelineItem[]>([])
const artifacts = ref<AgentArtifactVo[]>([])

onMounted(() => {
  fetchDetail()
})

onUnmounted(() => {
  stopTracking()
})

async function fetchDetail() {
  if (!taskId.value) return
  loading.value = true
  try {
    if (taskType.value === 'agent') {
      const res = await getAgentApi(taskId.value)
      applyAgent(res.data)
    } else {
      const res = await getWorkflowApi(taskId.value)
      applyWorkflow(res.data)
    }
  } finally {
    loading.value = false
  }
}

function applyAgent(vo: AgentTaskVo) {
  Object.assign(summary, {
    id: vo.id,
    goal: vo.goal,
    status: vo.status,
    tokenUsage: vo.tokenUsage,
    stepCount: vo.stepCount,
    result: vo.result,
    errorMsg: vo.errorMsg,
    createTime: vo.createTime,
    finishedTime: vo.finishedTime
  })
  timeline.value = (vo.steps || []).map((s) => ({
    status: s.status,
    timestamp: s.startedAt || '',
    stepIndex: s.stepIndex,
    agentType: s.agentType,
    outputSummary: s.outputSummary,
    durationMs: s.durationMs,
    errorMsg: s.errorMsg
  }))
  artifacts.value = vo.artifacts || []
  // 非终态启动 SSE 实时追踪
  if (!isTerminal(vo.status)) {
    startAgentSse()
  } else {
    stopTracking()
  }
}

function applyWorkflow(vo: WorkflowTaskVo) {
  Object.assign(summary, {
    id: vo.id,
    goal: vo.goal,
    status: vo.status,
    tokenUsage: vo.tokenUsage,
    nodeCount: vo.nodeCount,
    retryCount: vo.retryCount,
    currentNode: vo.currentNode,
    result: vo.result,
    errorMsg: vo.errorMsg,
    createTime: vo.createTime,
    finishedTime: vo.finishedTime
  })
  timeline.value = (vo.nodeRuns || []).map((r) => ({
    status: r.status,
    timestamp: r.startedAt || '',
    nodeId: r.nodeId,
    nodeName: r.nodeName,
    nodeType: r.nodeType,
    attempt: r.attempt,
    inputJson: r.inputJson,
    outputJson: r.outputJson,
    approved: r.approved,
    approvalComment: r.approvalComment,
    tokenUsage: r.tokenUsage,
    durationMs: r.durationMs,
    errorMsg: r.errorMsg
  }))
  // Workflow 无 SSE，非终态则轮询
  if (!isTerminal(vo.status)) {
    startWorkflowPolling()
  } else {
    stopTracking()
  }
}

/** Agent SSE 实时追踪 */
function startAgentSse() {
  stopTracking()
  live.value = true
  eventSource = subscribeAgentStream(taskId.value, {
    onProgress: (vo) => applyAgent(vo),
    onComplete: (vo) => {
      applyAgent(vo)
      stopTracking()
    },
    onError: () => stopTracking()
  })
}

/** Workflow 轮询追踪（无 SSE 端点） */
function startWorkflowPolling() {
  stopTracking()
  live.value = true
  pollTimer = setInterval(async () => {
    try {
      const res = await getWorkflowApi(taskId.value)
      applyWorkflow(res.data)
    } catch {
      stopTracking()
    }
  }, 2500)
}

function stopTracking() {
  live.value = false
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function isTerminal(status?: string) {
  return !!status && ['COMPLETED', 'FAILED', 'CANCELED'].includes(status)
}

function timelineItemType(status?: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const s = (status || '').toUpperCase()
  if (s === 'COMPLETED' || s === 'SUCCESS') return 'success'
  if (s === 'RUNNING' || s === 'EXECUTING' || s === 'WAITING_HUMAN') return 'warning'
  if (s === 'FAILED') return 'danger'
  if (s === 'CANCELED' || s === 'SKIPPED') return 'info'
  return 'primary'
}

function nodeTagType(type?: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  return ({ START: 'success', TOOL: 'primary', LLM: 'warning', HUMAN: 'danger', END: 'info' } as const)[
    (type || '').toUpperCase()
  ] ?? 'info'
}

function formatMs(ms?: number) {
  if (!ms && ms !== 0) return '—'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

/** 尝试美化 JSON 字符串，非 JSON 原样返回 */
function prettyJson(raw?: string) {
  if (!raw) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function goBack() {
  router.back()
}
</script>

<style scoped lang="scss">
.detail-page {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .header-left {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .title {
    font-size: 16px;
    font-weight: 600;
  }
}

.error-text {
  color: var(--el-color-danger);
  word-break: break-all;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
}

.step-box {
  padding-bottom: 4px;
}

.step-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;

  .step-index {
    font-weight: 600;
  }

  .duration {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.step-body {
  margin-top: 8px;
  padding: 8px 10px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  word-break: break-word;
}

.step-error {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-color-danger);
}

.step-approval {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;

  .approval-comment {
    font-size: 13px;
    color: var(--el-text-color-regular);
  }
}

.json-collapse {
  margin-top: 8px;
  border: none;

  :deep(.el-collapse-item__header) {
    font-size: 13px;
    height: 28px;
  }
}

.json-block {
  margin: 0;
  padding: 10px;
  background: var(--el-fill-color-darker);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.result-text {
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.artifact-item {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
  margin-right: 4px;
  animation: pulse 1.2s infinite ease-in-out;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.4;
  }
}
</style>
