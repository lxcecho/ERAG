<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="工作台">
    <el-row :gutter="16">
      <el-col v-for="card in cards" :key="card.title" :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card-box" :body-style="{ padding: '0' }">
          <div class="stat-card" @click="goStatPage(card.path)">
            <el-icon :size="40" :color="card.color"><component :is="card.icon" /></el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-title">{{ card.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="welcome-card">
      <h3>欢迎使用 AI 知识库助手</h3>
      <p>这是一个企业级 RAG 知识库系统，支持文档上传、智能切片、向量检索与增强问答。</p>
    </el-card>
  </PageWrapper>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageWrapper from '@/components/PageWrapper/index.vue'
import { getDashboardStatsApi } from '@/api/dashboard'

const router = useRouter()

const stats = ref<DashboardStats>({ kbCount: 0, docCount: 0, chunkCount: 0, todayChatCount: 0 })

const cards = computed(() => [
  { title: '知识库', value: stats.value.kbCount, icon: 'Collection', color: '#409eff', path: '/knowledge/list' },
  { title: '文档数', value: stats.value.docCount, icon: 'Document', color: '#67c23a', path: '/knowledge/documents' },
  { title: '切片数', value: stats.value.chunkCount, icon: 'Files', color: '#e6a23c', path: '/knowledge/documents' },
  { title: '今日对话', value: stats.value.todayChatCount, icon: 'ChatDotRound', color: '#f56c6c', path: '/chat/index' }
])

/** 点击统计卡片跳转到对应业务页 */
function goStatPage(path: string) {
  router.push(path)
}

onMounted(async () => {
  try {
    const res = await getDashboardStatsApi()
    stats.value = res.data
  } catch {
    // 错误提示已由 request 拦截器统一处理
  }
})
</script>

<style scoped lang="scss">
.stat-card-box {
  margin-bottom: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background: var(--el-fill-color-light);
  }
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #303133;
}

.stat-title {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}

.welcome-card {
  h3 {
    margin-bottom: 8px;
    color: #303133;
  }
  p {
    color: #606266;
    line-height: 1.6;
  }
}
</style>
