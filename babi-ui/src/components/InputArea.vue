<script setup lang="ts">
import { ref, nextTick, watch, onBeforeUnmount } from 'vue'
import { fetchWorkspaceFiles } from '../api/client'

defineProps<{ isStreaming: boolean }>()
const emit = defineEmits<{
  send: [message: string]
  interrupt: []
}>()

const text = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)

// ---- @-mention file completion ----
const mentionOpen = ref(false)
const mentionItems = ref<string[]>([])
const mentionActive = ref(0)
const mentionLoading = ref(false)
let mentionStart = -1 // index of the triggering '@' in text
let mentionTimer: number | undefined
let mentionSeq = 0 // guards against out-of-order responses

function closeMention() {
  mentionOpen.value = false
  mentionItems.value = []
  mentionActive.value = 0
  mentionStart = -1
}

/** Detect an '@token' immediately before the caret and open the popup. */
function detectMention() {
  const el = textareaRef.value
  if (!el) return closeMention()
  const caret = el.selectionStart ?? 0
  const match = /@([^\s@]*)$/.exec(text.value.slice(0, caret))
  if (!match) return closeMention()
  mentionStart = caret - match[0].length
  mentionActive.value = 0
  mentionOpen.value = true
  const query = match[1]
  clearTimeout(mentionTimer)
  mentionTimer = window.setTimeout(() => loadMentionItems(query), 150)
}

async function loadMentionItems(query: string) {
  const seq = ++mentionSeq
  mentionLoading.value = true
  try {
    const items = await fetchWorkspaceFiles(query)
    if (seq === mentionSeq && mentionOpen.value) mentionItems.value = items
  } catch {
    if (seq === mentionSeq) mentionItems.value = []
  } finally {
    if (seq === mentionSeq) mentionLoading.value = false
  }
}

/** Replace '@token' with the selected workspace-relative path. */
function applyMention(path: string) {
  const el = textareaRef.value
  if (!el || mentionStart < 0) return
  const start = mentionStart
  const caret = el.selectionStart ?? 0
  const insert = '@' + path + ' '
  text.value = text.value.slice(0, start) + insert + text.value.slice(caret)
  closeMention()
  nextTick(() => {
    el.focus()
    const pos = start + insert.length
    el.setSelectionRange(pos, pos)
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 120) + 'px'
  })
}

watch(mentionActive, () => {
  nextTick(() => {
    document.querySelector('.mention-item.active')?.scrollIntoView({ block: 'nearest' })
  })
})

onBeforeUnmount(() => clearTimeout(mentionTimer))

function baseName(path: string): string {
  return path.substring(path.lastIndexOf('/') + 1)
}

function dirName(path: string): string {
  const idx = path.lastIndexOf('/')
  return idx > 0 ? path.slice(0, idx) : ''
}

function handleSend() {
  if (!text.value.trim()) return
  closeMention()
  if (emit) {
    emit('send', text.value)
    text.value = ''
    // Reset textarea height
    nextTick(() => {
      if (textareaRef.value) textareaRef.value.style.height = 'auto'
    })
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (mentionOpen.value) {
    if (e.key === 'Escape') {
      e.preventDefault()
      closeMention()
      return
    }
    if (mentionItems.value.length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        mentionActive.value = (mentionActive.value + 1) % mentionItems.value.length
        return
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault()
        mentionActive.value =
          (mentionActive.value - 1 + mentionItems.value.length) % mentionItems.value.length
        return
      }
      if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault()
        applyMention(mentionItems.value[mentionActive.value])
        return
      }
    }
  }
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function onInput(e: Event) {
  autoResize(e)
  detectMention()
}

function autoResize(e: Event) {
  const el = e.target as HTMLTextAreaElement
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}
</script>

<template>
  <div class="input-area">
    <div class="input-inner">
      <div v-if="mentionOpen" class="mention-menu">
        <div v-if="mentionLoading && mentionItems.length === 0" class="mention-empty">搜索中...</div>
        <div v-else-if="mentionItems.length === 0" class="mention-empty">无匹配文件</div>
        <div
          v-for="(path, i) in mentionItems"
          :key="path"
          class="mention-item"
          :class="{ active: i === mentionActive }"
          @mousedown.prevent="applyMention(path)"
          @mouseenter="mentionActive = i"
        >
          <span class="mention-name">{{ baseName(path) }}</span>
          <span class="mention-dir">{{ dirName(path) }}</span>
        </div>
      </div>
      <textarea
        ref="textareaRef"
        v-model="text"
        :placeholder="isStreaming ? '等待回复中...' : '输入消息，Enter 发送，Shift+Enter 换行，@ 引用工作区文件...'"
        rows="1"
        @keydown="handleKeydown"
        @input="onInput"
        @click="detectMention"
        @blur="closeMention"
      />
      <button
        v-if="!isStreaming"
        :disabled="!text.trim()"
        @click="handleSend"
      >发送</button>
      <button v-else class="stop-btn" @click="emit('interrupt')">停止</button>
    </div>
  </div>
</template>

<style scoped>
.input-area { padding: 16px 24px; display: flex; justify-content: center; }
.input-inner { max-width: 1100px; width: 100%; position: relative; display: flex; align-items: flex-end; }
textarea {
  flex: 1; border: 1px solid var(--border); border-radius: 8px;
  padding: 12px 80px 12px 14px; font-size: 16px; font-family: inherit;
  resize: none; outline: none; min-height: 48px; max-height: 120px;
  line-height: 1.5; background: var(--bg); color: var(--text);
}
textarea:focus { border-color: var(--accent); }
button {
  position: absolute; right: 6px; bottom: 6px; background: var(--accent);
  color: #fff; border: none; border-radius: 6px; padding: 8px 16px;
  font-size: 14px; cursor: pointer; height: 36px;
}
button:hover { opacity: 0.9; }
button:disabled { background: #ccc; cursor: not-allowed; }
.stop-btn { background: #ea4335; }
.stop-btn:hover { background: #c5221f; }
.mention-menu {
  position: absolute; bottom: calc(100% + 6px); left: 0; right: 0;
  max-height: 280px; overflow-y: auto; background: var(--bg);
  border: 1px solid var(--border); border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.18); z-index: 1000; padding: 4px;
}
.mention-item {
  display: flex; align-items: baseline; gap: 8px; padding: 8px 12px;
  border-radius: 6px; cursor: pointer; font-size: 14px;
}
.mention-item.active { background: var(--code-bg); }
.mention-name { color: var(--text); font-weight: 600; white-space: nowrap; }
.mention-dir {
  color: var(--text-muted); font-size: 12px; overflow: hidden;
  text-overflow: ellipsis; white-space: nowrap; direction: rtl; text-align: left;
}
.mention-empty { padding: 10px 12px; color: var(--text-muted); font-size: 13px; }
</style>
