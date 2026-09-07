<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="任务执行记录">
    <template #extra>
      <el-select
        v-model="filter.status"
        placeholder="状态筛选"
        clearable
        style="width: 150px; margin-right: 8px"
        @change="onFilter"
      >
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-button :icon="Refresh" @click="loadData">刷新</el-button>
    </template>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="id" label="任务ID" width="170" />
      <el-table-column prop="definitionCode" label="流程" width="160" />
      <el-table-column prop="goal" label="任务目标" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <StatusTag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="currentNode" label="当前节点" width="140" show-overflow-tooltip />
      <el-table-column prop="retryCount" label="重试" width="70" align="center" />
      <el-table-column prop="tokenUsage" label="Token" width="100" align="center" />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row as WorkflowTaskVo)">详情</el-button>
          <el-button v-if="row.status === 'WAITING_HUMAN'" link type="warning" @click="openApprove(row as WorkflowTaskVo)">
            审批
          </el-button>
          <el-button v-if="row.status === 'FAILED'" link type="danger" @click="onRetry(row as WorkflowTaskVo)">
            重试
          </el-button>
          <el-button
            v-if="!isTerminal(row.status)"
            link
            type="info"
            @click="onCancel(row as WorkflowTaskVo)"
          >
            取消
          </el-button>
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

    <!-- 人工审批弹窗 -->
    <el-dialog v-model="approveVisible" title="人工审批" width="480px">
      <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="任务ID">{{ currentTask?.id }}</el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ currentTask?.currentNode }}</el-descriptions-item>
        <el-descriptions-item label="目标">{{ currentTask?.goal }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="approveForm" label-width="80px">
        <el-form-item label="审批结果" required>
          <el-radio-group v-model="approveForm.approved">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input
            v-model="approveForm.comment"
            type="textarea"
            :rows="3"
            maxlength="300"
            show-word-limit
            placeholder="审批意见将作为 HUMAN 节点输入写入流程上下文"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onApprove">提交审批</el-button>
      </template>
    </el-dialog>
  </PageWrapper>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageWrapper from '@/components/PageWrapper/index.vue'
import StatusTag from '@/views/agent/components/StatusTag.vue'
import {
  pageWorkflowApi, approveWorkflowApi, retryWorkflowApi, cancelWorkflowApi
} from '@/api/workflow'

const router = useRouter()
const loading = ref(false)
const tableData = ref<WorkflowTaskVo[]>([])
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)
const filter = reactive({ status: undefined as string | undefined })

const statusOptions = [
  { label: '待执行', value: 'CREATED' },
  { label: '执行中', value: 'RUNNING' },
  { label: '待审批', value: 'WAITING_HUMAN' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELED' }
]

const approveVisible = ref(false)
const submitting = ref(false)
const currentTask = ref<WorkflowTaskVo>()
const approveForm = reactive({ approved: true, comment: '' })

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await pageWorkflowApi({
      current: pageNo.value,
      size: pageSize.value,
      status: filter.status || undefined
    })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function onFilter() {
  pageNo.value = 1
  loadData()
}

function isTerminal(status: string) {
  return ['COMPLETED', 'FAILED', 'CANCELED'].includes(status)
}

function goDetail(row: WorkflowTaskVo) {
  router.push({ path: '/agent/detail', query: { type: 'workflow', id: String(row.id) } })
}

function openApprove(row: WorkflowTaskVo) {
  currentTask.value = row
  approveForm.approved = true
  approveForm.comment = ''
  approveVisible.value = true
}

async function onApprove() {
  if (!currentTask.value) return
  submitting.value = true
  try {
    await approveWorkflowApi(currentTask.value.id, {
      approved: approveForm.approved,
      comment: approveForm.comment || undefined
    })
    ElMessage.success(approveForm.approved ? '已通过审批' : '已驳回')
    approveVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function onRetry(row: WorkflowTaskVo) {
  await ElMessageBox.confirm(`确定重试任务「${row.id}」？将从最近失败节点重新执行。`, '重试确认', {
    type: 'warning'
  })
  await retryWorkflowApi(row.id)
  ElMessage.success('已触发重试')
  loadData()
}

async function onCancel(row: WorkflowTaskVo) {
  const { value } = await ElMessageBox.prompt('请输入取消原因（可选）', '取消流程', {
    confirmButtonText: '确定取消',
    cancelButtonText: '返回',
    inputPlaceholder: '取消原因',
    inputValidator: () => true
  })
  await cancelWorkflowApi(row.id, value || undefined)
  ElMessage.success('已取消')
  loadData()
}
</script>

<style scoped lang="scss">
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
