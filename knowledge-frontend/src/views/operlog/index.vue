<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="操作日志">
    <template #extra>
      <el-input v-model="filter.title" placeholder="模块标题" clearable style="width: 160px; margin-right: 8px" @change="loadData" />
      <el-input v-model="filter.operUser" placeholder="操作用户" clearable style="width: 140px; margin-right: 8px" @change="loadData" />
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 120px" @change="loadData">
        <el-option label="正常" :value="0" />
        <el-option label="异常" :value="1" />
      </el-select>
    </template>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="title" label="模块" width="120" />
      <el-table-column label="业务类型" width="100" align="center">
        <template #default="{ row }">{{ bizLabel(row.businessType) }}</template>
      </el-table-column>
      <el-table-column prop="operUser" label="操作用户" width="110" />
      <el-table-column prop="requestUrl" label="请求URL" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'">
            {{ row.status === 0 ? '正常' : '异常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="costTime" label="耗时(ms)" width="100" align="center" />
      <el-table-column prop="operIp" label="IP" width="130" />
      <el-table-column prop="createTime" label="操作时间" width="170" />
      <el-table-column label="详情" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">查看</el-button>
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

    <el-dialog v-model="detailVisible" title="日志详情" width="680px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="方法">{{ detail.method }}</el-descriptions-item>
        <el-descriptions-item label="请求参数">{{ detail.requestParam }}</el-descriptions-item>
        <el-descriptions-item label="响应结果">{{ detail.responseResult }}</el-descriptions-item>
        <el-descriptions-item label="错误信息">{{ detail.errorMsg }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </PageWrapper>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import PageWrapper from '@/components/PageWrapper/index.vue'
import { pageOperLogApi } from '@/api/operlog'

const loading = ref(false)
const tableData = ref<OperLog[]>([])
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)
const filter = reactive({ title: '', operUser: '', status: undefined as number | undefined })

const detailVisible = ref(false)
const detail = ref<Partial<OperLog>>({})

async function loadData() {
  loading.value = true
  try {
    const res = await pageOperLogApi({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      title: filter.title,
      operUser: filter.operUser,
      status: filter.status
    })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function showDetail(row: OperLog) {
  detail.value = row
  detailVisible.value = true
}

function bizLabel(t: number) {
  return ['其它', '新增', '修改', '删除', '导出', '导入', '登录'][t] || '其它'
}

loadData()
</script>

<style scoped lang="scss">
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
