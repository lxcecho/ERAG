/**
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
 */

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// Vite 配置：别名、开发代理、构建分包
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  return {
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src')
      }
    },
    plugins: [vue()],
    server: {
      port: 5173,
      host: '0.0.0.0',
      open: true,
      proxy: {
        // 前端 /api 请求代理到后端 Spring Boot 服务
        '/api': {
          target: env.VITE_APP_API_TARGET || 'http://localhost:8080',
          changeOrigin: true
        }
      }
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia'],
            'element-plus': ['element-plus', '@element-plus/icons-vue']
          }
        }
      }
    }
  }
})
