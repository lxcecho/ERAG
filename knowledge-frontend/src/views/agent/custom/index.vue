<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="自定义 Agent">
    <template #extra>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建 Agent</el-button>
      <el-button :icon="Refresh" style="margin-left: 8px" @click="loadData">刷新</el-button>
    </template>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column label="Agent" min-width="200">
        <template #default="{ row }">
          <div class="agent-cell">
            <el-avatar :size="32" :src="(row as AgentDefinitionVo).avatar || undefined">
              <el-icon><Cpu /></el-icon>
            </el-avatar>
            <div class="agent-meta">
              <div class="agent-name">{{ (row as AgentDefinitionVo).name }}</div>
              <div class="agent-desc">{{ (row as AgentDefinitionVo).description || '—' }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="执行模型" width="120" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="(row as AgentDefinitionVo).execMode === 'multi' ? 'warning' : 'primary'" effect="plain">
            {{ (row as AgentDefinitionVo).execMode === 'multi' ? '多步流程' : '单步流式' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType((row as AgentDefinitionVo).status)">
            {{ statusLabel((row as AgentDefinitionVo).status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="enterChat(row as AgentDefinitionVo)">进入对话</el-button>
          <el-button link type="primary" @click="openEdit(row as AgentDefinitionVo)">编辑</el-button>
          <el-button
            v-if="(row as AgentDefinitionVo).status !== 'PUBLISHED'"
            link
            type="success"
            @click="onPublish(row as AgentDefinitionVo)"
          >
            发布
          </el-button>
          <el-button link type="danger" @click="onDelete(row as AgentDefinitionVo)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      v-model:current-page="pageNo"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @change="loadData"
    />

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑 Agent' : '新建 Agent'"
      width="680px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="64" show-word-limit placeholder="如：日志根因分析助手" />
        </el-form-item>
        <el-form-item label="功能描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="512" show-word-limit />
        </el-form-item>
        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="4"
            placeholder="定义该 Agent 的分析规则。支持 {context}（参考资料/日志/内容）与 {question}（用户问题）占位符"
          />
          <div class="form-tip">提示：单步/多步均会把检索到的资料注入 {context}，模型需基于该上下文回答。</div>
        </el-form-item>
        <el-form-item label="执行模型" prop="execMode">
          <el-radio-group v-model="form.execMode">
            <el-radio value="single">单步流式（推荐）</el-radio>
            <el-radio value="multi">多步流程</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.execMode === 'multi'" label="流程步骤">
          <div class="steps-editor">
            <div v-for="(step, idx) in form.stepsList" :key="idx" class="step-item">
              <div class="step-head">
                <span class="step-idx">第 {{ idx + 1 }} 步</span>
                <el-button link type="danger" @click="removeStep(idx)">移除</el-button>
              </div>
              <el-input v-model="step.stepName" placeholder="步骤名称（如：日志摘要）" style="margin-bottom: 8px" />
              <el-input
                v-model="step.prompt"
                type="textarea"
                :rows="3"
                placeholder="该步提示词，支持 {context}/{question}，inputFrom=prev 时可引用上一步 outputKey，如 {summary}"
              />
              <div class="step-row">
                <el-select v-model="step.inputFrom" style="width: 160px">
                  <el-option value="context" label="输入：外部上下文" />
                  <el-option value="prev" label="输入：上一步输出" />
                </el-select>
                <el-input v-model="step.outputKey" placeholder="产物key（下一步引用）" style="width: 200px" />
              </div>
            </div>
            <el-button type="primary" plain :icon="Plus" @click="addStep">添加步骤</el-button>
            <div class="form-tip">步骤按顺序执行，每步产物写入 outputKey，末步输出即最终结果（最多 5 步）。</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </PageWrapper>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Refresh, Cpu } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageWrapper from '@/components/PageWrapper/index.vue'
import {
  pageAgentDefApi,
  getAgentDefApi,
  createAgentDefApi,
  updateAgentDefApi,
  publishAgentDefApi,
  deleteAgentDefApi
} from '@/api/customAgent'

const router = useRouter()
const loading = ref(false)
const tableData = ref<AgentDefinitionVo[]>([])
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: undefined as number | string | undefined,
  name: '',
  description: '',
  systemPrompt: '',
  execMode: 'single' as AgentExecMode,
  stepsList: [] as StepDefinition[]
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入 Agent 名称', trigger: 'blur' }],
  systemPrompt: [{ required: true, message: '请输入系统提示词', trigger: 'blur' }],
  execMode: [{ required: true, message: '请选择执行模型', trigger: 'change' }]
}

onMounted(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await pageAgentDefApi({ current: pageNo.value, size: pageSize.value })
    tableData.value = res.data.records
    // total 转 Number：后端 Long 序列化可能为字符串，el-pagination 要求 Number
    total.value = Number(res.data.total)
  } finally {
    loading.value = false
  }
}

/* ---------- 新建/编辑 ---------- */

function openCreate() {
  form.id = undefined
  form.name = ''
  form.description = ''
  form.systemPrompt = ''
  form.execMode = 'single'
  form.stepsList = []
  dialogVisible.value = true
}

async function openEdit(row: AgentDefinitionVo) {
  // 详情接口返回完整定义（列表不含 systemPrompt/steps）
  const res = await getAgentDefApi(String(row.id))
  const def = res.data
  form.id = def.id
  form.name = def.name
  form.description = def.description
  form.systemPrompt = def.systemPrompt
  form.execMode = def.execMode
  form.stepsList = def.steps ? (JSON.parse(def.steps) as StepDefinition[]) : []
  dialogVisible.value = true
}

function addStep() {
  if (form.stepsList.length >= 5) return ElMessage.warning('最多 5 步')
  form.stepsList.push({ stepName: '', prompt: '', inputFrom: 'context', outputKey: '' })
}

function removeStep(idx: number) {
  form.stepsList.splice(idx, 1)
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.execMode === 'multi' && form.stepsList.length === 0) return ElMessage.warning('多步流程至少配置 1 个步骤')

  const payload: AgentDefinitionRequest = {
    name: form.name.trim(),
    description: form.description,
    systemPrompt: form.systemPrompt,
    execMode: form.execMode,
    steps: form.execMode === 'multi' ? JSON.stringify(form.stepsList) : undefined
  }
  submitting.value = true
  try {
    if (form.id) {
      await updateAgentDefApi(String(form.id), payload)
      ElMessage.success('已保存，发布状态已回落为草稿')
    } else {
      const res = await createAgentDefApi(payload)
      ElMessage.success(`创建成功，ID: ${res.data}`)
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

/* ---------- 发布/删除/进入对话 ---------- */

async function onPublish(row: AgentDefinitionVo) {
  await ElMessageBox.confirm(`发布后租户内可见，确认发布「${row.name}」？`, '提示', { type: 'info' })
  await publishAgentDefApi(String(row.id))
  ElMessage.success('发布成功')
  loadData()
}

async function onDelete(row: AgentDefinitionVo) {
  await ElMessageBox.confirm(`删除后不可恢复，确认删除「${row.name}」？`, '提示', { type: 'warning' })
  await deleteAgentDefApi(String(row.id))
  ElMessage.success('已删除')
  loadData()
}

function enterChat(row: AgentDefinitionVo) {
  router.push({ path: `/agent/custom/${row.id}` })
}

/* ---------- 展示辅助 ---------- */

function statusLabel(status: string) {
  return status === 'PUBLISHED' ? '已发布' : status === 'ARCHIVED' ? '已归档' : '草稿'
}

function statusTagType(status: string): 'success' | 'info' | 'warning' {
  return status === 'PUBLISHED' ? 'success' : status === 'ARCHIVED' ? 'info' : 'warning'
}
</script>

<style scoped lang="scss">
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.agent-cell {
  display: flex;
  align-items: center;
  gap: 10px;

  .agent-name {
    font-weight: 600;
  }

  .agent-desc {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    max-width: 320px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  margin-top: 4px;
}

.steps-editor {
  width: 100%;

  .step-item {
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    padding: 10px 12px;
    margin-bottom: 10px;

    .step-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;

      .step-idx {
        font-size: 13px;
        font-weight: 600;
        color: var(--el-color-primary);
      }
    }

    .step-row {
      display: flex;
      gap: 8px;
      margin-top: 8px;
    }
  }
}
</style>
