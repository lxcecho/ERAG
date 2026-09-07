<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <div class="agent-detail" v-loading="loadingDef">
    <!-- 头部：Agent 信息 -->
    <div class="agent-header">
      <div class="agent-info">
        <el-avatar :size="44" :src="def.avatar || undefined">
          <el-icon><Cpu /></el-icon>
        </el-avatar>
        <div>
          <div class="agent-name">{{ def.name }}</div>
          <div class="agent-desc">{{ def.description || '—' }}</div>
        </div>
      </div>
      <div class="agent-tags">
        <el-tag size="small" :type="def.execMode === 'multi' ? 'warning' : 'primary'" effect="plain">
          {{ def.execMode === 'multi' ? '多步流程' : '单步流式' }}
        </el-tag>
        <el-tag size="small" :type="def.status === 'PUBLISHED' ? 'success' : 'warning'" effect="plain">
          {{ def.status === 'PUBLISHED' ? '已发布' : '草稿' }}
        </el-tag>
        <el-button link type="primary" :icon="Plus" @click="newChat">新对话</el-button>
        <el-button link type="primary" :icon="ArrowLeft" @click="router.back()">返回</el-button>
      </div>
    </div>

    <div class="agent-body">
      <!-- 对话区 -->
      <div class="chat-panel">
        <div ref="chatBox" class="chat-messages">
          <div v-if="messages.length === 0" class="chat-empty">
            <el-icon :size="40"><ChatDotRound /></el-icon>
            <p>输入分析诉求，AI 将基于知识库检索或上传的文件分析回答</p>
          </div>

          <div v-for="(msg, idx) in messages" :key="idx" class="msg-row" :class="msg.role">
            <el-avatar :size="32" :src="msg.role === 'user' ? (userStore.userInfo?.avatar || undefined) : (def.avatar || undefined)">
              <el-icon><UserFilled v-if="msg.role === 'user'" /><Cpu v-else /></el-icon>
            </el-avatar>
            <div class="msg-bubble">
              <!-- 多步流程步骤进度 -->
              <div v-if="msg.steps && msg.steps.length" class="step-list">
                <div
                  v-for="(step, si) in msg.steps"
                  :key="si"
                  class="step-item"
                  :class="step.status?.toLowerCase()"
                >
                  <el-icon class="step-icon">
                    <Loading v-if="step.status === 'RUNNING'" />
                    <CircleCheckFilled v-else-if="step.status === 'SUCCESS'" />
                    <CircleCloseFilled v-else-if="step.status === 'FAILED'" />
                    <Clock v-else />
                  </el-icon>
                  <span class="step-name">{{ step.stepName }}</span>
                  <span v-if="step.output" class="step-output">{{ truncate(step.output, 120) }}</span>
                </div>
              </div>

              <div v-if="msg.content" class="msg-content">
                <MarkdownView :content="msg.content" />
              </div>

              <div v-if="msg.status === 'generating' && !msg.content" class="typing">
                <span class="dot" /><span class="dot" /><span class="dot" />
              </div>

              <div v-if="msg.error" class="msg-error">{{ msg.error }}</div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div
          class="chat-input"
          :class="{ 'drag-over': dragOver }"
          @dragover.prevent="dragOver = true"
          @dragleave.prevent="dragOver = false"
          @drop.prevent="onDrop"
        >
          <!-- 工具条：知识库检索开关 + 知识库下拉 + 文件上传（支持拖入对话框） -->
          <div class="input-toolbar">
            <el-switch
              v-model="ragEnabled"
              size="small"
              inline-prompt
              active-text="知识库检索"
              inactive-text="纯模型回答"
            />
            <el-select
              v-if="ragEnabled"
              v-model="selectedKbId"
              size="small"
              placeholder="选择知识库"
              filterable
              style="width: 220px"
            >
              <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
            </el-select>
            <span class="toolbar-spacer" />
            <el-button size="small" :icon="Upload" :loading="uploading" @click="pickFile">
              上传文件
            </el-button>
            <input
              ref="fileInput"
              type="file"
              hidden
              accept=".txt,.log,.md,.json,.csv,.xml,.yml,.yaml,.properties,.conf,.ini,.html"
              @change="onPickChange"
            />
          </div>

          <!-- 附件徽标 -->
          <div v-if="attachInfo" class="attach-badge">
            <el-icon><Document /></el-icon>
            <span class="attach-name">{{ attachInfo.originalName }}</span>
            <el-tag size="small" :type="attachInfo.mode === 'full' ? 'success' : 'warning'">
              {{ attachInfo.mode === 'full' ? '全文注入' : `切片检索（${attachInfo.chunkCount} 片）` }}
            </el-tag>
            <span class="attach-chars">{{ attachInfo.charCount }} 字符</span>
            <el-icon class="attach-close" @click="clearAttach"><CircleClose /></el-icon>
          </div>

          <div class="input-row">
            <el-input
              v-model="question"
              type="textarea"
              :rows="2"
              maxlength="1024"
              show-word-limit
              :placeholder="questionPlaceholder"
              @keydown.enter.exact.prevent="onSend"
            />
            <el-button
              type="primary"
              class="send-btn"
              :loading="sending"
              :disabled="!canSend"
              @click="onSend"
            >
              发送
            </el-button>
          </div>
        </div>
      </div>

      <!-- 历史运行记录 -->
      <div class="history-panel">
        <div class="history-title">
          <span>运行记录</span>
          <el-button link type="primary" :icon="Refresh" @click="loadHistory" />
        </div>
        <el-scrollbar class="history-scroll">
          <div
            v-for="run in historyList"
            :key="run.id"
            class="history-item"
            :class="{ active: run.id === activeRunId }"
            @click="viewRun(run)"
          >
            <div class="history-q">{{ truncate(run.question, 40) }}</div>
            <div class="history-meta">
              <el-tag size="small" :type="historyStatusType(run.status)">{{ historyStatusLabel(run.status) }}</el-tag>
              <span class="history-time">{{ run.createTime }}</span>
            </div>
          </div>
          <el-empty v-if="historyList.length === 0" description="暂无运行记录" :image-size="60" />
        </el-scrollbar>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ChatDotRound, CircleCheckFilled, CircleClose, CircleCloseFilled, Clock, Cpu, Document, Loading, Plus, Refresh, Upload, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MarkdownView from '@/components/MarkdownView/index.vue'
