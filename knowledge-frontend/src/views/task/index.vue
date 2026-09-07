<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="文档解析任务">
    <template #extra>
      <el-select v-model="filter.kbId" placeholder="知识库" clearable filterable style="width: 180px; margin-right: 8px" @change="loadData">
        <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
      </el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 130px" @change="loadData">
        <el-option label="待处理" :value="0" />
        <el-option label="处理中" :value="1" />
        <el-option label="成功" :value="2" />
        <el-option label="失败" :value="3" />
      </el-select>
      <el-button :icon="Refresh" style="margin-left: 8px" @click="loadData">刷新</el-button>
    </template>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column label="文档名" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.originalName || '文档已删除' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="errorMsg" label="失败原因" min-width="180" show-overflow-tooltip />
      <el-table-column prop="startTime" label="开始时间" width="170" />
      <el-table-column prop="endTime" label="结束时间" width="170" />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="warning" @click="onRetry(row)" :disabled="row.status !== 3">重试</el-button>
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
  </PageWrapper>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageWrapper from '@/components/PageWrapper/index.vue'
import { pageKbApi } from '@/api/kb'
import { pageTaskApi, retryTaskApi } from '@/api/task'

const loading = ref(false)
const tableData = ref<ParseTask[]>([])
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)
const kbList = ref<KbItem[]>([])
const filter = reactive({ kbId: undefined as number | undefined, status: undefined as number | undefined })

onMounted(async () => {
  const res = await pageKbApi({ pageNo: 1, pageSize: 100 })
  kbList.value = res.data.records
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await pageTaskApi({ pageNo: pageNo.value, pageSize: pageSize.value, kbId: filter.kbId, status: filter.status })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function onRetry(row: ParseTask) {
  await retryTaskApi(row.id)
  ElMessage.success('已重新派发任务')
  loadData()
}

function statusLabel(s: number) {
  return ['待处理', '处理中', '成功', '失败'][s] || '未知'
}
function statusTag(s: number): any {
  return ['info', 'warning', 'success', 'danger'][s] || 'info'
}
</script>

<style scoped lang="scss">
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
