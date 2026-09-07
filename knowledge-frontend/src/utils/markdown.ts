/**
 * @since: 2026-08-02
 * @author: lxcechoo@gmail.com
 */

import MarkdownIt from 'markdown-it'

/**
 * 全局共享的 markdown-it 实例。
 * - html:false  禁止内联 HTML，避免 XSS 注入
 * - breaks:true 单个换行符转为 <br>，贴合聊天/报告换行习惯
 * - linkify:true 自动识别裸链接为 <a>
 *
 * 统一在此处配置，供 MarkdownView 组件、chat、agent 详情、prompt 测试等复用，
 * 避免每个页面各自 new MarkdownIt 造成配置漂移。
 */
const md = new MarkdownIt({ html: false, breaks: true, linkify: true })

/**
 * LLM 中文输出的 markdown 规范化（渲染前清洗）。
 * <p>大模型生成的 markdown 常存在不规范书写，导致渲染后排版混乱，常见缺陷：
 * <ul>
 *   <li>标题/列表项与正文粘连不换行：如 {@code 概念：### 1. xxx}、{@code AAC*100:DTS}，
 *       先补 {@code \n} 使其成为合法标题/列表项（markdown-it 要求独占一行）</li>
 *   <li>有序列表序号后缺空格：{@code 1.xxx} → {@code 1. xxx}（否则 markdown-it 不识别为列表）</li>
 *   <li>无序列表符号后缺空格：{@code *xxx} / {@code -xxx} → {@code * xxx}</li>
 *   <li>中英文/数字直接粘连：{@code EDID是一种} → {@code EDID 是一种}（pangu 风格，显著提升可读性）</li>
 *   <li>行尾孤立 {@code *}：如 {@code ...version 2*} 不成对强调，会被 markdown-it 吞掉后续内容</li>
 * </ul>
 * 处理时跳过代码块（``` 围栏）与行内代码（{@code `...`}）内容，避免误伤。
 */
function normalizeMarkdown(content: string): string {
  // 预保护行内代码片段：后续所有规则均不会改动反引号内内容
  const codes: string[] = []
  let text = content.replace(/`[^`\n]+`/g, (m) => {
    codes.push(m)
    return `\u0000${codes.length - 1}\u0000`
  })

  // 预补换行（LLM 常把标题/列表项直接粘在正文后面，不换行则 markdown-it 无法识别）：
  // 1) 段内标题：中文句读/引号/右括号/表格分隔符后紧跟 ###/#### → 补 \n 使其成为合法标题。
  //    前置限定为中文标点/引号等，避免误伤 "C# 编程"、"#tag" 等正常内容。
  text = text.replace(/([。！？：；，、）】」』""''|])(#{1,6})/g, '$1\n$2')
  // 2) 粘连列表项：非空白/星号字符后紧跟 * 且 * 后随空格/数字/中文 → 补 \n 使其成为列表项。
  //    如 "AAC*100:DTS" → "AAC\n*100:DTS"、"II)* 011" → "II)\n* 011"；
  //    "Description*（"、"2*3" 等不在前置条件或后随字符范围内，不会被误拆。
  text = text.replace(/([^\s*])(\*)(?=[\s\d\u4e00-\u9fa5])/g, '$1\n$2')

  const lines = text.split('\n')
  let inCodeBlock = false
  const body = lines
    .map((rawLine) => {
      // 围栏代码块整体跳过
      if (/^\s*(```|~~~)/.test(rawLine)) {
        inCodeBlock = !inCodeBlock
        return rawLine
      }
      if (inCodeBlock) return rawLine

      let line = rawLine
      // 1) 行首有序列表：1.xxx / 1、xxx / 1)xxx → 1. xxx
      line = line.replace(/^(\s*)(\d+)\s*[.、)）]\s*(?=\S)/, '$1$2. ')
      // 2) 行首无序列表：*xxx / -xxx → * xxx / - xxx（保留原符号）
      //    (?![\s*])：排除"* 位置"（已带空格）与行首 **加粗**（避免把 * 误当列表符）
      line = line.replace(/^(\s*)([*+-])(?![\s*])/, '$1$2 ')

      // 3) 中英文 / 中数之间补空格（pangu 风格）
      line = line.replace(/([\u4e00-\u9fa5])([A-Za-z0-9])/g, '$1 $2')
      line = line.replace(/([A-Za-z0-9])([\u4e00-\u9fa5])/g, '$1 $2')

      // 4) ** 加粗标记与中文的边界补空格。
      //    markdown-it 要求：开始 ** 前为空白/行首，闭合 ** 后为空白/行尾；
      //    LLM 中文输出中 "**" 常紧贴中文（如"将**SAD**与"），此时加粗不渲染、符号残留。
      //    按成对交替处理：奇数个 ** 补前空格（变合法开始），偶数个补后空格（变合法闭合）；
      //    已处于边界（前/后为空白或行首行尾）时不重复补。
      {
        let starIdx = 0
        line = line.replace(/\*\*/g, (_m, off: number) => {
          starIdx++
          const before = off > 0 ? line[off - 1] : undefined
          const after = line[off + 2]
          if (starIdx % 2 === 1) {
            return before === undefined || /\s/.test(before) ? '**' : ' **'
          }
          return after === undefined || /\s/.test(after) ? '**' : '** '
        })
      }
      // 5) 标题 # 后补空格：####Byte0 → #### Byte0（markdown-it 要求 # 后跟空格才识别为标题）。
      //    后随须为非空白且非 #：若已形如 "### 1."（已带空格）则保持原样，
      //    避免贪婪回溯把 "###" 误拆成 "## #"。
      line = line.replace(/^(\s*#{1,6})(?=[^\s#])/, '$1 ')

      // 6) 行尾孤立 *（该行 * 数量为奇数，即强调未闭合）：去掉行尾 *
      const starCount = (line.match(/\*/g) || []).length
      if (starCount % 2 === 1 && /\*+\s*$/.test(line)) {
        line = line.replace(/\*+\s*$/, '')
      }

      return line
    })
    .join('\n')

  // 恢复行内代码
  return body.replace(/\u0000(\d+)\u0000/g, (_m, i) => codes[Number(i)])
}

/**
 * 将 markdown 文本渲染为 HTML 字符串。
 * 先经 {@link normalizeMarkdown} 规范化 LLM 输出，再交给 markdown-it 渲染。
 * 空值返回空串，配合 v-html 使用时不会注入多余节点。
 */
export function renderMarkdown(content: string | null | undefined): string {
  if (!content) return ''
  return md.render(normalizeMarkdown(content))
}

export default md
