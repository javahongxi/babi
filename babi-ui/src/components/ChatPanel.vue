<script setup lang="ts">
import { nextTick, ref, watch, type DeepReadonly } from 'vue'
import type { ChatMessage } from '../types/chat'
import MessageBubble from './MessageBubble.vue'

const props = defineProps<{
  messages: DeepReadonly<ChatMessage[]>
  isStreaming: boolean
  streamingStarted: boolean
}>()

const containerRef = ref<HTMLElement | null>(null)

function scrollToBottom() {
  nextTick(() => {
    if (containerRef.value) {
      containerRef.value.scrollTop = containerRef.value.scrollHeight
    }
  })
}

// Scroll on messages change and on token updates (via last msg content length)
watch(
  () => {
    const last = props.messages[props.messages.length - 1]
    return last ? (last.content.length + (last.toolEvents?.length || 0) + (last.reasoning?.length || 0)) : 0
  },
  scrollToBottom
)
watch(() => props.messages.length, scrollToBottom)
</script>

<template>
  <div class="chat-container" ref="containerRef">
    <div class="chat-inner">
      <MessageBubble
        v-for="(msg, i) in messages"
        :key="i"
        :message="msg"
        :streaming="isStreaming && i === messages.length - 1 && msg.role === 'agent'"
      />
      <div v-if="isStreaming && !streamingStarted" class="typing-indicator">
        <span></span><span></span><span></span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-container { flex: 1; overflow-y: auto; padding: 24px; display: flex; flex-direction: column; }
.chat-inner { max-width: 1100px; width: 100%; margin: 0 auto; display: flex; flex-direction: column; gap: 16px; }
.typing-indicator { display: inline-flex; gap: 4px; padding: 12px 16px; align-self: flex-end; width: 100%; }
.typing-indicator span { width: 6px; height: 6px; background: #999; border-radius: 50%; animation: bounce 1.4s infinite both; }
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce { 0%, 80%, 100% { transform: scale(0); } 40% { transform: scale(1); } }
</style>
