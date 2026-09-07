<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <div class="page-wrapper">
    <el-card shadow="never">
      <template v-if="title || $slots.extra" #header>
        <div class="page-header">
          <span class="page-title">{{ title }}</span>
          <div class="page-extra">
            <slot name="extra" />
          </div>
        </div>
      </template>
      <slot />
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 基础布局组件：页面容器
 * 统一页面外层卡片 + 标题 + 右侧操作区，所有业务页面套用此组件保持视觉一致。
 */
defineProps<{ title?: string }>()
</script>

<style scoped lang="scss">
.page-wrapper {
  padding: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
</style>
