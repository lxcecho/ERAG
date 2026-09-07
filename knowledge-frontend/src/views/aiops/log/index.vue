<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="AI 调用日志">
    <template #extra>
      <el-select v-model="filter.module" placeholder="业务模块" clearable style="width: 150px; margin-right: 8px" @change="resetAndLoad">
        <el-option v-for="m in moduleOptions" :key="m.value" :label="m.label" :value="m.value" />
      </el-select>
      <el-select v-model="filter.bizType" placeholder="调用类型" clearable style="width: 120px; margin-right: 8px" @change="resetAndLoad">
        <el-option label="CHAT" value="CHAT" />
        <el-option label="EMBEDDING" value="EMBEDDING" />
      </el-select>
      <el-input v-model="filter.modelName" placeholder="模型名" clearable style="width: 140px; margin-right: 8px" @change="resetAndLoad" />
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 110px; margin-right: 8px" @change="resetAndLoad">
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILED" />
      </el-select>
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        range-separator="-"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        value-format="YYYY-MM-DD HH:mm:ss"
        style="width: 360px; margin-right: 8px"
        @change="resetAndLoad"
      />
      <el-button type="primary" :icon="Search" @click="resetAndLoad">查询</el-button>
      <el-button :icon="Refresh" @click="resetFilter">重置</el-button>
    </template>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="createTime" label="调用时间" width="170" />
      <el-table-column label="用户" width="120" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.username || (row.userId ? '#' + row.userId : '系统') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="模块" width="130" align="center">
        <template #default="{ row }">
          <el-tag :type="moduleTagType(row.module)" effect="plain">{{ moduleLabel(row.module) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="bizType" label="类型" width="100" align="center" />
      <el-table-column prop="modelName" label="模型" width="150" show-overflow-tooltip />
      <el-table-column label="Token 消耗" width="150" align="center">
        <template #default="{ row }">
          <span class="token-cell">
            <strong>{{ row.totalTokens }}</strong>
            <span class="token-detail">（入{{ row.promptTokens }} / 出{{ row.completionTokens }}）</span>
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="durationMs" label="耗时(ms)" width="100" align="center" />
      <el-table-column label="费用(元)" width="110" align="right">
        <template #default="{ row }">{{ formatCost(row.cost) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">
            {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="详情" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row as AiCallLog)">查看</el-button>
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

    <el-dialog v-model="detailVisible" title="调用详情" width="640px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="调用时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ detail.username || (detail.userId ? '#' + detail.userId : '系统调用') }}</el-descriptions-item>
        <el-descriptions-item label="业务模块">{{ moduleLabel(detail.module) }}（{{ detail.module }}）</el-descriptions-item>
        <el-descriptions-item label="调用类型">{{ detail.bizType }}</el-descriptions-item>
        <el-descriptions-item label="模型">{{ detail.modelName }}</el-descriptions-item>
        <el-descriptions-item label="Token">
          总计 {{ detail.totalTokens }}（输入 {{ detail.promptTokens }} / 输出 {{ detail.completionTokens }}）
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.durationMs }} ms</el-descriptions-item>
        <el-descriptions-item label="费用">{{ formatCost(detail.cost) }} 元</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detail.status === 'SUCCESS' ? 'success' : 'danger'">
            {{ detail.status === 'SUCCESS' ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMsg" label="错误信息">{{ detail.errorMsg }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </PageWrapper>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import PageWrapper from '@/components/PageWrapper/index.vue'
import { pageAiCallLogApi } from '@/api/aiops'

const loading = ref(false)
const tableData = ref<AiCallLog[]>([])
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)
const timeRange = ref<[string, string] | null>(null)
const filter = reactive({
  module: '',
  bizType: '',
  modelName: '',
  status: ''
})

const detailVisible = ref(false)
const detail = ref<Partial<AiCallLog>>({})

/** 业务模块字典（value 对应后端 AiCallLog.module） */
const moduleOptions = [
  { value: 'rag_chat', label: 'RAG 对话' },
  { value: 'agent', label: 'Agent' },
  { value: 'workflow', label: 'Workflow' },
  { value: 'prompt_test', label: 'Prompt 测试' },
  { value: 'embedding', label: '向量化' },
  { value: 'document_compare', label: '文档对比' },
  { value: 'report_generate', label: '报告生成' }
]

function moduleLabel(m?: string) {
  return moduleOptions.find((x) => x.value === m)?.label || m || '-'
}

function moduleTagType(m?: string) {
  switch (m) {
    case 'rag_chat':
      return 'primary'
    case 'agent':
    case 'workflow':
      return 'warning'
    case 'embedding':
      return 'info'
    case 'prompt_test':
      return 'success'
    default:
      return 'info'
  }
}

function formatCost(cost?: number) {
  if (cost === null || cost === undefined) return '0.000000'
  return Number(cost).toFixed(6)
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageAiCallLogApi({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      module: filter.module || undefined,
      bizType: filter.bizType || undefined,
      modelName: filter.modelName || undefined,
      status: filter.status || undefined,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1]
    })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  pageNo.value = 1
  loadData()
}

function resetFilter() {
  filter.module = ''
  filter.bizType = ''
  filter.modelName = ''
  filter.status = ''
  timeRange.value = null
  resetAndLoad()
}

function showDetail(row: AiCallLog) {
  detail.value = row
  detailVisible.value = true
}

loadData()
</script>

<style scoped lang="scss">
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.token-cell {
  display: inline-flex;
  flex-direction: column;
  line-height: 1.3;
}

.token-detail {
  font-size: 12px;
  color: #909399;
}
</style>
