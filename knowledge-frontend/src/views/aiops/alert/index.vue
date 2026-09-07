<!--
 * @since: 2026-08-02
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="告警规则">
    <template #extra>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建规则</el-button>
      <el-button :icon="Refresh" @click="loadList">刷新</el-button>
    </template>

    <el-table :data="list" v-loading="loading" border stripe size="small">
      <el-table-column prop="name" label="规则名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="resource" label="资源" width="130" />
      <el-table-column prop="metric" label="指标" width="120" />
      <el-table-column label="阈值条件" width="150" align="center">
        <template #default="{ row }">
          {{ row.operator }} {{ row.threshold }}
        </template>
      </el-table-column>
      <el-table-column prop="windowMinutes" label="窗口(分)" width="90" align="center" />
      <el-table-column prop="level" label="级别" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="levelTag(row.level)">{{ row.level }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled === 1"
            @change="(val: boolean) => toggleEnabled(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑规则' : '新建规则'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="规则名称" prop="name">
          <el-input v-model="form.name" placeholder="如 Milvus 检索延迟告警" />
        </el-form-item>
        <el-form-item label="资源" prop="resource">
          <el-select v-model="form.resource" placeholder="选择资源" style="width: 100%">
            <el-option label="Milvus 检索" value="milvus:search" />
            <el-option label="ES 检索" value="es:search" />
            <el-option label="MQ 解析" value="mq:parse" />
            <el-option label="LLM 调用" value="llm:chat" />
          </el-select>
        </el-form-item>
        <el-form-item label="指标" prop="metric">
          <el-select v-model="form.metric" placeholder="选择指标" style="width: 100%">
            <el-option label="调用次数 calls" value="calls" />
            <el-option label="错误率 error_rate" value="error_rate" />
            <el-option label="P95 延迟 latency_p95" value="latency_p95" />
            <el-option label="Token 消耗 token_usage" value="token_usage" />
          </el-select>
        </el-form-item>
        <el-form-item label="比较符" prop="operator">
          <el-select v-model="form.operator" style="width: 100%">
            <el-option label="大于 GT" value="GT" />
            <el-option label="大于等于 GTE" value="GTE" />
            <el-option label="小于 LT" value="LT" />
            <el-option label="小于等于 LTE" value="LTE" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值" prop="threshold">
          <el-input-number v-model="form.threshold" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="窗口(分钟)">
          <el-input-number v-model="form.windowMinutes" :min="1" :max="1440" style="width: 100%" />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="form.level" style="width: 100%">
            <el-option label="INFO" value="INFO" />
            <el-option label="WARN" value="WARN" />
            <el-option label="CRITICAL" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="formEnabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>
  </PageWrapper>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageWrapper from '@/components/PageWrapper/index.vue'
import {
  listAlertRulesApi,
  createAlertRuleApi,
  updateAlertRuleApi,
  deleteAlertRuleApi,
  toggleAlertRuleApi
} from '@/api/ops'

const loading = ref(false)
const list = ref<AlertRule[]>([])

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const defaultForm = (): AlertRuleRequest => ({
  name: '',
  resource: 'milvus:search',
  metric: 'latency_p95',
  operator: 'GT',
  threshold: 1000,
  windowMinutes: 5,
  level: 'WARN',
  enabled: 1
})
const form = reactive<AlertRuleRequest>(defaultForm())
const formEnabled = ref(true)

const rules: FormRules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  resource: [{ required: true, message: '请选择资源', trigger: 'change' }],
  metric: [{ required: true, message: '请选择指标', trigger: 'change' }],
  operator: [{ required: true, message: '请选择比较符', trigger: 'change' }],
  threshold: [{ required: true, message: '请输入阈值', trigger: 'blur' }]
}

type TagType = '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'

function levelTag(level: string): TagType {
  const map: Record<string, TagType> = { INFO: 'info', WARN: 'warning', CRITICAL: 'danger' }
  return map[level] ?? ''
}

async function loadList() {
  loading.value = true
  try {
    const res = await listAlertRulesApi()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, defaultForm())
  formEnabled.value = true
  dialogVisible.value = true
}

function openEdit(row: AlertRule) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    resource: row.resource,
    metric: row.metric,
    operator: row.operator,
    threshold: row.threshold,
    windowMinutes: row.windowMinutes,
    level: row.level,
    enabled: row.enabled
  })
  formEnabled.value = row.enabled === 1
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload: AlertRuleRequest = { ...form, enabled: formEnabled.value ? 1 : 0 }
      if (editingId.value) {
        await updateAlertRuleApi(editingId.value, payload)
        ElMessage.success('更新成功')
      } else {
        await createAlertRuleApi(payload)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadList()
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row: AlertRule) {
  await ElMessageBox.confirm(`确认删除规则「${row.name}」?`, '提示', { type: 'warning' })
  await deleteAlertRuleApi(row.id)
  ElMessage.success('删除成功')
  loadList()
}

async function toggleEnabled(row: AlertRule, val: boolean) {
  try {
    await toggleAlertRuleApi(row.id, val)
    row.enabled = val ? 1 : 0
    ElMessage.success(val ? '已启用' : '已停用')
  } catch {
    // 失败时恢复由重新加载保证
    loadList()
  }
}

loadList()
</script>

<style scoped lang="scss"></style>
