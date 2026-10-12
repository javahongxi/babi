<script setup lang="ts">
import { ref, type DeepReadonly } from 'vue'
import type { ToolEvent } from '../types/chat'

const props = defineProps<{
  events: DeepReadonly<ToolEvent[]>
}>()

const visibleCount = ref(2)
const shown = () => props.events.slice(-visibleCount.value)
const hiddenCount = () => Math.max(0, props.events.length - visibleCount.value)
</script>

<template>
  <div class="tool-events">
    <div v-if="hiddenCount() > 0" class="tool-summary">
      ⋯ 已调用 {{ events.length }} 次工具（仅显示最近 {{ visibleCount }} 次）
    </div>
    <div
      v-for="(evt, i) in shown()"
      :key="i"
      class="tool-event"
      :class="{ success: evt.state === 'SUCCESS', error: evt.state === 'ERROR' }"
    >
      <template v-if="evt.type === 'tool_call'">🔧 调用工具: {{ evt.tool }}</template>
      <template v-else>{{ evt.state === 'SUCCESS' ? '✅' : '❌' }} {{ evt.tool }} → {{ evt.state }}</template>
    </div>
  </div>
</template>

<style scoped>
.tool-events { display: flex; flex-direction: column; gap: 4px; margin-bottom: 6px; }
.tool-summary { font-size: 12px; color: var(--text-muted); font-style: italic; padding: 4px 10px; }
.tool-event {
  background: var(--code-bg); border-left: 3px solid var(--accent);
  padding: 6px 10px; font-size: 13px; color: var(--text-muted); border-radius: 4px;
}
.tool-event.success { border-left-color: #34a853; }
.tool-event.error { border-left-color: #ea4335; }
</style>
