<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <div class="chat-page">
    <!-- 左侧：知识库选择 + 会话列表 -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <el-select
          v-model="currentKbId"
          :placeholder="useRag ? '请选择知识库（RAG必选）' : '选择知识库（可选）'"
          filterable
          clearable
          @change="onKbChange"
        >
          <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <div class="mode-switch">
          <el-switch
            v-model="useRag"
            active-text="RAG检索"
            inactive-text="普通对话"
            inline-prompt
            @change="onModeChange"
          />
          <span class="mode-tip">{{ useRag ? '基于知识库回答' : '直接与大模型对话' }}</span>
        </div>
        <el-button type="primary" :icon="Plus" plain @click="newSession" :disabled="useRag && !currentKbId">
          新对话
        </el-button>
      </div>
      <div class="session-list">
        <div
          v-for="s in sessionList"
          :key="s.id"
          class="session-item"
          :class="{ active: s.id === currentSessionId }"
          @click="selectSession(s.id)"
        >
          <el-icon class="session-icon"><ChatDotRound /></el-icon>
          <span class="session-title">{{ s.title }}</span>
          <el-tag v-if="s.kbId > 0 && s.kbName" size="small" type="primary" effect="plain" class="session-kb">
            {{ s.kbName }}
          </el-tag>
          <el-icon class="session-edit" @click.stop="renameSession(s)"><Edit /></el-icon>
          <el-icon class="session-del" @click.stop="deleteSession(s.id)"><Delete /></el-icon>
        </div>
        <el-empty v-if="!sessionList.length" description="暂无会话" :image-size="60" />
      </div>
    </div>

    <!-- 右侧：消息区 + 输入区 -->
    <div class="chat-main">
      <div class="message-area" ref="msgAreaRef">
        <div v-if="!messageList.length" class="empty-tip">
          <el-icon :size="48"><ChatLineSquare /></el-icon>
          <p>{{ useRag ? '选择知识库后开始提问，基于知识库回答' : '直接输入问题，与大模型多轮对话' }}</p>
          <p class="empty-sub">支持流式输出与历史上下文</p>
        </div>
        <div
          v-for="(msg, idx) in messageList"
          :key="idx"
          class="message-row"
          :class="msg.role"
        >
          <div class="avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
          <div class="message-content">
            <div v-if="msg.thinking && !msg.content" class="thinking">
              <span class="thinking-dots"><i></i><i></i><i></i></span>
              <span class="thinking-text">{{ msg.thinkingText || '正在思考…' }}</span>
            </div>
            <div v-else class="message-text markdown-body" v-html="renderWithCitations(msg)" @click="handleCitationClick($event, msg)"></div>
            <div v-if="msg.sources && msg.sources.length" class="sources">
              <div class="sources-title">
                <el-icon><Link /></el-icon>
                <span>引用来源（{{ msg.sources.length }}）</span>
              </div>
              <el-collapse v-model="collapseActiveNames[idx]">
                <el-collapse-item v-for="(src, i) in msg.sources" :key="i" :name="i">
                  <template #title>
                    <div class="source-title" :class="{ 'source-highlighted': highlightedSrc?.msgIdx === idx && highlightedSrc?.srcIdx === i }">
                      <span class="source-idx">[{{ i + 1 }}]</span>
                      <span class="source-name">{{ src.source || '未知来源' }}</span>
                      <el-tag v-if="src.chunkIndex != null" size="small" type="info" class="source-chunk">
                        切片 #{{ src.chunkIndex }}
                      </el-tag>
                      <el-tag size="small" class="source-score">
                        相似度 {{ (src.score * 100).toFixed(1) }}%
                      </el-tag>
                    </div>
                  </template>
                  <div class="source-body">
                    <div class="source-text">{{ src.text }}</div>
                    <el-button
                      size="small"
                      type="primary"
                      text
                      :icon="View"
                      @click.stop="openSourceDoc(src)"
                    >
                      查看原文档
                    </el-button>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </div>
      </div>

      <div class="input-area">
        <div class="input-box">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            resize="none"
            :placeholder="useRag && !currentKbId ? '请先选择知识库' : '输入问题，Enter 发送，Shift+Enter 换行'"
            @keydown.enter.exact.prevent="send"
            :disabled="streaming"
          />
          <el-button type="primary" :loading="streaming" @click="send" :disabled="useRag && !currentKbId">
            {{ streaming ? '生成中' : '发送' }}
          </el-button>
          <el-button v-if="streaming" @click="stopStream">停止</el-button>
        </div>
      </div>
    </div>

    <!-- 查看原文档弹窗：展示文档元数据与命中的切片原文，可追溯引用出处 -->
    <el-dialog v-model="docDialog.visible" title="原文档详情" width="720px" top="6vh">
      <template v-if="docDialog.doc">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="文档名称" :span="2">
            {{ docDialog.doc.originalName }}
          </el-descriptions-item>
          <el-descriptions-item label="所属知识库">{{ docDialog.doc.kbName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文件类型">{{ docDialog.doc.fileType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文件大小">
            {{ docDialog.doc.fileSize ? (docDialog.doc.fileSize / 1024).toFixed(1) + ' KB' : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="切片总数">{{ docDialog.doc.chunkCount }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ docDialog.doc.createTime }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <div class="doc-chunk-block">
        <div class="doc-chunk-title">
          命中的切片原文
          <el-tag v-if="docDialog.chunkIndex != null" size="small" type="info">切片 #{{ docDialog.chunkIndex }}</el-tag>
        </div>
        <div class="doc-chunk-text">{{ docDialog.text }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Plus, Delete, Edit, ChatDotRound, ChatLineSquare, View, Link } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageKbApi, pageMemberApi } from '@/api/kb'
import {
  pageSessionApi, createSessionApi, removeSessionApi, renameSessionApi, listMessageApi
} from '@/api/chat'
import { getDocumentApi } from '@/api/document'
import { streamChat } from '@/utils/sse'
import { renderMarkdown } from '@/utils/markdown'

const kbList = ref<KbItem[]>([])
const currentKbId = ref<number | string>()
const sessionList = ref<ChatSessionVo[]>([])
const currentSessionId = ref<number | string>()
const messageList = ref<ChatMessageVo[]>([])
const inputText = ref('')
const streaming = ref(false)
const msgAreaRef = ref<HTMLElement>()
// 对话模式：false=普通对话（直接大模型），true=RAG对话（检索知识库）
const useRag = ref(false)
const route = useRoute()
let abortController: AbortController | null = null

/** 查看原文档弹窗状态 */
const docDialog = ref<{
  visible: boolean
  doc: KbDocument | null
  chunkIndex: number | null
  text: string
}>({ visible: false, doc: null, chunkIndex: null, text: '' })

/** 当前高亮的来源（点击引用 [n] 时设置，驱动高亮动画） */
const highlightedSrc = ref<{ msgIdx: number; srcIdx: number } | null>(null)

/** 各消息 el-collapse 的展开状态（按消息索引存储 activeNames 数组） */
const collapseActiveNames = ref<Record<number, (string | number)[]>>({})

/**
 * 渲染 Markdown 并将 [n] 引用标记转为可点击的 <sup> 元素。
 * 点击后展开对应来源卡片并高亮，实现引用交互。
 */
function renderWithCitations(msg: ChatMessageVo): string {
  if (!msg.content) return ''
  let html = renderMarkdown(msg.content)
  const sources = msg.sources
  if (!sources || !sources.length) return html
  // 将 [n] 替换为可点击的引用标记（仅替换 n 在有效范围内的）
  html = html.replace(/\[(\d+)\]/g, (match, num: string) => {
    const idx = parseInt(num, 10) - 1
    if (idx >= 0 && idx < sources.length) {
      return `<sup class="citation-mark" data-src-idx="${idx}">[${num}]</sup>`
    }
    return match
  })
  return html
}

/** 引用标记点击处理（事件委托）：展开对应来源卡片并高亮 */
function handleCitationClick(e: MouseEvent, msg: ChatMessageVo) {
  const target = e.target as HTMLElement
  const citationEl = target.closest('.citation-mark') as HTMLElement | null
  if (!citationEl) return
  const srcIdx = parseInt(citationEl.getAttribute('data-src-idx') || '-1', 10)
  if (srcIdx < 0 || !msg.sources || srcIdx >= msg.sources.length) return

  // 找到当前消息在 messageList 中的索引
  const msgIdx = messageList.value.indexOf(msg)
  if (msgIdx < 0) return

  // 编程式展开对应的 el-collapse-item
  collapseActiveNames.value[msgIdx] = [srcIdx]

  // 设置高亮并自动清除
  highlightedSrc.value = { msgIdx, srcIdx }
  setTimeout(() => {
    highlightedSrc.value = null
  }, 3000)

  // 滚动到来源区域
  nextTick(() => {
    const sourceArea = citationEl.closest('.message-content')?.querySelector('.sources')
    sourceArea?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  })
}

/** 追溯引用出处：加载文档详情并弹出，定位到命中的切片原文 */
async function openSourceDoc(src: RetrievalResult) {
  docDialog.value = { visible: true, doc: null, chunkIndex: src.chunkIndex, text: src.text }
  if (!src.documentId) return
  try {
    const res = await getDocumentApi(src.documentId)
    docDialog.value.doc = res.data
  } catch {
    // 文档已删除或无权限：仅展示切片原文（切片文本随 sources 一起返回，不影响追溯）
    docDialog.value.doc = null
  }
}

onMounted(async () => {
  // 从知识库列表"问答"入口进入：自动切换到 RAG 模式并关联该知识库，无需手动选择
  const qkbId = route.query.kbId
  if (qkbId) {
    useRag.value = true
    currentKbId.value = qkbId as string
  }
  await loadKbList()
  // 默认普通对话模式：加载当前用户全部会话（不限定知识库）
  await loadSessions()
})

async function loadKbList() {
  const res = await pageKbApi({ pageNo: 1, pageSize: 100 })
  kbList.value = res.data.records
  // RAG 模式下自动选中第一个知识库；普通模式不强制选
  if (useRag.value && kbList.value.length && !currentKbId.value) {
    currentKbId.value = kbList.value[0].id
  }
}

async function onKbChange() {
  sessionList.value = []
  messageList.value = []
  currentSessionId.value = undefined
  await loadSessions()
}

/** 切换对话模式：清空当前会话与消息，RAG 模式自动选知识库 */
async function onModeChange() {
  sessionList.value = []
  messageList.value = []
  currentSessionId.value = undefined
  // RAG 模式：未选知识库时自动选第一个
  if (useRag.value && !currentKbId.value && kbList.value.length) {
    currentKbId.value = kbList.value[0].id
  }
  await loadSessions()
}

async function loadSessions() {
  // 普通对话且未选知识库：查全部会话（kbId 不传）
  // RAG 模式或已选知识库：按 kbId 查
  const params: { pageNo: number; pageSize: number; kbId?: number } = { pageNo: 1, pageSize: 50 }
  if (currentKbId.value) {
    params.kbId = currentKbId.value
  }
  const res = await pageSessionApi(params)
  sessionList.value = res.data.records
}

async function newSession() {
  // RAG 模式必须选知识库
  if (useRag.value && !currentKbId.value) return
  // 普通对话未选知识库：不预先创建会话，首次发送时后端 ensureSession 自动创建
  if (!currentKbId.value) {
    currentSessionId.value = undefined
    messageList.value = []
    return
  }
  const res = await createSessionApi({ kbId: currentKbId.value })
  currentSessionId.value = res.data
  messageList.value = []
  await loadSessions()
}

async function selectSession(id: number | string) {
  currentSessionId.value = id
  // 进入会话时同步知识库：RAG 会话自动选中对应知识库，普通对话切回普通模式（有知识库才显示）
  const target = sessionList.value.find((s) => s.id === id)
  if (target) {
    if (target.kbId > 0) {
      useRag.value = true
      currentKbId.value = target.kbId
    } else {
      useRag.value = false
      currentKbId.value = undefined
    }
  }
  const res = await listMessageApi(id)
  messageList.value = res.data
  await scrollToBottom()
}

async function deleteSession(id: number | string) {
  await ElMessageBox.confirm('确定删除该会话及其消息？', '提示', { type: 'warning' })
  await removeSessionApi(id)
  if (currentSessionId.value === id) {
    currentSessionId.value = undefined
    messageList.value = []
  }
  await loadSessions()
  ElMessage.success('已删除')
}

/** 重命名会话：弹窗输入新标题，成功后同步本地列表 */
async function renameSession(s: ChatSessionVo) {
  const { value } = await ElMessageBox.prompt('请输入新的会话标题', '重命名会话', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: s.title,
    inputPattern: /\S/,
    inputErrorMessage: '标题不能为空'
  })
  if (!value || value === s.title) return
  await renameSessionApi(s.id, value)
  s.title = value
  ElMessage.success('已重命名')
}

async function send() {
  const question = inputText.value.trim()
  if (!question || streaming.value) return
  // RAG 模式必须选知识库；普通模式可不选
  if (useRag.value && !currentKbId.value) return

  // 追加用户消息
  messageList.value.push({ id: 0, sessionId: currentSessionId.value || 0, role: 'user', content: question, sources: [], createTime: '' })
  inputText.value = ''
  streaming.value = true

  // 预占位助手消息，流式填充
  // 关键：用 reactive() 显式创建 proxy 再 push（数组内保存同一 proxy），
  //       回调里修改 content/sources 必然触发响应式，v-html 随 token 实时刷新；
  //       若持有 push 前的普通对象直接修改，不会触发 Vue 渲染（切页重挂载才显示）。
  const assistantMsg = reactive<ChatMessageVo>({
    id: 0,
    sessionId: currentSessionId.value || 0,
    role: 'assistant',
    content: '',
    sources: [],
    createTime: '',
    // 首个 token 到达前展示"思考模式"占位，onStatus 更新阶段文案
    thinking: true,
    thinkingText: '正在思考…'
  })
  messageList.value.push(assistantMsg)
  await scrollToBottom()

  abortController = await streamChat(
    { question, kbId: currentKbId.value, sessionId: currentSessionId.value, useRag: useRag.value },
    {
      onStatus: (stage) => {
        // 检索中 → 生成中，首 token 前用阶段文案反馈进度
        assistantMsg.thinkingText =
          stage === 'retrieving' ? '正在检索知识库…' : stage === 'generating' ? '正在生成回答…' : assistantMsg.thinkingText
      },
      onSources: (sources) => {
        assistantMsg.sources = sources
      },
      onToken: (token) => {
        // 首个 token 到达，退出思考占位，切到流式内容渲染
        assistantMsg.thinking = false
        assistantMsg.content += token
        scrollToBottom()
      },
      onDone: (sid) => {
        assistantMsg.thinking = false
        // 清洗引号：旧后端 data(Long) 会被 SSE 序列化为带引号字符串，回传将导致后端 400
        currentSessionId.value = String(sid).replace(/^["']|["']$/g, '') || ''
        streaming.value = false
        loadSessions()
      },
      onCorrect: (answer) => {
        // 后端修复了缺失的引用标注：用修正后的完整回答整体替换
        assistantMsg.content = answer
        scrollToBottom()
      },
      onError: (err) => {
        assistantMsg.thinking = false
        assistantMsg.content = assistantMsg.content || `生成失败：${err}`
        streaming.value = false
        ElMessage.error(err)
      }
    }
  )
}

function stopStream() {
  abortController?.abort()
  streaming.value = false
}

async function scrollToBottom() {
  await nextTick()
  if (msgAreaRef.value) {
    msgAreaRef.value.scrollTop = msgAreaRef.value.scrollHeight
  }
}
</script>

<style scoped lang="scss">
.chat-page {
  display: flex;
  height: calc(100vh - 110px);
  background: #f5f7fa;
}

.chat-sidebar {
  width: 260px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;

  .sidebar-header {
    padding: 12px;
    border-bottom: 1px solid #e4e7ed;
    .el-select {
      width: 100%;
      margin-bottom: 8px;
    }
    .mode-switch {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;
      padding: 6px 8px;
      background: #f5f7fa;
      border-radius: 4px;
      .mode-tip {
        font-size: 12px;
        color: #909399;
      }
    }
    .el-button {
      width: 100%;
    }
  }

  .session-list {
    flex: 1;
    overflow-y: auto;
    padding: 8px;
  }

  .session-item {
    display: flex;
    align-items: center;
    padding: 8px 10px;
    border-radius: 6px;
    cursor: pointer;
    margin-bottom: 4px;
    &:hover {
      background: #f5f7fa;
      .session-del,
      .session-edit {
        opacity: 1;
      }
    }
    &.active {
      background: #ecf5ff;
      color: #409eff;
    }
    .session-icon {
      margin-right: 6px;
      flex-shrink: 0;
    }
    .session-title {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 14px;
    }
    .session-kb {
      margin-left: 4px;
      margin-right: 6px;
      flex-shrink: 0;
      max-width: 96px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .session-del {
      opacity: 0;
      flex-shrink: 0;
      color: #f56c6c;
    }
    .session-edit {
      opacity: 0;
      flex-shrink: 0;
      margin-right: 6px;
      color: #909399;
      &:hover {
        color: #409eff;
      }
    }
  }
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;

  .message-area {
    flex: 1;
    overflow-y: auto;
    padding: 32px 20px 48px;

    .empty-tip {
      text-align: center;
      color: #909399;
      margin-top: 150px;
      .el-icon {
        color: #d0d5dd;
      }
      p {
        margin-top: 16px;
        font-size: 15px;
        color: #6b7280;
      }
      .empty-sub {
        margin-top: 8px;
        font-size: 13px;
        color: #c0c4cc;
      }
    }
  }

  .message-row {
    display: flex;
    margin: 0 auto 32px;
    max-width: 820px;
    width: 100%;

    &.user {
      flex-direction: row-reverse;
      .avatar {
        background: #4d6bfe;
        margin-left: 12px;
      }
      .message-content {
        align-items: flex-end;
        .message-text {
          background: #f5f6f8;
          border-radius: 14px;
          padding: 10px 16px;
        }
      }
    }
    &.assistant {
      .avatar {
        background: linear-gradient(135deg, #4d6bfe 0%, #7c5cff 100%);
        margin-right: 12px;
      }
      .message-content {
        align-items: flex-start;
        .message-text {
          background: transparent;
          box-shadow: none;
          padding: 0;
          padding-top: 6px;
        }
      }
    }

    .avatar {
      width: 34px;
      height: 34px;
      border-radius: 50%;
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
      flex-shrink: 0;
    }

    .message-content {
      display: flex;
      flex-direction: column;
      flex: 1;
      min-width: 0;

      .message-text {
        font-size: 15px;
        line-height: 1.75;
        word-break: break-word;
        color: #1f2329;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
          'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
        max-width: 100%;

        /* markdown 渲染样式（DeepSeek 风格） */
        :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
          margin: 16px 0 8px;
          font-weight: 600;
          line-height: 1.4;
          color: #141414;
        }
        :deep(h1) { font-size: 1.5em; }
        :deep(h2) { font-size: 1.3em; }
        :deep(h3) { font-size: 1.15em; }
        :deep(h4) { font-size: 1.05em; }
        :deep(p) { margin: 10px 0; }
        :deep(ul), :deep(ol) { margin: 10px 0; padding-left: 24px; }
        :deep(li) { margin: 5px 0; line-height: 1.7; }
        :deep(li > ul), :deep(li > ol) { margin: 4px 0; }
        :deep(code) {
          background: #f0f1f3;
          color: #c7254e;
          padding: 2px 6px;
          border-radius: 4px;
          font-size: 0.88em;
          font-family: 'SF Mono', 'Consolas', 'Monaco', monospace;
        }
        :deep(pre) {
          background: #f6f7f9;
          border: 1px solid #e8eaed;
          color: #1f2329;
          padding: 14px 16px;
          border-radius: 10px;
          overflow-x: auto;
          margin: 12px 0;
          code {
            background: none;
            padding: 0;
            color: inherit;
            font-size: 13px;
          }
        }
        :deep(blockquote) {
          margin: 12px 0;
          padding: 6px 14px;
          border-left: 3px solid #d0d7de;
          background: #fafbfc;
          color: #57606a;
          border-radius: 0 6px 6px 0;
        }
        :deep(table) {
          border-collapse: collapse;
          margin: 12px 0;
          width: 100%;
          th, td {
            border: 1px solid #e5e7eb;
            padding: 8px 12px;
            text-align: left;
          }
          th { background: #f8f9fa; font-weight: 600; }
        }
        :deep(a) { color: #4d6bfe; text-decoration: none; &:hover { text-decoration: underline; } }
        :deep(strong) { font-weight: 600; color: #141414; }
        :deep(hr) { border: none; border-top: 1px solid #e5e7eb; margin: 16px 0; }

        /* 引用标记 [n]：可点击的上标，悬停高亮（hover 仅变色，不变尺寸，避免鼠标反复进出抖动） */
        :deep(.citation-mark) {
          display: inline-block;
          font-size: 0.75em;
          font-weight: 600;
          color: #4d6bfe;
          cursor: pointer;
          padding: 0 2px;
          vertical-align: super;
          line-height: 1;
          border-radius: 3px;
          transition: color 0.15s ease, background-color 0.15s ease;
          &:hover {
            color: #fff;
            background: #4d6bfe;
          }
        }
      }

      .thinking {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 8px 0 10px;

        .thinking-dots {
          display: flex;
          gap: 5px;
          i {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: #4d6bfe;
            animation: thinking-bounce 1.2s infinite ease-in-out;
            &:nth-child(2) { animation-delay: 0.15s; }
            &:nth-child(3) { animation-delay: 0.3s; }
          }
        }
        .thinking-text {
          font-size: 13px;
          color: #8a919f;
        }
      }

      .sources {
        margin-top: 10px;
        padding: 10px 12px;
        background: #f8f9fb;
        border: 1px solid #eef0f3;
        border-radius: 10px;
        width: 100%;

        .sources-title {
          display: flex;
          align-items: center;
          gap: 4px;
          font-size: 12px;
          color: #8a919f;
          margin-bottom: 6px;
        }
        .source-title {
          display: flex;
          align-items: center;
          gap: 6px;
          min-width: 0;
          .source-idx {
            font-weight: 600;
            color: #4d6bfe;
            flex-shrink: 0;
          }
          .source-name {
            flex: 1;
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }
          .source-chunk,
          .source-score {
            flex-shrink: 0;
          }
        }
        .source-body {
          display: flex;
          align-items: flex-start;
          gap: 8px;
        }
        .source-text {
          flex: 1;
          font-size: 13px;
          color: #5f6672;
          line-height: 1.6;
          white-space: pre-wrap;
        }
        /* 引用跳转高亮：点击 [n] 后对应来源卡片闪烁高亮 */
        .source-highlighted {
          animation: source-highlight-pulse 1.5s ease-out;
          border-radius: 4px;
        }
        @keyframes source-highlight-pulse {
          0% { background: #ecf5ff; box-shadow: 0 0 0 2px #4d6bfe; }
          50% { background: #ecf5ff; box-shadow: 0 0 0 2px #4d6bfe; }
          100% { background: transparent; box-shadow: 0 0 0 0 transparent; }
        }
      }
    }
  }

  .input-area {
    padding: 6px 20px 16px;
    background: #fff;
    border-top: 1px solid #f0f0f0;

    .input-box {
      max-width: 820px;
      width: 100%;
      margin: 0 auto;
      display: flex;
      gap: 10px;
      align-items: flex-end;
      .el-input {
        flex: 1;
      }
    }
  }
}

@keyframes thinking-bounce {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.35;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

// 原文档详情弹窗内容（el-dialog teleport 到 body，须置于顶层选择器）
.doc-chunk-block {
  margin-top: 12px;
  .doc-chunk-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 8px;
  }
  .doc-chunk-text {
    max-height: 300px;
    overflow-y: auto;
    padding: 12px;
    background: #f5f7fa;
    border-radius: 6px;
    font-size: 13px;
    color: #606266;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
  }
}
</style>