import { useUserStore } from '@/stores/user'
import {
  getAgentDefApi,
  getAgentRunApi,
  chatCustomAgent,
  runAgentApi,
  subscribeAgentRunStream,
  uploadLogApi,
  pageAgentRunApi
} from '@/api/customAgent'
import { pageKbApi } from '@/api/kb'

interface ChatMsg {
  role: 'user' | 'assistant'
  content: string
  /** generating / running / done / error */
  status?: string
  steps?: AgentStepRunVo[]
  error?: string
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const agentId = computed(() => String(route.params.id ?? ''))
const def = reactive<AgentDefinitionVo>({
  id: '',
  tenantId: '',
  userId: '',
  name: '自定义 Agent',
  description: '',
  avatar: '',
  systemPrompt: '',
  sourceMode: 'kb',
  kbId: null,
  kbName: '',
  execMode: 'single',
  steps: '',
  status: 'DRAFT',
  createTime: '',
  updateTime: ''
})
const loadingDef = ref(true)

const messages = ref<ChatMsg[]>([])
const chatBox = ref<HTMLElement>()
const question = ref('')
const uploading = ref(false)
const sending = ref(false)
let abortController: AbortController | null = null

const attachInfo = ref<UploadLogResult | null>(null)
/** 知识库检索开关（始终显示）：开启=RAG 检索回答（下拉选库），关闭=纯模型回答 */
const ragEnabled = ref(true)
/** 知识库下拉选项与选中项 */
const kbList = ref<KbItem[]>([])
const selectedKbId = ref<number | string>('')
/** 拖拽文件悬停高亮 */
const dragOver = ref(false)
const fileInput = ref<HTMLInputElement>()

/** 会话ID（跨轮记忆）：首轮后端生成经 session 事件回传，localStorage 按 agent 持久化复用 */
const sessionId = ref<string>('')
const sessionKey = () => `customAgentSession_${agentId.value}`

function persistSession(id: number | string) {
  const sid = String(id)
  sessionId.value = sid
  localStorage.setItem(sessionKey(), sid)
}

function restoreSession() {
  const saved = localStorage.getItem(sessionKey())
  if (saved) sessionId.value = saved
}

/** 加载用户有权限的知识库；默认选中定义绑定的库，否则选第一个 */
async function loadKbList() {
  try {
    const res = await pageKbApi({ pageNo: 1, pageSize: 100 })
    kbList.value = res.data.records
    if (!selectedKbId.value && kbList.value.length > 0) {
      selectedKbId.value = def.kbId ?? kbList.value[0].id
    }
  } catch {
    /* 无知识库权限时不阻断对话 */
  }
}

function kbName(id: number | string | undefined) {
  const kb = kbList.value.find(k => String(k.id) === String(id))
  return kb?.name || '知识库'
}

const historyList = ref<AgentRunVo[]>([])
const activeRunId = ref<number | string>('')

const questionPlaceholder = computed(() => {
  if (def.execMode === 'multi') return '输入分析诉求，将按配置的多步流程依次执行'
  if (ragEnabled.value && selectedKbId.value) return `基于「${kbName(selectedKbId.value)}」提问…`
  if (attachInfo.value) return '基于上传的文件提问，如：分析最近一次部署失败的根因'
  return '输入分析诉求，可开启知识库检索或拖入文件作为上下文…'
})

const canSend = computed(() => {
  if (sending.value) return false
  if (!question.value.trim()) return false
  if (ragEnabled.value && !attachInfo.value && !selectedKbId.value) return false
  return true
})

onMounted(async () => {
  try {
    const res = await getAgentDefApi(agentId.value)
    Object.assign(def, res.data)
  } catch {
    /* 详情加载失败：提示由全局拦截器处理 */
  } finally {
    loadingDef.value = false
  }
  restoreSession()
  await loadKbList()
  loadHistory()
})

onUnmounted(() => {
  abortController?.abort()
})

/* ---------- 发送 ---------- */

async function onSend() {
  if (!canSend.value) return
  const q = question.value.trim()
  question.value = ''
  messages.value.push({ role: 'user', content: q })

  const body: ChatStartRequest = { question: q }
  if (sessionId.value) body.sessionId = sessionId.value
  if (attachInfo.value) {
    // 附件优先：作为日志/文档上下文分析
    body.inputType = 'log'
    body.fileRef = attachInfo.value.fileRef
  } else if (ragEnabled.value) {
    // 知识库检索开关：开=RAG 检索回答（下拉选库），关=纯模型回答
    body.inputType = 'kb'
    body.kbId = selectedKbId.value || undefined
  } else {
    body.inputType = 'plain'
  }

  if (def.execMode === 'multi') {
    await sendMulti(body)
  } else {
    await sendSingle(body)
  }
  scrollToBottom()
}

/** 单步流式：token 逐段追加 */
function sendSingle(body: ChatStartRequest) {
  return new Promise<void>((resolve) => {
    const msg: ChatMsg = { role: 'assistant', content: '', status: 'generating' }
    messages.value.push(msg)
    sending.value = true
    abortController = chatCustomAgent(agentId.value, body, {
      onStatus: () => {
        msg.status = 'generating'
      },
      onSession: (sid) => {
        persistSession(sid)
      },
      onToken: (token) => {
        msg.status = 'generating'
        msg.content += token
        scrollToBottom()
      },
      onDone: (runId) => {
        msg.status = 'done'
        activeRunId.value = runId
        sending.value = false
        loadHistory()
        resolve()
      },
      onError: (err) => {
        msg.status = 'error'
        msg.error = err
        sending.value = false
        resolve()
      }
    })
  })
}

/** 多步流程：异步执行 + SSE 进度轮询 */
async function sendMulti(body: ChatStartRequest) {
  sending.value = true
  try {
    const res = await runAgentApi(agentId.value, body)
    const runId = res.data
    // 多步接口不回传 sessionId，从运行详情补齐并持久化（保证后续轮次延续记忆）
    try {
      const detail = await getAgentRunApi(runId)
      if (detail.data.sessionId) persistSession(detail.data.sessionId)
    } catch {
      /* 详情补齐失败不阻断对话 */
    }
    const msg: ChatMsg = { role: 'assistant', content: '', status: 'running', steps: initialSteps() }
    messages.value.push(msg)
    activeRunId.value = runId
    abortController = subscribeAgentRunStream(String(runId), {
      onProgress: (vo) => applyRunProgress(msg, vo),
      onComplete: (vo) => {
        applyRunProgress(msg, vo)
        msg.status = 'done'
        sending.value = false
        loadHistory()
      },
      onError: (err) => {
        msg.status = 'error'
        msg.error = err
        sending.value = false
      }
    })
  } catch {
    sending.value = false
  }
}

function initialSteps(): AgentStepRunVo[] {
  if (!def.steps) return []
  try {
    const steps = JSON.parse(def.steps) as StepDefinition[]
    return steps.map((s) => ({ stepName: s.stepName, outputKey: s.outputKey, status: 'PENDING', output: '' }))
  } catch {
    return []
  }
}

function applyRunProgress(msg: ChatMsg, vo: AgentRunVo) {
  if (vo.stepsResult) {
    try {
      msg.steps = JSON.parse(vo.stepsResult) as AgentStepRunVo[]
    } catch {
      /* 忽略解析异常 */
    }
  }
  if (vo.result) {
    msg.content = vo.result
  }
  if (vo.status === 'FAILED' && vo.errorMsg) {
    msg.error = vo.errorMsg
  }
  scrollToBottom()
}

/* ---------- 文件上传（拖拽 / 点选） ---------- */

/** 拖入对话框：取首个文件上传 */
function onDrop(e: DragEvent) {
  dragOver.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) void doUpload(file)
}

