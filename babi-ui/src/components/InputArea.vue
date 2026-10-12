<script setup lang="ts">
import { ref, nextTick } from 'vue'

defineProps<{ isStreaming: boolean }>()
const emit = defineEmits<{
  send: [message: string]
  interrupt: []
}>()

const text = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)

function handleSend() {
  if (!text.value.trim()) return
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
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
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
      <textarea
        ref="textareaRef"
        v-model="text"
        :placeholder="isStreaming ? '等待回复中...' : '输入消息，Enter 发送，Shift+Enter 换行...'"
        rows="1"
        @keydown="handleKeydown"
        @input="autoResize"
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
</style>
