<!--
 * @since: 2026-08-02
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="链路追踪">
    <template #extra>
      <el-input
        v-model="query.traceId"
        placeholder="traceId 精确查询"
        clearable
        style="width: 240px; margin-right: 8px"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="query.spanType" placeholder="span 类型" clearable style="width: 130px; margin-right: 8px">
        <el-option label="ROOT" value="ROOT" />
        <el-option label="SEARCH" value="SEARCH" />
        <el-option label="LLM" value="LLM" />
        <el-option label="TOOL" value="TOOL" />
        <el-option label="MQ" value="MQ" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
    </template>

    <el-table :data="records" v-loading="loading" border stripe size="small">
      <el-table-column prop="traceId" label="Trace ID" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" @click="openTree(row.traceId)">{{ row.traceId }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="spanName" label="入口 Span" min-width="140" show-overflow-tooltip />
      <el-table-column prop="spanType" label="类型" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="typeTag(row.spanType)">{{ row.spanType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'OK' ? 'success' : 'danger'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="durationMs" label="耗时(ms)" width="110" align="center" />
      <el-table-column prop="startTime" label="开始时间" width="170" />
      <el-table-column label="操作" width="90" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openTree(row.traceId)">Span 树</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      v-model:current-page="query.pageNo"
      v-model:page-size="query.pageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="loadList"
      @current-change="loadList"
    />

    <!-- Span 树弹窗 -->
    <el-dialog v-model="treeVisible" title="Span 调用树" width="720px" destroy-on-close>
      <div v-loading="treeLoading">
        <el-empty v-if="!treeData" description="无 Span 数据" />
        <el-tree
          v-else
          :data="[treeData]"
          :props="{ children: 'children', label: 'spanName' }"
          node-key="spanId"
          default-expand-all
        >
          <template #default="{ data }">
            <div class="tree-node">
              <el-tag size="small" :type="typeTag(data.spanType)">{{ data.spanType }}</el-tag>
              <span class="node-name">{{ data.spanName }}</span>
              <span class="node-time">{{ data.durationMs }}ms</span>
              <el-tag size="small" :type="data.status === 'OK' ? 'success' : 'danger'" effect="plain">
                {{ data.status }}
              </el-tag>
            </div>
          </template>
        </el-tree>
      </div>
    </el-dialog>
  </PageWrapper>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import PageWrapper from '@/components/PageWrapper/index.vue'
import { pageTracesApi, getTraceTreeApi } from '@/api/ops'

const loading = ref(false)
const records = ref<OpsTrace[]>([])
const total = ref(0)

const query = reactive<TraceQuery>({
  pageNo: 1,
  pageSize: 10,
  traceId: '',
  spanType: '',
  start: '',
  end: ''
})

const treeVisible = ref(false)
const treeLoading = ref(false)
const treeData = ref<TraceTreeVo | null>(null)

function typeTag(type: string) {
  const map: Record<string, string> = {
    ROOT: '',
    SEARCH: 'warning',
    LLM: 'success',
    TOOL: 'info',
    MQ: 'danger'
  }
  return map[type] ?? ''
}

async function loadList() {
  loading.value = true
  try {
    const res = await pageTracesApi(query)
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNo = 1
  loadList()
}

function handleReset() {
  query.traceId = ''
  query.spanType = ''
  query.pageNo = 1
  loadList()
}

async function openTree(traceId: string) {
  treeVisible.value = true
  treeLoading.value = true
  treeData.value = null
  try {
    const res = await getTraceTreeApi(traceId)
    treeData.value = res.data || null
  } finally {
    treeLoading.value = false
  }
}

loadList()
</script>

<style scoped lang="scss">
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.node-name {
  font-weight: 600;
  color: #303133;
}

.node-time {
  color: #909399;
}
</style>
