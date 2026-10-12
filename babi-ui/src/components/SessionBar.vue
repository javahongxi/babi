<script setup lang="ts">
defineProps<{
  sessionId: string
}>()

defineEmits<{
  'update:sessionId': [id: string]
  'clear-chat': []
  'clear-memory': []
}>()
</script>

<template>
  <div class="session-bar">
    <div class="session-inner">
      <label>Session:</label>
      <input
        :value="sessionId"
        @input="$emit('update:sessionId', ($event.target as HTMLInputElement).value)"
        placeholder="session id"
      />
      <button @click="$emit('clear-chat')">清空对话</button>
      <button @click="$emit('clear-memory')" title="清除工作区记忆">清除记忆</button>
    </div>
  </div>
</template>

<style scoped>
.session-bar { padding: 8px 24px; display: flex; justify-content: center; font-size: 13px; color: var(--text-muted); }
.session-inner { max-width: 1100px; width: 100%; display: flex; align-items: center; gap: 8px; }
.session-inner input { border: 1px solid var(--border); border-radius: 4px; padding: 4px 8px; font-size: 13px; width: 160px; outline: none; background: var(--bg); color: var(--text); }
.session-inner input:focus { border-color: var(--accent); }
.session-inner button { background: none; border: 1px solid var(--border); border-radius: 4px; padding: 4px 10px; font-size: 13px; cursor: pointer; color: var(--text-muted); }
.session-inner button:hover { border-color: var(--accent); color: var(--text); }
</style>
