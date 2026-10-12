<script setup lang="ts">
import type { TreeNode } from '../composables/useWorkspace'

defineProps<{ nodes: TreeNode[] }>()
const emit = defineEmits<{
  openFile: [path: string, name: string]
  toggleDir: [node: TreeNode]
}>()
</script>

<template>
  <div class="tree-level">
    <template v-for="node in nodes" :key="node.path">
      <div
        v-if="node.isDir"
        class="file-tree-item dir"
        @click="emit('toggleDir', node)"
      >
        <span class="toggle">{{ node.expanded ? '▼' : '▶' }}</span>
        <span class="icon">{{ node.expanded ? '📂' : '📁' }}</span>
        <span class="name">{{ node.name }}</span>
      </div>
      <div
        v-else
        class="file-tree-item file"
        @click="emit('openFile', node.path, node.name)"
      >
        <span class="toggle placeholder"></span>
        <span class="icon">📄</span>
        <span class="name">{{ node.name }}</span>
      </div>
      <div v-if="node.isDir && node.expanded" class="file-tree-children">
        <FileTree
          :nodes="node.children"
          @open-file="(p, n) => emit('openFile', p, n)"
          @toggle-dir="(n) => emit('toggleDir', n)"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.tree-level { font-size: 13px; }
.file-tree-item { padding: 4px 8px; cursor: pointer; display: flex; align-items: center; gap: 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: var(--text); }
.file-tree-item:hover { background: rgba(0,0,0,0.05); }
.file-tree-item .toggle { width: 12px; text-align: center; font-size: 9px; color: var(--text-muted); flex-shrink: 0; user-select: none; }
.file-tree-item .toggle.placeholder { visibility: hidden; }
.file-tree-item .icon { font-size: 13px; flex-shrink: 0; }
.file-tree-item .name { overflow: hidden; text-overflow: ellipsis; }
.file-tree-children { padding-left: 14px; }
</style>
