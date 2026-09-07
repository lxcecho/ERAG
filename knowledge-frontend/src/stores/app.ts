/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 应用全局状态：侧边栏折叠状态等 UI 配置
 */
export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return { sidebarCollapsed, toggleSidebar }
})
