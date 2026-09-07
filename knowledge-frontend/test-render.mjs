// 临时验证：复刻修复后的 normalizeMarkdown + md.render
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false, breaks: true, linkify: true })

function normalizeMarkdown(content) {
  const lines = content.split('\n')
  let inCodeBlock = false
  return lines
    .map((rawLine) => {
      if (/^\s*(```|~~~)/.test(rawLine)) {
        inCodeBlock = !inCodeBlock
        return rawLine
      }
      if (inCodeBlock) return rawLine
      let line = rawLine
      const codes = []
      line = line.replace(/`[^`\n]+`/g, (m) => {
        codes.push(m)
        return `\u0000${codes.length - 1}\u0000`
      })
      line = line.replace(/^(\s*)(\d+)\s*[.、)）]\s*(?=\S)/, '$1$2. ')
      line = line.replace(/^(\s*)([*+-])(?![\s*])/, '$1$2 ')
      line = line.replace(/([\u4e00-\u9fa5])([A-Za-z0-9])/g, '$1 $2')
      line = line.replace(/([A-Za-z0-9])([\u4e00-\u9fa5])/g, '$1 $2')
      {
        let starIdx = 0
        line = line.replace(/\*\*/g, (_m, off) => {
          starIdx++
          const before = off > 0 ? line[off - 1] : undefined
          const after = line[off + 2]
          if (starIdx % 2 === 1) {
            return before === undefined || /\s/.test(before) ? '**' : ' **'
          }
          return after === undefined || /\s/.test(after) ? '**' : '** '
        })
      }
      line = line.replace(/^(\s*#{1,6})(?=\S)/, '$1 ')
      const starCount = (line.match(/\*/g) || []).length
      if (starCount % 2 === 1 && /\*+\s*$/.test(line)) {
        line = line.replace(/\*+\s*$/, '')
      }
      return line.replace(/\u0000(\d+)\u0000/g, (_m, i) => codes[Number(i)])
    })
    .join('\n')
}

const raw = `用户常将**SAD (ShortAudio Descriptor)**与 LAD 混淆。
####Byte0: CodingType&ChannelCount (编码类型与声道数)
**单行加粗**与中文，中文**加粗**尾部。
* 位置：通常在 EDID 扩展块（VSDB）的Audio Support 字段中。
*长度标识：第1 个Byte 的前3 bit标识为 VSDB。`

console.log('==== 规范化后 ====')
console.log(normalizeMarkdown(raw))
console.log('==== 渲染结果 ====')
console.log(md.render(normalizeMarkdown(raw)))
