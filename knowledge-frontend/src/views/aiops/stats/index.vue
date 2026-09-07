<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="AI 调用统计">
    <template #extra>
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        range-separator="-"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        value-format="YYYY-MM-DD HH:mm:ss"
        style="width: 360px; margin-right: 8px"
      />
      <el-button type="primary" :icon="Search" @click="loadStats">查询</el-button>
      <el-button :icon="Refresh" @click="resetRange">最近7天</el-button>
    </template>

    <!-- 概览卡片 -->
    <el-row :gutter="16" v-loading="loading">
      <el-col v-for="card in cards" :key="card.title" :xs="12" :sm="12" :md="8" :lg="4">
        <el-card shadow="hover" class="stat-card-box">
          <div class="stat-card">
            <el-icon :size="36" :color="card.color"><component :is="card.icon" /></el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-title">{{ card.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 排行：模型 / 用户 -->
    <el-row :gutter="16" class="rank-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header><span class="rank-title">费用排行 · 按模型</span></template>
          <el-table :data="stats.byModel" border stripe size="small" max-height="360">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="key" label="模型" min-width="140" show-overflow-tooltip />
            <el-table-column prop="calls" label="调用次数" width="100" align="center" />
            <el-table-column prop="tokens" label="Token" width="120" align="center" />
            <el-table-column label="费用(元)" width="120" align="right">
              <template #default="{ row }">{{ formatCost(row.cost) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header><span class="rank-title">费用排行 · 按用户（Top 10）</span></template>
          <el-table :data="stats.byUser" border stripe size="small" max-height="360">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column label="用户" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.key || (row.userId ? '#' + row.userId : '系统') }}</template>
            </el-table-column>
            <el-table-column prop="calls" label="调用次数" width="100" align="center" />
            <el-table-column prop="tokens" label="Token" width="120" align="center" />
            <el-table-column label="费用(元)" width="120" align="right">
              <template #default="{ row }">{{ formatCost(row.cost) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 按日期趋势 -->
    <el-card shadow="never" class="trend-card">
      <template #header><span class="rank-title">调用趋势 · 按日期（最近 30 天）</span></template>
      <el-table :data="dailyTrend" border stripe size="small" max-height="400">
        <el-table-column prop="day" label="日期" width="140" />
        <el-table-column prop="calls" label="调用次数" width="140" align="center" />
        <el-table-column prop="tokens" label="Token 消耗" width="160" align="center" />
        <el-table-column label="费用(元)" width="160" align="right">
          <template #default="{ row }">{{ formatCost(row.cost) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </PageWrapper>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import PageWrapper from '@/components/PageWrapper/index.vue'
import { statsAiCallApi } from '@/api/aiops'

const loading = ref(false)
const timeRange = ref<[string, string] | null>(null)

const emptyStats: AiCallStatsVo = {
  totalCalls: 0,
  successCount: 0,
  failedCount: 0,
  totalTokens: 0,
  promptTokens: 0,
  completionTokens: 0,
  totalCost: 0,
  avgDurationMs: 0,
  byModel: [],
  byUser: [],
  byDay: []
}
const stats = ref<AiCallStatsVo>({ ...emptyStats })

/** 后端 byDay 按日期倒序返回，趋势表正序展示（旧→新） */
const dailyTrend = computed(() => [...stats.value.byDay].reverse())

const cards = computed(() => [
  { title: '调用次数', value: stats.value.totalCalls, icon: 'Histogram', color: '#409eff' },
  { title: '成功 / 失败', value: `${stats.value.successCount} / ${stats.value.failedCount}`, icon: 'CircleCheck', color: '#67c23a' },
  { title: 'Token 消耗', value: formatNum(stats.value.totalTokens), icon: 'Coin', color: '#e6a23c' },
  {
    title: '输入 / 输出 Token',
    value: `${formatNum(stats.value.promptTokens)} / ${formatNum(stats.value.completionTokens)}`,
    icon: 'Switch',
    color: '#909399'
  },
  { title: '总费用(元)', value: formatCost(stats.value.totalCost), icon: 'Money', color: '#f56c6c' },
  { title: '平均耗时(ms)', value: formatNum(stats.value.avgDurationMs), icon: 'Timer', color: '#9c27b0' }
])

function formatCost(cost: number) {
  if (cost === null || cost === undefined) return '0.000000'
  return Number(cost).toFixed(6)
}

function formatNum(n: number) {
  if (n === null || n === undefined) return '0'
  return n.toLocaleString('zh-CN')
}

async function loadStats() {
  loading.value = true
  try {
    const res = await statsAiCallApi({
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1]
    })
    stats.value = res.data || ({ ...emptyStats } as AiCallStatsVo)
  } finally {
    loading.value = false
  }
}

function resetRange() {
  timeRange.value = null
  loadStats()
}

loadStats()
</script>

<style scoped lang="scss">
.stat-card-box {
  margin-bottom: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-title {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}

.rank-row {
  margin-bottom: 16px;
}

.rank-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.trend-card {
  margin-top: 4px;
}
</style>
