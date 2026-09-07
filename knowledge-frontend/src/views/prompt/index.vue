<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="Prompt 模板管理">
    <template #extra>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建模板</el-button>
    </template>

    <!-- 检索区 -->
    <el-form :inline="true" class="search-bar">
      <el-form-item label="名称">
        <el-input v-model="filter.name" placeholder="模板名称" clearable @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="编码">
        <el-input v-model="filter.promptCode" placeholder="promptCode" clearable @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="filter.type" placeholder="全部" clearable style="width: 140px">
          <el-option label="RAG" value="rag" />
          <el-option label="通用系统" value="system" />
          <el-option label="Agent" value="agent" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 主列表：每个 promptCode 最新版本 -->
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="promptCode" label="编码" min-width="160" show-overflow-tooltip />
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag>{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="最新版本" width="90" align="center">
        <template #default="{ row }">v{{ row.version }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="variables" label="变量" width="140" show-overflow-tooltip />
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as PromptTemplate)">编辑</el-button>
          <el-button link type="info" @click="openVersions(row as PromptTemplate)">版本</el-button>
          <el-button link type="warning" @click="openTest(row.content, row.variables)">测试</el-button>
          <el-button link type="danger" @click="onRemove(row as PromptTemplate)">删除</el-button>
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
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑模板' : '新建模板'" width="720px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="编码" required>
          <el-input
            v-model="form.promptCode"
            :disabled="!!form.id"
            placeholder="小写字母/数字/下划线，如 rag_system_prompt"
          />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-radio-group v-model="form.type">
            <el-radio value="rag">RAG 系统提示</el-radio>
            <el-radio value="system">通用系统</el-radio>
            <el-radio value="agent">Agent</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            placeholder="使用 {varName} 作为命名占位符，如 {context} {question}"
          />
        </el-form-item>
        <el-form-item label="变量">
          <el-input v-model="form.variables" placeholder="逗号分隔，如 context,question" />
        </el-form-item>
        <el-form-item label="版本说明">
          <el-input v-model="form.remark" maxlength="255" placeholder="本版本变更说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="warning" @click="openTest(form.content, form.variables)">测试</el-button>
        <el-button type="primary" :loading="submitting" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 版本抽屉 -->
    <el-drawer v-model="versionDrawer" :title="`版本管理 - ${currentCode}`" size="560px">
      <el-timeline v-loading="versionLoading">
        <el-timeline-item
          v-for="v in versions"
          :key="v.id"
          :timestamp="v.updateTime"
          placement="top"
          :type="statusMeta(v.status).type"
        >
          <el-card shadow="never">
            <div class="version-head">
              <span class="version-no">v{{ v.version }}</span>
              <el-tag :type="statusMeta(v.status).type" size="small">{{ statusMeta(v.status).label }}</el-tag>
              <span v-if="v.tenantId === 0" class="platform-tag">平台预置</span>
            </div>
            <div class="version-remark">{{ v.remark || '无说明' }}</div>
            <el-input
              :model-value="v.content"
              type="textarea"
              :rows="4"
              readonly
              resize="none"
              class="version-content"
            />
            <div class="version-actions">
              <el-button
                link
                type="success"
                :disabled="v.status === 'PUBLISHED'"
                @click="onPublish(v as PromptTemplate)"
              >发布</el-button>
              <el-button link type="warning" @click="onRollback(v as PromptTemplate)">回滚</el-button>
              <el-button link type="primary" @click="openTest(v.content, v.variables)">测试</el-button>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>

    <!-- 测试弹窗 -->
    <el-dialog v-model="testVisible" title="模板测试" width="760px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item v-if="testVars.length" label="变量填充">
          <div class="var-list">
            <div v-for="item in testVars" :key="item.key" class="var-row">
              <el-tag size="small" type="info">{{ item.key }}</el-tag>
              <el-input v-model="item.value" :placeholder="`输入 ${item.key} 的测试值`" />
            </div>
          </div>
        </el-form-item>
        <el-form-item label="待测内容">
          <el-input v-model="testContent" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item v-if="testResult" label="渲染结果">
          <el-input :model-value="testResult.renderedPrompt" type="textarea" :rows="5" readonly />
        </el-form-item>
        <el-form-item v-if="testResult" label="LLM 输出">
          <div class="llm-output">
            <MarkdownView :content="testResult.output" />
          </div>
        </el-form-item>
        <el-form-item v-if="testResult" label="Token">
          <el-tag type="success">{{ testResult.tokens }}</el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testVisible = false">关闭</el-button>
        <el-button type="primary" :loading="testLoading" @click="onTest">调用测试</el-button>
      </template>
    </el-dialog>
  </PageWrapper>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageWrapper from '@/components/PageWrapper/index.vue'
import MarkdownView from '@/components/MarkdownView/index.vue'
import {
  pagePromptApi,
  createPromptApi,
  editPromptApi,
  publishPromptApi,
  rollbackPromptApi,
  removePromptApi,
  listPromptVersionsApi,
  testPromptApi
} from '@/api/prompt'

