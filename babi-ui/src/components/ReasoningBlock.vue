<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  content: string
  collapsed?: boolean
}>()

const isCollapsed = ref(props.collapsed ?? false)
</script>

<template>
  <div class="reasoning-block">
    <div class="reasoning-header" @click="isCollapsed = !isCollapsed">
      <span class="arrow" :class="{ collapsed: isCollapsed }">▼</span>
      <span>💭 思考过程</span>
    </div>
    <div class="reasoning-content" :class="{ collapsed: isCollapsed }">{{ content }}</div>
  </div>
</template>

<style scoped>
.reasoning-block {
  margin: 8px 0; border-left: 3px solid #a78bfa;
  background: rgba(167,139,250,0.08); border-radius: 0 6px 6px 0; overflow: hidden;
}
.reasoning-header {
  display: flex; align-items: center; gap: 6px; padding: 6px 12px;
  cursor: pointer; font-size: 13px; color: #7c3aed; user-select: none; font-weight: 500;
}
.reasoning-header:hover { background: rgba(167,139,250,0.12); }
.reasoning-header .arrow { transition: transform 0.2s; font-size: 10px; }
.reasoning-header .arrow.collapsed { transform: rotate(-90deg); }
.reasoning-content {
  padding: 4px 12px 10px; font-size: 13px; line-height: 1.6;
  color: var(--text-muted); font-style: italic; white-space: pre-wrap;
  max-height: 300px; overflow-y: auto;
}
.reasoning-content.collapsed { display: none; }
</style>
