import { Marked } from 'marked'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import java from 'highlight.js/lib/languages/java'
import python from 'highlight.js/lib/languages/python'
import go from 'highlight.js/lib/languages/go'
import rust from 'highlight.js/lib/languages/rust'
import bash from 'highlight.js/lib/languages/bash'
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import markdown from 'highlight.js/lib/languages/markdown'
import yaml from 'highlight.js/lib/languages/yaml'
import sql from 'highlight.js/lib/languages/sql'
import plaintext from 'highlight.js/lib/languages/plaintext'
import dockerfile from 'highlight.js/lib/languages/dockerfile'
import properties from 'highlight.js/lib/languages/properties'

import type { PinnedRepos } from '../types/chat'

// Register commonly-used languages only; keeps bundle size down.
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('java', java)
hljs.registerLanguage('python', python)
hljs.registerLanguage('py', python)
hljs.registerLanguage('go', go)
hljs.registerLanguage('rust', rust)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('sh', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('json', json)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('markdown', markdown)
hljs.registerLanguage('md', markdown)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('yml', yaml)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('plaintext', plaintext)
hljs.registerLanguage('text', plaintext)
hljs.registerLanguage('dockerfile', dockerfile)
hljs.registerLanguage('properties', properties)

export { hljs }

const markedInstance = new Marked({ breaks: true, gfm: true })

/**
 * Re-percent-encode the `Signature` query param of pre-signed URLs
 * (Aliyun OSS / dashscope / S3-style). base64 signatures contain `+` and `/`;
 * if they reach the browser raw, `+` is decoded to space and OSS rejects
 * the request with SignatureDoesNotMatch. We encode the raw signature
 * substring directly (NOT via URLSearchParams, which would conflate a
 * literal `+` with a space). Idempotent: a value already carrying `%XX`
 * escapes is treated as correctly encoded and left untouched.
 */
export function normalizeSignedUrl(raw: string): string {
  if (!raw || raw.indexOf('Signature=') === -1) return raw
  return raw.replace(/([?&]Signature=)([^&]*)/g, (_m, prefix: string, value: string) => {
    if (/%[0-9A-Fa-f]{2}/.test(value)) return prefix + value // already encoded
    const enc = value.replace(/\+/g, '%2B').replace(/\//g, '%2F').replace(/=/g, '%3D')
    return prefix + enc
  })
}

function escapeAttr(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

markedInstance.use({
  renderer: {
    // Normalize signed image URLs before the browser ever fetches them.
    image(token: { href: string; title?: string | null; text: string }) {
      const src = escapeAttr(normalizeSignedUrl(token.href || ''))
      const alt = escapeAttr(token.text || '')
      const title = token.title ? ` title="${escapeAttr(token.title)}"` : ''
      return `<img src="${src}" alt="${alt}"${title}>`
    },
    code({ text, lang }: { text: string; lang?: string }) {
      const escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      if (lang && hljs.getLanguage(lang)) {
        try {
          const result = hljs.highlight(text, { language: lang, ignoreIllegals: true })
          return `<pre><code class="hljs language-${lang}">${result.value}</code></pre>`
        } catch {
          // fall through to plain
        }
      }
      return `<pre><code class="hljs">${escaped}</code></pre>`
    },
  },
})

export function renderMarkdown(text: string): string {
  return markedInstance.parse(text, { async: false }) as string
}

// -------- Pinned repos detection (special markdown pattern for GitHub demo) --------
const LANG_COLORS: Record<string, string> = {
  'Java': '#b07219', 'JavaScript': '#f1e05a', 'TypeScript': '#3178c6',
  'Python': '#3572A5', 'Go': '#00ADD8', 'Rust': '#dea584',
  'Ruby': '#701516', 'C++': '#f34b7d', 'C': '#555555',
  'Shell': '#89e051', 'HTML': '#e34c26', 'CSS': '#563d7c',
  'Kotlin': '#A97BFF', 'Scala': '#c22d40', 'PHP': '#4F5D95',
}

export function parsePinnedRepos(text: string): PinnedRepos | null {
  const headerMatch = text.match(/##\s*📌\s*@(\S+)\s*的置顶仓库[^\n]*\n/)
  if (!headerMatch) return null
  const username = headerMatch[1]
  const repos: PinnedRepos['repos'] = []
  const repoRegex = /###\s*\[([^\]]+)\]\(([^)]+)\)\s*\n?([\s\S]*?)\n?`Language:\s*([^`]*)`\s*\|\s*⭐\s*(\d+)\s*\|\s*🔀\s*(\d+)/g
  let m
  while ((m = repoRegex.exec(text)) !== null) {
    repos.push({ name: m[1], url: m[2], desc: m[3].trim(), lang: m[4], stars: m[5], forks: m[6] })
  }
  if (repos.length === 0) return null
  return { username, repos }
}

export function renderPinnedRepos(data: PinnedRepos): string {
  let html = `<div class="pinned-repos-header">📌 @${data.username} 的置顶仓库 (${data.repos.length} 个)</div>`
  html += '<div class="repo-grid">'
  for (const r of data.repos) {
    const color = LANG_COLORS[r.lang] || '#8b949e'
    html += `<div class="repo-card">`
    html += `<div class="repo-name"><a href="${r.url}" target="_blank">${r.name}</a></div>`
    if (r.desc) html += `<div class="repo-desc">${r.desc}</div>`
    html += `<div class="repo-meta">`
    html += `<span><span class="lang-dot" style="background:${color}"></span>${r.lang}</span>`
    html += `<span>⭐ ${r.stars}</span>`
    html += `<span>🔀 ${r.forks}</span>`
    html += `</div></div>`
  }
  html += '</div>'
  return html
}

// -------- Auto-embed bare image URLs inside a rendered DOM fragment --------
const IMG_EXT = /\.(png|jpe?g|gif|webp|svg|bmp|ico)(\?[^\s]*)?$/i
const URL_REGEX = /(https?:\/\/[^\s<]+)/g

export function embedImageUrls(root: HTMLElement, onImageClick?: (src: string) => void) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  const textNodes: Text[] = []
  while (walker.nextNode()) textNodes.push(walker.currentNode as Text)

  for (const node of textNodes) {
    const parent = node.parentElement
    if (!parent || parent.tagName === 'A' || parent.tagName === 'IMG') continue
    let hasImage = false
    URL_REGEX.lastIndex = 0
    let m: RegExpExecArray | null
    while ((m = URL_REGEX.exec(node.textContent || '')) !== null) {
      if (IMG_EXT.test(m[1])) { hasImage = true; break }
    }
    if (!hasImage) continue

    const frag = document.createDocumentFragment()
    const text = node.textContent || ''
    let lastIdx = 0
    URL_REGEX.lastIndex = 0
    while ((m = URL_REGEX.exec(text)) !== null) {
      const url = m[1]
      if (!IMG_EXT.test(url)) continue
      if (m.index > lastIdx) frag.appendChild(document.createTextNode(text.slice(lastIdx, m.index)))
      const img = document.createElement('img')
      const normalized = normalizeSignedUrl(url)
      img.src = normalized
      img.classList.add('embedded-image')
      if (onImageClick) {
        img.addEventListener('click', () => onImageClick(normalized))
      }
      frag.appendChild(img)
      lastIdx = m.index + url.length
    }
    if (lastIdx < text.length) frag.appendChild(document.createTextNode(text.slice(lastIdx)))
    node.parentNode?.replaceChild(frag, node)
  }
}

// Bind click handler to all <img> inside a rendered markdown container.
export function bindImageClicks(root: HTMLElement, onImageClick: (src: string) => void) {
  root.querySelectorAll<HTMLImageElement>('.md-content img').forEach((img) => {
    img.style.cursor = 'zoom-in'
    img.addEventListener('click', () => onImageClick(img.src))
  })
}
