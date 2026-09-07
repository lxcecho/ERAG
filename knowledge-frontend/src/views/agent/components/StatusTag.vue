<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <el-tag :type="tagType" :effect="effect" size="small" class="status-tag">
    <span v-if="active" class="dot" />
    {{ label }}
  </el-tag>
</template>

<script setup lang="ts">
/**
 * 状态标签：统一渲染 Agent / Workflow / 节点运行状态。
 * - 进行中态（EXECUTING/RUNNING/WAITING_HUMAN）带脉冲圆点，强化"活"状态感知
 * - 兼容后端节点级状态（SUCCESS/FAILED/RUNNING/SKIPPED 等）
 */
import { computed } from 'vue'

const props = defineProps<{ status?: string }>()

const STATUS_META: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info'; active?: boolean }> = {
  // Agent
  CREATED: { label: '待执行', type: 'info' },
  EXECUTING: { label: '执行中', type: 'warning', active: true },
  // Workflow
  RUNNING: { label: '执行中', type: 'warning', active: true },
  WAITING_HUMAN: { label: '待审批', type: 'danger', active: true },
  // 终态
  COMPLETED: { label: '已完成', type: 'success' },
  SUCCESS: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  CANCELED: { label: '已取消', type: 'info' },
  SKIPPED: { label: '已跳过', type: 'info' },
  PENDING: { label: '待执行', type: 'info' }
}

const meta = computed(() => {
  const s = (props.status || '').toUpperCase()
  return STATUS_META[s] || { label: props.status || '未知', type: 'info' as const }
})

const tagType = computed(() => meta.value.type)
const label = computed(() => meta.value.label)
const effect = computed(() => (meta.value.active ? 'dark' : 'light'))
const active = computed(() => !!meta.value.active)
</script>

<style scoped lang="scss">
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
  animation: pulse 1.2s infinite ease-in-out;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.4;
    transform: scale(0.7);
  }
}
</style>
