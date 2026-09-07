<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="Workflow 流程配置">
    <template #extra>
      <el-tag type="info" effect="plain">系统预置 + 租户自定义流程</el-tag>
      <el-button :icon="Refresh" style="margin-left: 8px" @click="loadData">刷新</el-button>
    </template>

    <el-table :data="tableData" v-loading="loading" border stripe row-key="id">
      <el-table-column prop="code" label="流程编码" width="180" />
      <el-table-column prop="name" label="流程名称" min-width="160" />
      <el-table-column prop="version" label="版本" width="80" align="center">
        <template #default="{ row }">v{{ row.version }}</template>
      </el-table-column>
      <el-table-column label="来源" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.tenantId === 0 ? 'info' : 'success'" size="small">
            {{ row.tenantId === 0 ? '系统预置' : '租户自定义' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'danger'" size="small">
            {{ row.status === 'ENABLED' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openNodes(row as WorkflowDefinition)">节点结构</el-button>
          <el-button
            link
            type="success"
            :disabled="row.status !== 'ENABLED'"
            @click="openStart(row as WorkflowDefinition)"
          >
            启动流程
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 启动流程弹窗 -->
    <el-dialog v-model="startVisible" title="启动流程" width="560px">
      <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="流程">{{ currentDef?.name }}（{{ currentDef?.code }}）</el-descriptions-item>
      </el-descriptions>
      <el-form :model="form" label-width="90px">
        <el-form-item label="任务目标" required>
          <el-input
            v-model="form.goal"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="描述本次流程要达成的目标"
          />
        </el-form-item>
        <el-form-item label="知识库">
          <el-select v-model="form.kbId" placeholder="检索类流程必填" filterable clearable style="width: 100%">
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务键">
          <el-input v-model="form.businessKey" placeholder="外部关联键（可选）" maxlength="64" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onStart">确认启动</el-button>
      </template>
    </el-dialog>

    <!-- 节点结构弹窗 -->
    <el-dialog v-model="nodesVisible" title="流程节点结构" width="640px">
      <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="流程">{{ currentDef?.name }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ currentDef?.description || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="parsedNodes.length" class="node-flow">
        <div v-for="(n, idx) in parsedNodes" :key="idx" class="node-item">
          <div class="node-head">
            <el-icon :color="nodeColor(n.type)"><component :is="nodeIcon(n.type)" /></el-icon>
            <span class="node-name">{{ n.name || n.nodeId }}</span>
            <el-tag size="small" :type="nodeTagType(n.type)">{{ n.type }}</el-tag>
          </div>
          <div v-if="n.toolName" class="node-meta">工具：{{ n.toolName }}</div>
          <div v-if="n.prompt" class="node-meta">提示：{{ n.prompt }}</div>
          <el-icon v-if="idx < parsedNodes.length - 1" class="arrow"><ArrowDown /></el-icon>
        </div>
      </div>
      <el-empty v-else description="未解析到节点（definition 格式非预期）" />
    </el-dialog>
  </PageWrapper>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  Refresh, ArrowDown, Flag, Tools, MagicStick, User, CircleCheck
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageWrapper from '@/components/PageWrapper/index.vue'
import { pageKbApi } from '@/api/kb'
import { listWorkflowDefinitionsApi, startWorkflowApi } from '@/api/workflow'

interface ParsedNode {
  nodeId: string
  name: string
  type: string
  toolName?: string
  prompt?: string
}

const router = useRouter()
const loading = ref(false)
const tableData = ref<WorkflowDefinition[]>([])
const kbList = ref<KbItem[]>([])

const startVisible = ref(false)
const submitting = ref(false)
const currentDef = ref<WorkflowDefinition>()
const form = reactive({
  goal: '',
  kbId: undefined as number | undefined,
  businessKey: ''
})

const nodesVisible = ref(false)
const parsedNodes = ref<ParsedNode[]>([])

onMounted(async () => {
  const res = await pageKbApi({ pageNo: 1, pageSize: 100 })
  kbList.value = res.data.records
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await listWorkflowDefinitionsApi()
    tableData.value = res.data
  } finally {
    loading.value = false
  }
}

function openStart(row: WorkflowDefinition) {
  currentDef.value = row
  form.goal = ''
  form.kbId = undefined
  form.businessKey = ''
  startVisible.value = true
}

async function onStart() {
  if (!currentDef.value) return
  if (!form.goal.trim()) return ElMessage.warning('请输入任务目标')
  submitting.value = true
  try {
    const res = await startWorkflowApi({
      definitionId: currentDef.value.id,
      goal: form.goal.trim(),
      kbId: form.kbId,
      businessKey: form.businessKey || undefined
    })
    ElMessage.success(`流程已启动，任务ID: ${res.data}`)
    startVisible.value = false
    router.push({ path: '/agent/detail', query: { type: 'workflow', id: String(res.data) } })
  } finally {
    submitting.value = false
  }
}

function openNodes(row: WorkflowDefinition) {
  currentDef.value = row
  parsedNodes.value = parseNodes(row.definition)
  nodesVisible.value = true
}

/** 防御式解析流程定义 JSON，兼容多种字段命名（nodeId/id、name/label、type/nodeType） */
function parseNodes(raw: string): ParsedNode[] {
  if (!raw) return []
  try {
    const model = JSON.parse(raw)
    const list = model.nodes || model.nodeList || model.steps || []
    if (!Array.isArray(list)) return []
    return list.map((n: any) => ({
      nodeId: String(n.nodeId ?? n.id ?? ''),
      name: n.name ?? n.label ?? '',
      type: String(n.type ?? n.nodeType ?? 'UNKNOWN').toUpperCase(),
      toolName: n.toolName ?? n.tool ?? undefined,
      prompt: n.prompt ?? undefined
    }))
  } catch {
    return []
  }
}

const ICON_MAP: Record<string, any> = {
  START: Flag, TOOL: Tools, LLM: MagicStick, HUMAN: User, END: CircleCheck
}
function nodeIcon(type: string) {
  return ICON_MAP[type] || Tools
}
function nodeColor(type: string) {
  return { START: '#67c23a', TOOL: '#409eff', LLM: '#a855f7', HUMAN: '#e6a23c', END: '#909399' }[type] || '#409eff'
}
function nodeTagType(type: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  return ({ START: 'success', TOOL: 'primary', LLM: 'warning', HUMAN: 'danger', END: 'info' } as const)[type] ?? 'info'
}
</script>

<style scoped lang="scss">
.node-flow {
  max-height: 420px;
  overflow-y: auto;
}

.node-item {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  margin-bottom: 4px;
}

.node-head {
  display: flex;
  align-items: center;
  gap: 8px;

  .node-name {
    font-weight: 600;
    flex: 1;
  }
}

.node-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 6px;
  margin-left: 24px;
  word-break: break-all;
}

.arrow {
  display: block;
  margin: 4px auto;
  color: var(--el-text-color-placeholder);
}
</style>
