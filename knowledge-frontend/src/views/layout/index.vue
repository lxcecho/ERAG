<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <div class="app-wrapper">
    <Sidebar class="sidebar-container" :class="{ collapsed: appStore.sidebarCollapsed }" />
    <div class="main-container">
      <Navbar />
      <AppMain />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import Sidebar from './components/Sidebar.vue'
import Navbar from './components/Navbar.vue'
import AppMain from './components/AppMain.vue'

const appStore = useAppStore()
</script>

<style scoped lang="scss">
.app-wrapper {
  display: flex;
  height: 100%;
  width: 100%;
}

.sidebar-container {
  width: var(--sidebar-width);
  transition: width 0.28s;
  flex-shrink: 0;
  background-color: var(--sidebar-bg);
  height: 100%;

  &.collapsed {
    width: var(--sidebar-collapsed-width);
  }
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}
</style>