// ============ 主列表 ============
const loading = ref(false)
const tableData = ref<PromptTemplate[]>([])
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)
const filter = reactive({ name: '', promptCode: '', type: '' })

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await pagePromptApi({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      name: filter.name || undefined,
      promptCode: filter.promptCode || undefined,
      type: filter.type || undefined
    })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNo.value = 1
  loadData()
}

function onReset() {
  filter.name = ''
  filter.promptCode = ''
  filter.type = ''
  pageNo.value = 1
  loadData()
}

// ============ 新建/编辑 ============
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({
  id: undefined as number | undefined,
  promptCode: '',
  name: '',
  type: 'rag',
  content: '',
  variables: '',
  remark: ''
})

function openCreate() {
  form.id = undefined
  form.promptCode = ''
  form.name = ''
  form.type = 'rag'
  form.content = ''
  form.variables = ''
  form.remark = ''
  dialogVisible.value = true
}

function openEdit(row: PromptTemplate) {
  form.id = row.id
  form.promptCode = row.promptCode
  form.name = row.name
  form.type = row.type
  form.content = row.content
  form.variables = row.variables
  form.remark = row.remark
  dialogVisible.value = true
}

async function onSave() {
  if (!form.promptCode.trim()) return ElMessage.warning('编码不能为空')
  if (!form.name.trim()) return ElMessage.warning('名称不能为空')
  if (!form.content.trim()) return ElMessage.warning('内容不能为空')
  submitting.value = true
  try {
    if (form.id) {
      await editPromptApi({ ...form })
      ElMessage.success('保存成功（DRAFT原地更新，已发布/归档版将派生新草稿）')
    } else {
      await createPromptApi({ ...form })
      ElMessage.success('新建成功（v1 草稿）')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function onRemove(row: PromptTemplate) {
  await ElMessageBox.confirm(`确定删除「${row.name}」v${row.version}？`, '提示', { type: 'warning' })
  await removePromptApi(row.id)
  ElMessage.success('已删除')
  loadData()
}

// ============ 版本管理 ============
const versionDrawer = ref(false)
const versionLoading = ref(false)
const versions = ref<PromptTemplate[]>([])
const currentCode = ref('')

async function openVersions(row: PromptTemplate) {
  currentCode.value = row.promptCode
  versionDrawer.value = true
  await loadVersions()
}

async function loadVersions() {
  versionLoading.value = true
  try {
    const res = await listPromptVersionsApi(currentCode.value)
    versions.value = res.data
  } finally {
    versionLoading.value = false
  }
}

async function onPublish(v: PromptTemplate) {
  await ElMessageBox.confirm(`确定发布 v${v.version}？同编码旧发布版将归档。`, '提示', { type: 'warning' })
  await publishPromptApi(v.id)
  ElMessage.success('已发布')
  loadVersions()
  loadData()
}

async function onRollback(v: PromptTemplate) {
  await ElMessageBox.confirm(`确定回滚至 v${v.version}？将派生新发布版本。`, '提示', { type: 'warning' })
  await rollbackPromptApi(v.id)
  ElMessage.success('已回滚')
  loadVersions()
  loadData()
}

// ============ 测试 ============
const testVisible = ref(false)
const testLoading = ref(false)
const testContent = ref('')
const testVars = ref<{ key: string; value: string }[]>([])
const testResult = ref<PromptTestResult>()

function openTest(content: string, variablesStr: string) {
  testContent.value = content || ''
  testVars.value = parseVars(variablesStr)
  testResult.value = undefined
  testVisible.value = true
}

function parseVars(variablesStr: string): { key: string; value: string }[] {
  if (!variablesStr) return []
  return variablesStr
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .map((k) => ({ key: k, value: '' }))
}

async function onTest() {
  if (!testContent.value.trim()) return ElMessage.warning('待测内容不能为空')
  const variables: Record<string, string> = {}
  testVars.value.forEach((v) => {
    variables[v.key] = v.value
  })
  testLoading.value = true
  try {
    const res = await testPromptApi({ content: testContent.value, variables })
    testResult.value = res.data
  } finally {
    testLoading.value = false
  }
}

// ============ 工具函数 ============
function statusMeta(status: string): { type: 'success' | 'info' | 'warning'; label: string } {
  if (status === 'PUBLISHED') return { type: 'success', label: '已发布' }
  if (status === 'ARCHIVED') return { type: 'info', label: '已归档' }
  return { type: 'warning', label: '草稿' }
}

function typeLabel(type: string): string {
  const map: Record<string, string> = { rag: 'RAG', system: '通用系统', agent: 'Agent' }
  return map[type] || type
}
</script>

<style scoped lang="scss">
.search-bar {
  margin-bottom: 12px;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.version-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;

  .version-no {
    font-weight: 600;
    font-size: 14px;
  }

  .platform-tag {
    font-size: 12px;
    color: #909399;
  }
}

.version-remark {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}

.version-content {
  margin-bottom: 8px;
}

.version-actions {
  display: flex;
  gap: 4px;
}

/* LLM 输出渲染区：替代原只读 textarea，渲染 markdown */
.llm-output {
  width: 100%;
  min-height: 120px;
  max-height: 360px;
  overflow: auto;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  box-sizing: border-box;
}

.var-list {
  width: 100%;

  .var-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }
}
</style>
