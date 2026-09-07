/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 权限指令 v-permission="'system:user:add'" 或 v-permission="['a','b']"
 * 无权限时直接移除该 DOM 节点（按钮级权限控制）
 */
export const permissionDirective: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const perms = userStore.permissions
    const required = Array.isArray(binding.value) ? binding.value : [binding.value]
    // 拥有 *:*:* 超管权限或命中任一所需权限即放行
    const hasPerm = perms.includes('*:*:*') || required.some((p) => perms.includes(p))
    if (!hasPerm) {
      el.parentNode?.removeChild(el)
    }
  }
}
