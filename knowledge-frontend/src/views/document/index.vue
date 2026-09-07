<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="文档管理">
    <template #extra>
      <el-select v-model="filterKbId" placeholder="选择知识库" filterable clearable style="width: 200px; margin-right: 8px" @change="loadData">
        <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
      </el-select>
      <el-upload :show-file-list="false" :before-upload="onUpload" accept=".pdf,.doc,.docx,.md">
        <el-button type="primary" :icon="Upload" :disabled="!filterKbId">上传文档</el-button>
      </el-upload>
    </template>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="originalName" label="文件名" min-width="200" show-overflow-tooltip />
      <el-table-column prop="kbName" label="所属知识库" width="140" />
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="fileType" label="类型" width="80" align="center" />
      <el-table-column label="解析状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="切片数" width="80" align="center" />
      <el-table-column prop="createTime" label="上传时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="onParse(row)" :disabled="row.status === 1">解析</el-button>
          <el-button link type="danger" @click="onRemove(row)">删除</el-button>
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
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageWrapper from '@/components/PageWrapper/index.vue'
import { pageKbApi } from '@/api/kb'
import { pageDocumentApi, uploadDocumentApi, removeDocumentApi, triggerParseApi } from '@/api/document'

const route = useRoute()
const loading = ref(false)
const tableData = ref<KbDocument[]>([])
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)
const kbList = ref<KbItem[]>([])
const filterKbId = ref<number | string>()

onMounted(async () => {
  const res = await pageKbApi({ pageNo: 1, pageSize: 100 })
  kbList.value = res.data.records
  // 优先用路由参数指定的 kbId，否则默认选第一个知识库（避免"需指定知识库ID"提示）
  if (route.query.kbId) {
    filterKbId.value = route.query.kbId as string
  } else if (kbList.value.length > 0) {
    filterKbId.value = kbList.value[0].id
  }
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await pageDocumentApi({ pageNo: pageNo.value, pageSize: pageSize.value, kbId: filterKbId.value })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function onUpload(file: File) {
  if (!filterKbId.value) return ElMessage.warning('请先选择知识库')
  const loadingInst = ElMessage({ message: '上传中...', duration: 0 })
  try {
    await uploadDocumentApi(filterKbId.value, file)
    ElMessage.success('上传成功，已创建解析任务')
    loadData()
  } finally {
    loadingInst.close()
  }
  return false
}

async function onParse(row: KbDocument) {
  await triggerParseApi(row.id)
  ElMessage.success('已触发解析任务')
  loadData()
}

async function onRemove(row: KbDocument) {
  await ElMessageBox.confirm(`确定删除文档「${row.originalName}」？`, '提示', { type: 'warning' })
  await removeDocumentApi(row.id)
  ElMessage.success('已删除')
  loadData()
}

function formatSize(size: number) {
  if (size < 1024) return size + 'B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + 'KB'
  return (size / 1024 / 1024).toFixed(1) + 'MB'
}
function statusLabel(s: number) {
  return ['待解析', '解析中', '已解析', '解析失败'][s] || '未知'
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
