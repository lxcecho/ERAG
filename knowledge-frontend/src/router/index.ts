/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/stores/user'
import staticRoutes from './modules/static'

/**
 * 路由设计 + 全局前置守卫
 * - 白名单（/login、/404）无需鉴权
 * - 已登录访问 /login 自动跳首页
 * - 已登录但未加载用户信息时，首次进入拉取用户信息；失败则强制登出回登录页
 */
const routes: RouteRecordRaw[] = [
  ...staticRoutes,
  // 兜底：未匹配路由重定向到 404
  { path: '/:pathMatch(.*)*', redirect: '/404' }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  // 切换路由时滚动到顶部
  scrollBehavior: () => ({ top: 0 })
})

const whiteList = ['/login', '/404']

router.beforeEach(async (to, _from, next) => {
  // 设置页面标题
  document.title = (to.meta?.title as string) ? `${to.meta.title} - AI 知识库助手` : 'AI 知识库助手'

  const token = getToken()

  if (token) {
    if (to.path === '/login') {
      next({ path: '/' })
      return
    }
    const userStore = useUserStore()
    if (!userStore.userInfo) {
      try {
        await userStore.fetchUserInfo()
        next()
      } catch {
        userStore.resetState()
        next(`/login?redirect=${to.path}`)
      }
    } else {
      next()
    }
  } else {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next(`/login?redirect=${to.path}`)
    }
  }
})

export default router