/** 点击「上传文件」按钮 */
function pickFile() {
  fileInput.value?.click()
}

/** 文件选择器变更 */
async function onPickChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (fileInput.value) fileInput.value.value = ''
  if (file) await doUpload(file)
}

/** 上传并记录附件信息（上限 100MB，后端同步限制） */
async function doUpload(raw: File) {
  if (raw.size > 100 * 1024 * 1024) return ElMessage.warning('文件不能超过 100MB')
  uploading.value = true
  try {
    const res = await uploadLogApi(raw)
    attachInfo.value = res.data
    ElMessage.success(`上传成功（${res.data.mode === 'full' ? '全文注入' : '切片检索'}）`)
  } finally {
    uploading.value = false
  }
}

/** 移除附件 */
function clearAttach() {
  attachInfo.value = null
}

/* ---------- 历史记录 ---------- */

async function loadHistory() {
  try {
    const res = await pageAgentRunApi({ current: 1, size: 20, agentId: agentId.value })
    // 后端已按 agentId 过滤，无需客户端再次筛选
    historyList.value = res.data.records
    // 当前无 sessionId 时从最近一条历史恢复（刷新页面后继续同一会话）
    if (!sessionId.value && historyList.value.length > 0) {
      const last = historyList.value.find((r) => r.sessionId)
      if (last?.sessionId) persistSession(last.sessionId)
    }
  } catch {
    /* 忽略 */
  }
}

