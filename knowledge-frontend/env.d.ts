/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

/// <reference types="vite/client" />
/// <reference types="element-plus/global" />

// 让 TS 识别 .vue 文件
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>
  export default component
}

// 环境变量类型
interface ImportMetaEnv {
  readonly VITE_APP_BASE_API: string
  readonly VITE_APP_API_TARGET: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
