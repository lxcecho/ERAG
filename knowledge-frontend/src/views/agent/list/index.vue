<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="Agent 列表">
    <template #extra>
      <el-button type="primary" :icon="Promotion" @click="openStart">启动 Agent</el-button>
      <el-button :icon="Refresh" style="margin-left: 8px" @click="loadData">刷新</el-button>
    </template>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="id" label="任务ID" width="170" />
      <el-table-column prop="goal" label="任务目标" min-width="240" show-overflow-tooltip />
      <el-table-column label="知识库" width="160">
        <template #default="{ row }">
          {{ kbName(row.kbId) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <StatusTag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="stepCount" label="步骤数" width="90" align="center" />
      <el-table-column prop="tokenUsage" label="Token" width="100" align="center" />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column prop="finishedTime" label="完成时间" width="170" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row as AgentTaskVo)">查看过程</el-button>
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

    <!-- 启动 Agent 配置弹窗 -->
    <el-dialog v-model="startVisible" title="启动 Agent 任务" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="知识库" required>
          <el-select
            v-model="form.kbId"
            placeholder="选择检索范围知识库"
            filterable
            style="width: 100%"
          >
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务目标" required>
          <el-input
            v-model="form.goal"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="用自然语言描述分析诉求，如：分析 2025 销售政策相比 2024 的变化"
          />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="自主式 Agent 将按 规划 → 检索 → 分析 → 报告 四步流水线异步执行"
          description="启动后立即返回任务ID，可在「执行过程查看」中通过 SSE 实时追踪进度。"
        />
      </el-form>
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onStart">确认启动</el-button>
      </template>
    </el-dialog>
  </PageWrapper>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Promotion, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageWrapper from '@/components/PageWrapper/index.vue'
import StatusTag from '@/views/agent/components/StatusTag.vue'
import { pageKbApi } from '@/api/kb'
import { pageAgentApi, startAgentApi } from '@/api/agent'

const router = useRouter()
const loading = ref(false)
const tableData = ref<AgentTaskVo[]>([])
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)
const kbList = ref<KbItem[]>([])

const startVisible = ref(false)
const submitting = ref(false)
const form = reactive({ kbId: undefined as number | string | undefined, goal: '' })

onMounted(async () => {
  const res = await pageKbApi({ pageNo: 1, pageSize: 100 })
  kbList.value = res.data.records
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await pageAgentApi({ current: pageNo.value, size: pageSize.value })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function kbName(kbId: number | string) {
  return kbList.value.find((k) => k.id === kbId)?.name || `#${kbId}`
}

function openStart() {
  form.kbId = undefined
  form.goal = ''
  startVisible.value = true
}

async function onStart() {
  if (!form.kbId) return ElMessage.warning('请选择知识库')
  if (!form.goal.trim()) return ElMessage.warning('请输入任务目标')
  submitting.value = true
  try {
    const res = await startAgentApi({ kbId: form.kbId, goal: form.goal.trim() })
    ElMessage.success(`任务已启动，ID: ${res.data}`)
    startVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

function goDetail(row: AgentTaskVo) {
  router.push({ path: '/agent/detail', query: { type: 'agent', id: String(row.id) } })
}
</script>

<style scoped lang="scss">
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