function viewRun(run: AgentRunVo) {
  activeRunId.value = run.id
  if (!run.result && !run.stepsResult) return
  const msg: ChatMsg = { role: 'assistant', content: run.result || '', status: 'done' }
  if (run.stepsResult) {
    try {
      msg.steps = JSON.parse(run.stepsResult) as AgentStepRunVo[]
    } catch {
      /* 忽略 */
    }
  }
  // 替换当前对话为所选运行记录的问答快照，避免多次点击堆叠历史
  messages.value = [
    { role: 'user', content: run.question },
    msg
  ]
  scrollToBottom()
}

/** 新对话：清空消息并重置会话（清空按 agent 持久化的 sessionId，下次发送将开启全新会话） */
function newChat() {
  messages.value = []
  activeRunId.value = ''
  sessionId.value = ''
  localStorage.removeItem(sessionKey())
  ElMessage.success('已开始新对话')
}

/* ---------- 工具 ---------- */

function scrollToBottom() {
  nextTick(() => {
    const el = chatBox.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function truncate(text: string, len: number) {
  if (!text) return ''
  return text.length > len ? text.slice(0, len) + '…' : text
}

function historyStatusLabel(status: string) {
  return (
    {
      CREATED: '待执行',
      RUNNING: '执行中',
      COMPLETED: '已完成',
      FAILED: '失败',
      CANCELED: '已取消'
    } as Record<string, string>
  )[status] || status
}

function historyStatusType(status: string): 'primary' | 'success' | 'danger' | 'info' | 'warning' {
  return (
    {
      CREATED: 'info',
      RUNNING: 'warning',
      COMPLETED: 'success',
      FAILED: 'danger',
      CANCELED: 'info'
    } as Record<string, 'primary' | 'success' | 'danger' | 'info' | 'warning'>
  )[status] || 'info'
}
</script>

<style scoped lang="scss">
.agent-detail {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--navbar-height) - 40px);
}

.agent-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  margin-bottom: 12px;

  .agent-info {
    display: flex;
    align-items: center;
    gap: 12px;

    .agent-name {
      font-size: 16px;
      font-weight: 600;
    }

    .agent-desc {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      margin-top: 2px;
    }
  }

  .agent-tags {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.agent-body {
  flex: 1;
  display: flex;
  gap: 12px;
  min-height: 0;
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  min-width: 0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;

  .chat-empty {
    margin: auto;
    text-align: center;
    color: var(--el-text-color-secondary);

    p {
      margin-top: 8px;
      font-size: 13px;
    }
  }
}

.msg-row {
  display: flex;
  gap: 10px;

  &.user {
    flex-direction: row-reverse;

    .msg-bubble {
      background: var(--el-color-primary-light-8);
    }
  }

  .msg-bubble {
    max-width: 76%;
    background: var(--el-fill-color-light);
    border-radius: 8px;
    padding: 10px 12px;
    font-size: 14px;
    line-height: 1.7;
    word-break: break-word;
  }
}

.msg-content {
  :deep(.markdown-body) {
    font-size: 14px;
  }
}

.msg-error {
  color: var(--el-color-danger);
  font-size: 13px;
  margin-top: 6px;
}

.typing {
  display: flex;
  gap: 4px;
  padding: 6px 2px;

  .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--el-text-color-secondary);
    animation: blink 1.2s infinite ease-in-out;

    &:nth-child(2) {
      animation-delay: 0.2s;
    }

    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }
}

