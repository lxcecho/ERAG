<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="知识库管理">
    <template #extra>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建知识库</el-button>
    </template>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="docCount" label="文档数" width="90" align="center" />
      <el-table-column prop="ownerId" label="创建人" width="90" align="center" />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openMembers(row)">成员</el-button>
          <el-button link type="primary" @click="goDocuments(row)">文档</el-button>
          <el-button link type="primary" @click="goChat(row)">问答</el-button>
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
      @size-change="loadData"
    />

    <!-- 新建知识库对话框 -->
    <el-dialog v-model="createVisible" title="新建知识库" width="460px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="128" placeholder="知识库名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="onCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 成员管理抽屉 -->
    <el-drawer v-model="memberVisible" title="成员管理" size="520px">
      <div class="member-toolbar">
        <el-input v-model="memberForm.userId" placeholder="用户ID" style="width: 120px" />
        <el-select v-model="memberForm.role" style="width: 120px; margin-left: 8px">
          <el-option label="编辑者" value="editor" />
          <el-option label="查看者" value="viewer" />
        </el-select>
        <el-button type="primary" @click="onAddMember" style="margin-left: 8px">添加</el-button>
      </div>
      <el-table :data="members" border style="margin-top: 12px">
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="roleTag(row.role)">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button v-if="row.role !== 'owner'" link type="danger" @click="onRemoveMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </PageWrapper>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageWrapper from '@/components/PageWrapper/index.vue'
import {
  pageKbApi, createKbApi, removeKbApi,
  pageMemberApi, addMemberApi, removeMemberApi
} from '@/api/kb'

const router = useRouter()
const loading = ref(false)
const tableData = ref<KbItem[]>([])
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)

const createVisible = ref(false)
const form = reactive({ name: '', description: '' })

const memberVisible = ref(false)
const currentKb = ref<KbItem>()
const members = ref<KbMember[]>([])
const memberForm = reactive({ userId: undefined as number | undefined, role: 'viewer' })

async function loadData() {
  loading.value = true
  try {
    const res = await pageKbApi({ pageNo: pageNo.value, pageSize: pageSize.value })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.name = ''
  form.description = ''
  createVisible.value = true
}

async function onCreate() {
  if (!form.name.trim()) return ElMessage.warning('请输入名称')
  await createKbApi({ name: form.name, description: form.description })
  ElMessage.success('创建成功')
  createVisible.value = false
  loadData()
}

async function onRemove(row: KbItem) {
  await ElMessageBox.confirm(`确定删除知识库「${row.name}」？`, '提示', { type: 'warning' })
  await removeKbApi(row.id)
  ElMessage.success('已删除')
  loadData()
}

function goDocuments(row: KbItem) {
  router.push({ path: '/knowledge/documents', query: { kbId: row.id } })
}

/** 进入 RAG 问答：携带知识库 ID，chat 页自动关联并切换到 RAG 模式 */
function goChat(row: KbItem) {
  router.push({ path: '/chat', query: { kbId: row.id } })
}

async function openMembers(row: KbItem) {
  currentKb.value = row
  memberVisible.value = true
  const res = await pageMemberApi(row.id, { pageNo: 1, pageSize: 100 })
  members.value = res.data.records
}

async function onAddMember() {
  if (!currentKb.value || !memberForm.userId) return
  await addMemberApi(currentKb.value.id, { userId: memberForm.userId, role: memberForm.role })
  ElMessage.success('已添加')
  memberForm.userId = undefined
  const res = await pageMemberApi(currentKb.value.id, { pageNo: 1, pageSize: 100 })
  members.value = res.data.records
}

async function onRemoveMember(row: KbMember) {
  if (!currentKb.value) return
  await removeMemberApi(currentKb.value.id, row.id)
  ElMessage.success('已移除')
  const res = await pageMemberApi(currentKb.value.id, { pageNo: 1, pageSize: 100 })
  members.value = res.data.records
}

function roleLabel(role: string) {
  return { owner: '拥有者', editor: '编辑者', viewer: '查看者' }[role] || role
}
function roleTag(role: string): any {
  return { owner: 'danger', editor: 'warning', viewer: 'info' }[role] || 'info'
}

loadData()
</script>

<style scoped lang="scss">
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.member-toolbar {
  display: flex;
  align-items: center;
}
</style>
