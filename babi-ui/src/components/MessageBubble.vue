<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch, type DeepReadonly } from 'vue'
import type { ChatMessage } from '../types/chat'
import ToolEventList from './ToolEventList.vue'
import ReasoningBlock from './ReasoningBlock.vue'
import {
  renderMarkdown,
  parsePinnedRepos,
  renderPinnedRepos,
  embedImageUrls,
  bindImageClicks,
} from '../utils/markdown'
import { emitImageClick } from '../composables/useLightbox'

const props = defineProps<{
  message: DeepReadonly<ChatMessage>
  streaming?: boolean
}>()

const contentRef = ref<HTMLElement | null>(null)

const pinnedRepos = computed(() => parsePinnedRepos(props.message.content || ''))

const renderedHtml = computed(() => {
  if (props.message.role === 'user') return null
  if (pinnedRepos.value) return renderPinnedRepos(pinnedRepos.value)
  return renderMarkdown(props.message.content || '')
})

async function decorate() {
  if (props.message.role === 'user') return
  await nextTick()
  const el = contentRef.value
  if (!el) return
  // Only apply markdown-specific post-processing when we rendered markdown (not cards)
  if (!pinnedRepos.value) {
    embedImageUrls(el, emitImageClick)
    bindImageClicks(el, emitImageClick)
  }
}

watch(renderedHtml, decorate)
onMounted(decorate)
</script>

<template>
  <div class="msg" :class="message.role">
    <!-- User: plain text -->
    <template v-if="message.role === 'user'">{{ message.content }}</template>

    <!-- Agent: reasoning + tool events + markdown body -->
    <template v-else>
      <ReasoningBlock
        v-if="message.reasoning"
        :content="message.reasoning"
        :collapsed="!streaming"
      />
      <ToolEventList
        v-if="message.toolEvents && message.toolEvents.length"
        :events="message.toolEvents"
      />
      <div ref="contentRef" class="md-content" v-html="renderedHtml" />
    </template>
  </div>
</template>

<style scoped>
.msg { padding: 12px 16px; border-radius: 12px; line-height: 1.7; font-size: 16px; word-break: break-word; }
.msg.user {
  max-width: 75%; align-self: flex-end; background: rgba(26,115,232,0.75);
  color: #fff; border-bottom-right-radius: 4px; white-space: pre-wrap;
  border: 1px solid rgba(255,255,255,0.35);
}
.msg.agent {
  width: 100%; background: var(--msg-agent-bg);
  color: var(--text); border-bottom-right-radius: 4px;
  border: 1px solid var(--border);
}
.md-content :deep(pre) { background: var(--code-bg); border-radius: 6px; padding: 12px; overflow-x: auto; font-size: 12px; line-height: 1.5; margin: 8px 0; }
.md-content :deep(code) { font-family: var(--mono); font-size: 0.82em; }
.md-content :deep(:not(pre) > code) { background: var(--code-bg); padding: 2px 5px; border-radius: 3px; }
.md-content :deep(table) { border-collapse: collapse; margin: 8px 0; width: 100%; }
.md-content :deep(th), .md-content :deep(td) { border: 1px solid var(--border); padding: 6px 12px; text-align: left; }
.md-content :deep(th) { background: var(--code-bg); font-weight: 600; }
.md-content :deep(a) { color: var(--accent); text-decoration: none; }
.md-content :deep(a:hover) { text-decoration: underline; }
.md-content :deep(img), .md-content :deep(.embedded-image) { max-width: 100%; border-radius: 8px; margin: 8px 0; }
.md-content :deep(h1), .md-content :deep(h2), .md-content :deep(h3) { margin: 16px 0 8px; font-weight: 600; }
.md-content :deep(blockquote) { border-left: 4px solid var(--border); padding: 4px 12px; margin: 8px 0; color: var(--text-muted); background: var(--code-bg); border-radius: 0 4px 4px 0; }

/* Pinned repos card styles */
:deep(.pinned-repos-header) { font-size: 1.2em; font-weight: 600; margin: 12px 0 16px; color: var(--text); }
:deep(.repo-grid) { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; margin: 12px 0; }
:deep(.repo-card) { border: 1px solid var(--border); border-radius: 8px; padding: 14px 16px; background: var(--bg); transition: box-shadow 0.2s, border-color 0.2s; display: flex; flex-direction: column; }
:deep(.repo-card:hover) { box-shadow: 0 2px 8px rgba(0,0,0,0.08); border-color: var(--accent); }
:deep(.repo-card .repo-name) { font-size: 14px; font-weight: 600; margin-bottom: 6px; }
:deep(.repo-card .repo-name a) { color: var(--accent); text-decoration: none; }
:deep(.repo-card .repo-desc) { font-size: 13px; color: var(--text-muted); margin-bottom: 10px; flex: 1; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
:deep(.repo-card .repo-meta) { font-size: 12px; color: var(--text-muted); display: flex; gap: 14px; align-items: center; }
:deep(.repo-card .repo-meta span) { display: flex; align-items: center; gap: 3px; }
:deep(.lang-dot) { width: 10px; height: 10px; border-radius: 50%; display: inline-block; margin-right: 3px; vertical-align: middle; }
</style>
