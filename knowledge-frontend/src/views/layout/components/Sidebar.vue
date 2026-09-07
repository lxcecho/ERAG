<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <div class="sidebar">
    <!-- Logo 区域 -->
    <div class="logo">
      <el-icon :size="28" color="#409EFF"><Cpu /></el-icon>
      <span v-show="!appStore.sidebarCollapsed" class="logo-title">AI 知识库助手</span>
    </div>

    <el-scrollbar class="menu-scroll">
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.sidebarCollapsed"
        :collapse-transition="false"
        background-color="var(--sidebar-bg)"
        text-color="var(--sidebar-text)"
        active-text-color="var(--sidebar-active-text)"
        router
      >
        <template v-for="route in menuRoutes" :key="route.path">
          <!-- 单子路由：扁平化为顶级菜单项 -->
          <el-menu-item v-if="isSingleChild(route)" :index="resolvePath(route, singleChild(route)!)">
            <el-icon v-if="menuIcon(route)"><component :is="menuIcon(route)" /></el-icon>
            <template #title>{{ menuTitle(route) }}</template>
          </el-menu-item>

          <!-- 多子路由：渲染为子菜单 -->
          <el-sub-menu v-else :index="route.path">
            <template #title>
              <el-icon v-if="route.meta?.icon"><component :is="route.meta.icon" /></el-icon>
              <span>{{ route.meta?.title }}</span>
            </template>
            <el-menu-item
              v-for="child in visibleChildren(route)"
              :key="resolvePath(route, child)"
              :index="resolvePath(route, child)"
            >
              <el-icon v-if="child.meta?.icon"><component :is="child.meta.icon" /></el-icon>
              <template #title>{{ child.meta?.title }}</template>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter, type RouteRecordRaw } from 'vue-router'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

// 仅渲染 Layout 容器路由（含 children 且非 hidden）
const menuRoutes = computed(() =>
  router.options.routes.filter(
    (r) => !r.meta?.hidden && r.children && r.children.length > 0 && r.path !== '/:pathMatch(.*)*'
  )
)

const activeMenu = computed(() => (route.meta?.activeMenu as string) || route.path)

function visibleChildren(route: RouteRecordRaw): RouteRecordRaw[] {
  return (route.children || []).filter((c) => !c.meta?.hidden)
}

/** 是否仅含一个子路由且父级无标题（扁平化条件） */
function isSingleChild(route: RouteRecordRaw): boolean {
  return visibleChildren(route).length === 1 && !route.meta?.title
}

function singleChild(route: RouteRecordRaw): RouteRecordRaw | undefined {
  return visibleChildren(route)[0]
}

function menuIcon(route: RouteRecordRaw): string | undefined {
  if (isSingleChild(route)) {
    return singleChild(route)?.meta?.icon as string | undefined
  }
  return route.meta?.icon as string | undefined
}

function menuTitle(route: RouteRecordRaw): string | undefined {
  if (isSingleChild(route)) {
    return singleChild(route)?.meta?.title as string | undefined
  }
  return route.meta?.title as string | undefined
}

/** 拼接父子路由路径 */
function resolvePath(parent: RouteRecordRaw, child: RouteRecordRaw): string {
  const childPath = child.path
  if (childPath.startsWith('/')) return childPath
  return `${parent.path.replace(/\/$/, '')}/${childPath}`
}
</script>

<style scoped lang="scss">
.sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.logo {
  height: var(--navbar-height);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #fff;
  overflow: hidden;
  flex-shrink: 0;

  .logo-title {
    font-size: 16px;
    font-weight: 600;
    white-space: nowrap;
  }
}

.menu-scroll {
  flex: 1;
}

// el-menu 折叠态宽度
:deep(.el-menu) {
  border-right: none;
}
</style>