@keyframes blink {
  0%,
  100% {
    opacity: 0.3;
  }
  50% {
    opacity: 1;
  }
}

.step-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;

  .step-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 10px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    font-size: 13px;

    &.success {
      border-color: var(--el-color-success-light-5);
      .step-icon {
        color: var(--el-color-success);
      }
    }

    &.running {
      border-color: var(--el-color-warning-light-5);
      background: var(--el-color-warning-light-9);
      .step-icon {
        color: var(--el-color-warning);
      }
    }

    &.failed {
      border-color: var(--el-color-danger-light-5);
      .step-icon {
        color: var(--el-color-danger);
      }
    }

    .step-icon {
      flex-shrink: 0;
    }

    .step-name {
      font-weight: 600;
      flex-shrink: 0;
    }

    .step-output {
      color: var(--el-text-color-secondary);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

.chat-input {
  border-top: 1px solid var(--el-border-color-lighter);
  padding: 12px 16px;

  &.drag-over {
    background: var(--el-color-primary-light-9);
    outline: 2px dashed var(--el-color-primary);
    outline-offset: -4px;
    border-radius: 8px;
  }

  .input-toolbar {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 8px;

    .toolbar-hint {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }

    .toolbar-spacer {
      flex: 1;
    }
  }

  .attach-badge {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px;
    border: 1px solid var(--el-border-color-lighter);
    background: var(--el-fill-color-light);
    border-radius: 6px;
    margin-bottom: 8px;
    font-size: 13px;

    .attach-name {
      max-width: 220px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-weight: 600;
    }

    .attach-chars {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }

    .attach-close {
      cursor: pointer;
      color: var(--el-text-color-secondary);

      &:hover {
        color: var(--el-color-danger);
      }
    }
  }

  .input-extra {
    margin-bottom: 8px;
  }

  .input-row {
    display: flex;
    align-items: flex-end;
    gap: 10px;

    .send-btn {
      height: 54px;
      width: 80px;
    }
  }
}

.history-panel {
  width: 280px;
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  min-height: 0;

  .history-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 14px;
    font-weight: 600;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .history-scroll {
    flex: 1;
    padding: 8px;
  }

  .history-item {
    padding: 10px;
    border-radius: 6px;
    cursor: pointer;
    margin-bottom: 6px;

    &:hover {
      background: var(--el-fill-color-light);
    }

    &.active {
      background: var(--el-color-primary-light-9);
      border: 1px solid var(--el-color-primary-light-5);
    }

    .history-q {
      font-size: 13px;
      margin-bottom: 6px;
      color: var(--el-text-color-primary);
    }

    .history-meta {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .history-time {
        font-size: 11px;
        color: var(--el-text-color-secondary);
      }
    }
  }
}
</style>
