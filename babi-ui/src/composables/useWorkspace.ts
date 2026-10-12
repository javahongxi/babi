import { ref } from 'vue'
import type { WorkspaceItem, FileContent } from '../types/chat'
import { fetchFileTree, fetchFileContent } from '../api/client'

/** A tree node = workspace item + expansion state + children */
export interface TreeNode extends WorkspaceItem {
  expanded: boolean
  loaded: boolean
  children: TreeNode[]
}

function toNode(item: WorkspaceItem): TreeNode {
  return { ...item, expanded: false, loaded: false, children: [] }
}

export function useWorkspace() {
  const treeItems = ref<TreeNode[]>([])
  const previewFile = ref<FileContent | null>(null)
  const previewFilename = ref('')
  const previewPath = ref('')
  const sidebarVisible = ref(false)
  const previewOpen = ref(false)

  async function loadTree(path: string = '') {
    try {
      const items = await fetchFileTree(path)
      treeItems.value = items.map(toNode)
    } catch (e) {
      console.warn('Failed to load file tree:', e)
    }
  }

  async function refresh() {
    await loadTree('')
  }

  async function toggleDir(node: TreeNode) {
    if (!node.isDir) return
    if (!node.loaded) {
      try {
        const items = await fetchFileTree(node.path)
        node.children = items.map(toNode)
        node.loaded = true
      } catch (e) {
        console.warn('Failed to load children:', e)
        return
      }
    }
    node.expanded = !node.expanded
  }

  async function openFile(path: string, name: string) {
    if (/\.(png|jpe?g|gif|webp|svg|bmp|ico)$/i.test(name)) {
      previewFile.value = null
      previewPath.value = path
      previewFilename.value = name
      previewOpen.value = true
      return
    }
    previewPath.value = path
    previewFilename.value = name
    previewOpen.value = true
    try {
      previewFile.value = await fetchFileContent(path)
    } catch (e: any) {
      previewFile.value = { content: `Error: ${e.message}`, language: 'plaintext', size: 0 }
    }
  }

  function closePreview() {
    previewFile.value = null
    previewFilename.value = ''
    previewPath.value = ''
    previewOpen.value = false
  }

  function toggleSidebar() {
    sidebarVisible.value = !sidebarVisible.value
    if (!sidebarVisible.value) closePreview()
  }

  return {
    treeItems,
    previewFile,
    previewFilename,
    previewPath,
    previewOpen,
    sidebarVisible,
    loadTree,
    refresh,
    toggleDir,
    openFile,
    closePreview,
    toggleSidebar,
  }
}
