/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

import type { RouteRecordRaw } from 'vue-router'

/**
 * 静态路由定义
 * - login / 404 为独立全屏页面（hidden，不出现在侧边栏菜单）
 * - 其余为 Layout 容器路由，children 即菜单项
 * - 当 Layout 路由仅含 1 个子路由且父级无 title 时，侧边栏扁平化为顶级菜单项
 */

const Layout = () => import('@/views/layout/index.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', hidden: true }
  },
  {
    path: '/profile',
    component: Layout,
    redirect: '/profile/index',
    children: [
      {
        path: 'index',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心', hidden: true }
      }
    ]
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台', icon: 'Odometer', affix: true }
      }
    ]
  },
  {
    path: '/knowledge',
    component: Layout,
    redirect: '/knowledge/list',
    meta: { title: '知识库管理', icon: 'Collection' },
    children: [
      {
        path: 'list',
        name: 'KnowledgeList',
        component: () => import('@/views/knowledge/index.vue'),
        meta: { title: '知识库', icon: 'Files' }
      },
      {
        path: 'documents',
        name: 'DocumentList',
        component: () => import('@/views/document/index.vue'),
        meta: { title: '文档管理', icon: 'Document' }
      },
      {
        path: 'tasks',
        name: 'TaskList',
        component: () => import('@/views/task/index.vue'),
        meta: { title: '解析任务', icon: 'Loading' }
      }
    ]
  },
  {
    path: '/chat',
    component: Layout,
    redirect: '/chat/index',
    children: [
      {
        path: 'index',
        name: 'Chat',
        component: () => import('@/views/chat/index.vue'),
        meta: { title: 'RAG 对话', icon: 'ChatDotRound' }
      }
    ]
  },
  {
    path: '/prompt',
    component: Layout,
    redirect: '/prompt/index',
    children: [
      {
        path: 'index',
        name: 'Prompt',
        component: () => import('@/views/prompt/index.vue'),
        meta: { title: 'Prompt模板', icon: 'EditPen' }
      }
    ]
  },
  {
    path: '/agent',
    component: Layout,
    redirect: '/agent/list',
    meta: { title: 'Agent 中心', icon: 'Operation' },
    children: [
      {
        path: 'list',
        name: 'AgentList',
        component: () => import('@/views/agent/list/index.vue'),
        meta: { title: 'Agent 列表', icon: 'Cpu' }
      },
      {
        path: 'workflow',
        name: 'AgentWorkflow',
        component: () => import('@/views/agent/workflow/index.vue'),
        meta: { title: 'Workflow 配置', icon: 'Share' }
      },
      {
        path: 'custom',
        name: 'AgentCustom',
        component: () => import('@/views/agent/custom/index.vue'),
        meta: { title: '自定义 Agent', icon: 'MagicStick' }
      },
      {
        path: 'custom/:id',
        name: 'AgentCustomDetail',
        component: () => import('@/views/agent/custom/detail.vue'),
        meta: { title: 'Agent 对话', hidden: true, activeMenu: '/agent/custom' }
      },
      {
        path: 'tasks',
        name: 'AgentTasks',
        component: () => import('@/views/agent/tasks/index.vue'),
        meta: { title: '任务执行记录', icon: 'List' }
      },
      {
        path: 'detail',
        name: 'AgentDetail',
        component: () => import('@/views/agent/detail/index.vue'),
        meta: { title: '执行过程查看', hidden: true, activeMenu: '/agent/tasks' }
      }
    ]
  },
  {
    path: '/aiops',
    component: Layout,
    redirect: '/aiops/dashboard',
    meta: { title: 'AI 运营', icon: 'DataAnalysis' },
    children: [
      {
        path: 'dashboard',
        name: 'OpsDashboard',
        component: () => import('@/views/aiops/dashboard/index.vue'),
        meta: { title: '运维看板', icon: 'Monitor' }
      },
      {
        path: 'trace',
        name: 'OpsTrace',
        component: () => import('@/views/aiops/trace/index.vue'),
        meta: { title: '链路追踪', icon: 'Connection' }
      },
      {
        path: 'alert',
        name: 'OpsAlert',
        component: () => import('@/views/aiops/alert/index.vue'),
        meta: { title: '告警规则', icon: 'Bell' }
      },
      {
        path: 'log',
        name: 'AiCallLog',
        component: () => import('@/views/aiops/log/index.vue'),
        meta: { title: '调用日志', icon: 'List' }
      },
      {
        path: 'stats',
        name: 'AiCallStats',
        component: () => import('@/views/aiops/stats/index.vue'),
        meta: { title: '调用统计', icon: 'TrendCharts' }
      }
    ]
  },
  {
    path: '/system',
    component: Layout,
    redirect: '/system/oper-log',
    meta: { title: '系统管理', icon: 'Setting' },
    children: [
      {
        path: 'oper-log',
        name: 'OperLog',
        component: () => import('@/views/operlog/index.vue'),
        meta: { title: '操作日志', icon: 'Document' }
      }
    ]
  }
]

export default routes
