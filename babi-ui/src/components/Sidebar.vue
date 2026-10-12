<script setup lang="ts">
import type { TreeNode } from '../composables/useWorkspace'
import FileTree from './FileTree.vue'

defineProps<{
  nodes: TreeNode[]
  visible: boolean
}>()

const emit = defineEmits<{
  openFile: [path: string, name: string]
  toggleDir: [node: TreeNode]
  refresh: []
  close: []
}>()
</script>

<template>
  <aside class="sidebar" :class="{ collapsed: !visible }">
    <div class="sidebar-header">
      <span>📁 工作区</span>
      <span class="header-actions">
        <button @click="emit('refresh')" title="刷新文件树">🔄</button>
        <button @click="emit('close')" title="收起侧边栏">✕</button>
      </span>
    </div>
    <div class="sidebar-body">
      <FileTree
        :nodes="nodes"
        @open-file="(p, n) => emit('openFile', p, n)"
        @toggle-dir="(n) => emit('toggleDir', n)"
      />
      <div v-if="nodes.length === 0" class="empty-hint">暂无文件</div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 240px; min-width: 200px; background: var(--code-bg);
  border-right: 1px solid var(--border); display: flex; flex-direction: column;
  transition: width 0.2s, min-width 0.2s, opacity 0.2s; overflow: hidden;
}
.sidebar.collapsed { width: 0; min-width: 0; opacity: 0; pointer-events: none; }
.sidebar-header {
  padding: 10px 14px; font-size: 12px; font-weight: 600;
  text-transform: uppercase; letter-spacing: 0.5px; color: var(--text-muted);
  border-bottom: 1px solid var(--border);
  display: flex; align-items: center; justify-content: space-between;
  white-space: nowrap;
}
.header-actions { display: flex; gap: 4px; }
.sidebar-header button { background: none; border: none; cursor: pointer; font-size: 14px; color: var(--text-muted); padding: 2px 6px; border-radius: 4px; }
.sidebar-header button:hover { background: rgba(0,0,0,0.06); }
.sidebar-body { flex: 1; overflow-y: auto; padding: 6px 0; }
.empty-hint { padding: 12px 14px; font-size: 13px; color: var(--text-muted); }
</style>
