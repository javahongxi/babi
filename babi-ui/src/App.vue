<script setup lang="ts">
import { onMounted } from 'vue'
import { useChat } from './composables/useChat'
import { useWorkspace } from './composables/useWorkspace'
import { useTheme } from './composables/useTheme'

import AppHeader from './components/AppHeader.vue'
import SessionBar from './components/SessionBar.vue'
import ChatPanel from './components/ChatPanel.vue'
import InputArea from './components/InputArea.vue'
import Sidebar from './components/Sidebar.vue'
import FilePreview from './components/FilePreview.vue'
import Lightbox from './components/Lightbox.vue'

const chat = useChat()
const workspace = useWorkspace()
const { theme, init: initTheme, toggle: toggleTheme } = useTheme()

onMounted(async () => {
  initTheme()
  await chat.loadConfig()
  await workspace.loadTree()
})
</script>

<template>
  <div class="app">
    <AppHeader
      :badge="chat.badge.value"
      :models="chat.models.value"
      :selected-model="chat.selectedModel.value"
      :theme="theme"
      @update:selected-model="(m) => (chat.selectedModel.value = m)"
      @toggle-theme="toggleTheme"
    />
    <SessionBar
      v-model:session-id="chat.sessionId.value"
      @clear-chat="chat.clearChat()"
      @clear-memory="chat.clearMemory()"
    />
    <div class="main-content">
      <Sidebar
        :nodes="workspace.treeItems.value"
        :visible="workspace.sidebarVisible.value"
        @open-file="workspace.openFile"
        @toggle-dir="workspace.toggleDir"
        @refresh="workspace.refresh"
        @close="workspace.toggleSidebar()"
      />
      <div class="content-area">
        <ChatPanel
          :messages="chat.messages.value"
          :is-streaming="chat.isStreaming.value"
          :streaming-started="chat.streamingStarted.value"
        />
        <InputArea
          :is-streaming="chat.isStreaming.value"
          @send="chat.sendMessage"
          @interrupt="chat.interrupt"
        />
      </div>
      <FilePreview
        :file="workspace.previewFile.value"
        :filename="workspace.previewFilename.value"
        :path="workspace.previewPath.value"
        :open="workspace.previewOpen.value"
        @close="workspace.closePreview()"
      />
    </div>

    <!-- Floating button to reopen the sidebar when collapsed -->
    <button
      v-if="!workspace.sidebarVisible.value"
      class="sidebar-toggle"
      @click="workspace.toggleSidebar()"
      title="展开侧边栏"
    >☰</button>

    <Lightbox />
  </div>
</template>

<style>
.app { height: 100vh; display: flex; flex-direction: column; overflow: hidden; background: var(--bg); }
.main-content { flex: 1; display: flex; overflow: hidden; }
.content-area { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.sidebar-toggle {
  position: fixed; left: 8px; bottom: 60px; z-index: 100;
  background: var(--accent); color: #fff; border: none; border-radius: 50%;
  width: 36px; height: 36px; font-size: 18px; cursor: pointer;
  box-shadow: 0 2px 8px rgba(0,0,0,0.2);
  display: flex; align-items: center; justify-content: center;
}
</style>
