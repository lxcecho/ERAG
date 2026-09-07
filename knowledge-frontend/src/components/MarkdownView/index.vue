<!--
 * @since: 2026-08-02
 * @author: lxcechoo@gmail.com
-->

<template>
  <!--
    通用 Markdown 渲染容器。
    使用 v-html 输出 renderMarkdown(content) 的结果，
    样式通过 :deep() 穿透到 v-html 注入的子元素。
    html:false 已在 utils/markdown.ts 中禁用内联 HTML，规避 XSS。
  -->
  <div class="markdown-view" v-html="html"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{
  /** 待渲染的 markdown 原文 */
  content?: string | null
}>()

const html = computed(() => renderMarkdown(props.content))
</script>

<style scoped lang="scss">
.markdown-view {
  font-size: 14px;
  line-height: 1.75;
  word-break: break-word;
  color: var(--el-text-color-regular);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4),
  :deep(h5),
  :deep(h6) {
    margin: 14px 0 8px;
    font-weight: 600;
    line-height: 1.4;
    color: var(--el-text-color-primary);
  }
  :deep(h1) { font-size: 1.4em; }
  :deep(h2) { font-size: 1.25em; }
  :deep(h3) { font-size: 1.1em; }
  :deep(h4) { font-size: 1em; }

  :deep(p) { margin: 8px 0; }

  :deep(ul),
  :deep(ol) {
    margin: 8px 0;
    padding-left: 24px;
  }
  :deep(li) { margin: 4px 0; }

  :deep(code) {
    background: var(--el-fill-color-light);
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 0.9em;
    font-family: 'Consolas', 'Monaco', monospace;
  }

  :deep(pre) {
    background: #1e1e1e;
    color: #d4d4d4;
    padding: 12px;
    border-radius: 6px;
    overflow-x: auto;
    margin: 8px 0;

    code {
      background: none;
      padding: 0;
      color: inherit;
    }
  }

  :deep(blockquote) {
    margin: 8px 0;
    padding: 4px 12px;
    border-left: 4px solid var(--el-color-primary);
    background: var(--el-fill-color-light);
    color: var(--el-text-color-secondary);
  }

  :deep(table) {
    border-collapse: collapse;
    margin: 8px 0;
    width: 100%;

    th,
    td {
      border: 1px solid var(--el-border-color);
      padding: 6px 10px;
      text-align: left;
    }
    th {
      background: var(--el-fill-color-light);
      font-weight: 600;
    }
  }

  :deep(a) {
    color: var(--el-color-primary);
    text-decoration: none;
    &:hover {
      text-decoration: underline;
    }
  }

  :deep(strong) { font-weight: 600; }

  :deep(hr) {
    border: none;
    border-top: 1px solid var(--el-border-color);
    margin: 10px 0;
  }

  :deep(img) {
    max-width: 100%;
    border-radius: 4px;
  }
}
</style>
