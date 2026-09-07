<!--
 * @since: 2026-08-02
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="AI 运维看板">
    <template #extra>
      <el-button :icon="Refresh" @click="loadDashboard" :loading="loading">刷新</el-button>
    </template>

    <!-- 7 指标卡片 -->
    <el-row :gutter="16" v-loading="loading" class="card-row">
      <el-col v-for="card in dashboard.cards" :key="card.key" :xs="12" :sm="12" :md="8" :lg="6" :xl="4">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value">
            {{ formatNum(card.value) }}<span class="metric-unit">{{ card.unit }}</span>
          </div>
          <div class="metric-title">{{ card.title }}</div>
          <div class="metric-sub" v-if="card.sub">{{ card.sub }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图：调用 + 费用 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never">
          <template #header><span class="chart-title">模型调用趋势（近 30 天）</span></template>
          <v-chart class="chart" :option="callTrendOption" autoresize />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never">
          <template #header><span class="chart-title">费用趋势（近 30 天）</span></template>
          <v-chart class="chart" :option="costTrendOption" autoresize />
        </el-card>
      </el-col>
    </el-row>

    <!-- 资源延迟柱状图 + 预算/预测 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never">
          <template #header><span class="chart-title">基础设施资源平均延迟（ms）</span></template>
          <v-chart class="chart" :option="latencyOption" autoresize />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="budget-card">
          <template #header><span class="chart-title">预算与预测</span></template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="当月累计费用">{{ formatCost(budget.monthCost) }} 元</el-descriptions-item>
            <el-descriptions-item label="月度预算">{{ formatCost(budget.budget) }} 元</el-descriptions-item>
            <el-descriptions-item label="已用占比">
              <el-tag :type="budget.exceeded ? 'danger' : budget.usedPercent > 80 ? 'warning' : 'success'">
                {{ budget.usedPercent.toFixed(1) }}%
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="近 7 天日均">{{ formatCost(forecast.dailyAvg7d) }} 元</el-descriptions-item>
            <el-descriptions-item label="剩余天数">{{ forecast.remainingDays }} 天</el-descriptions-item>
            <el-descriptions-item label="预测月底总费用">
              <span class="forecast-value">{{ formatCost(forecast.forecast) }} 元</span>
            </el-descriptions-item>
          </el-descriptions>
          <div class="forecast-note">{{ forecast.note }}</div>
        </el-card>
      </el-col>
    </el-row>
  </PageWrapper>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageWrapper from '@/components/PageWrapper/index.vue'
import VChart from '@/utils/echarts'
import {
  getDashboardApi,
  getCostBudgetApi,
  getCostForecastApi
} from '@/api/ops'

const loading = ref(false)

const emptyDashboard: OpsDashboardVo = {
  cards: [],
  callTrend: [],
  costTrend: [],
  latencyByResource: []
}
const dashboard = ref<OpsDashboardVo>({ ...emptyDashboard })

const emptyBudget: CostBudgetStatus = { monthCost: 0, budget: 0, exceeded: false, usedPercent: 0 }
const budget = ref<CostBudgetStatus>({ ...emptyBudget })

const emptyForecast: CostForecast = {
  monthToDate: 0,
  dailyAvg7d: 0,
  remainingDays: 0,
  forecast: 0,
  note: ''
}
const forecast = ref<CostForecast>({ ...emptyForecast })

/** 后端按日倒序返回，图表正序展示（旧→新） */
const callTrendAsc = computed(() => [...dashboard.value.callTrend].reverse())
const costTrendAsc = computed(() => [...dashboard.value.costTrend].reverse())

const callTrendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['调用次数', 'Token 消耗'], top: 0 },
  grid: { left: 50, right: 50, top: 40, bottom: 30 },
  xAxis: { type: 'category', data: callTrendAsc.value.map((p) => p.day), axisLabel: { fontSize: 10 } },
  yAxis: [
    { type: 'value', name: '次数', position: 'left' },
    { type: 'value', name: 'Token', position: 'right' }
  ],
  series: [
    { name: '调用次数', type: 'line', smooth: true, data: callTrendAsc.value.map((p) => p.calls), itemStyle: { color: '#409eff' } },
    { name: 'Token 消耗', type: 'line', smooth: true, yAxisIndex: 1, data: callTrendAsc.value.map((p) => p.tokens), itemStyle: { color: '#e6a23c' } }
  ]
}))

const costTrendOption = computed(() => ({
  tooltip: { trigger: 'axis', valueFormatter: (v: number) => formatCost(v) + ' 元' },
  grid: { left: 60, right: 30, top: 30, bottom: 30 },
  xAxis: { type: 'category', data: costTrendAsc.value.map((p) => p.day), axisLabel: { fontSize: 10 } },
  yAxis: { type: 'value', name: '费用(元)', axisLabel: { fontSize: 10 } },
  series: [
    {
      name: '费用',
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.15 },
      data: costTrendAsc.value.map((p) => Number(p.cost)),
      itemStyle: { color: '#f56c6c' }
    }
  ]
}))

const latencyOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    formatter: (params: any[]) => {
      const p = params[0]
      const row = dashboard.value.latencyByResource[p.dataIndex]
      return `${row.resource}<br/>平均延迟: ${p.value} ms<br/>调用: ${row.calls} 次<br/>失败: ${row.failedCount} 次`
    }
  },
  grid: { left: 50, right: 30, top: 30, bottom: 40 },
  xAxis: { type: 'category', data: dashboard.value.latencyByResource.map((r) => r.resource), axisLabel: { fontSize: 10 } },
  yAxis: { type: 'value', name: 'ms' },
  series: [
    {
      name: '平均延迟',
      type: 'bar',
      data: dashboard.value.latencyByResource.map((r) => Math.round(r.avgDurationMs)),
      itemStyle: { color: '#67c23a' },
      barWidth: '40%'
    }
  ]
}))

function formatNum(n: number) {
  if (n === null || n === undefined) return '0'
  return Number(n).toLocaleString('zh-CN')
}

function formatCost(cost: number) {
  if (cost === null || cost === undefined) return '0.00'
  return Number(cost).toFixed(2)
}

async function loadDashboard() {
  loading.value = true
  try {
    const [db, bd, fc] = await Promise.all([
      getDashboardApi(),
      getCostBudgetApi({}),
      getCostForecastApi()
    ])
    dashboard.value = db.data || ({ ...emptyDashboard } as OpsDashboardVo)
    budget.value = bd.data || ({ ...emptyBudget } as CostBudgetStatus)
    forecast.value = fc.data || ({ ...emptyForecast } as CostForecast)
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped lang="scss">
.card-row {
  margin-bottom: 4px;
}

.metric-card {
  margin-bottom: 16px;
  text-align: center;
}

.metric-value {
  font-size: 26px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.metric-unit {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 400;
  color: #909399;
}

.metric-title {
  margin-top: 6px;
  font-size: 14px;
  color: #606266;
}

.metric-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}

.chart-row {
  margin-bottom: 16px;
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.chart {
  height: 300px;
  width: 100%;
}

.budget-card {
  height: 100%;
}

.forecast-value {
  font-size: 16px;
  font-weight: 700;
  color: #f56c6c;
}

.forecast-note {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
