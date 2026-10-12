<script setup lang="ts">
import { computed } from 'vue'
import type { FileContent } from '../types/chat'
import { hljs } from '../utils/markdown'
import { emitImageClick } from '../composables/useLightbox'

const props = defineProps<{
  file: FileContent | null
  filename: string
  path: string
  open: boolean
}>()

defineEmits<{ close: [] }>()

function escapeHtml(s: string) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

const highlighted = computed(() => {
  if (!props.file?.content) return ''
  const lang = props.file.language
  if (lang && lang !== 'plaintext' && hljs.getLanguage(lang)) {
    try {
      return hljs.highlight(props.file.content, { language: lang, ignoreIllegals: true }).value
    } catch {
      // fall through
    }
  }
  return escapeHtml(props.file.content)
})

const isImage = computed(() => /\.(png|jpe?g|gif|webp|svg|bmp|ico)$/i.test(props.filename))

const imageSrc = computed(() =>
  isImage.value ? `/api/workspace/image?path=${encodeURIComponent(props.path)}` : ''
)
</script>

<template>
  <div class="file-preview" :class="{ open }">
    <div class="file-preview-header">
      <span class="filename">{{ filename }}</span>
      <button @click="$emit('close')">✕</button>
    </div>
    <div class="file-preview-content">
      <div v-if="isImage" class="image-preview">
        <img :src="imageSrc" :alt="filename" @click="emitImageClick(imageSrc)" />
      </div>
      <pre v-else-if="file"><code class="hljs" v-html="highlighted"></code></pre>
    </div>
  </div>
</template>

<style scoped>
.file-preview {
  width: 0; min-width: 0; background: var(--bg); border-left: 1px solid var(--border);
  display: flex; flex-direction: column; overflow: hidden;
  transition: width 0.2s, min-width 0.2s;
}
.file-preview.open { width: 480px; min-width: 320px; }
.file-preview-header {
  padding: 8px 14px; font-size: 12px; font-weight: 600; color: var(--text-muted);
  border-bottom: 1px solid var(--border); display: flex; align-items: center;
  justify-content: space-between; background: var(--code-bg); white-space: nowrap;
}
.file-preview-header .filename { overflow: hidden; text-overflow: ellipsis; }
.file-preview-header button { background: none; border: none; cursor: pointer; font-size: 16px; color: var(--text-muted); padding: 2px 8px; border-radius: 4px; }
.file-preview-header button:hover { background: rgba(0,0,0,0.06); }
.file-preview-content { flex: 1; overflow: auto; }
.file-preview-content pre { margin: 0; padding: 12px 16px; font-size: 12px; line-height: 1.6; tab-size: 4; white-space: pre; width: max-content; min-width: 100%; }
.file-preview-content code { font-family: var(--mono); }
.image-preview { display: flex; align-items: center; justify-content: center; padding: 16px; }
.image-preview img { max-width: 100%; max-height: 80vh; border-radius: 8px; cursor: zoom-in; box-shadow: 0 2px 12px rgba(0,0,0,0.15); }
</style>
