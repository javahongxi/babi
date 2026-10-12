<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import type { ModelInfo } from '../types/chat'

const props = defineProps<{
  models: ModelInfo[]
  modelValue: string
}>()

const emit = defineEmits<{ 'update:modelValue': [name: string] }>()

const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)

const current = computed(() => props.models.find((m) => m.name === props.modelValue))
const triggerLabel = computed(() => current.value?.displayName || 'Model')

function toggle() {
  open.value = !open.value
}

function select(name: string) {
  emit('update:modelValue', name)
  open.value = false
}

function onDocClick(e: MouseEvent) {
  if (rootRef.value && !rootRef.value.contains(e.target as Node)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('click', onDocClick, true))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick, true))
</script>

<template>
  <div ref="rootRef" class="model-dropdown" :class="{ open }">
    <button class="model-dropdown-trigger" type="button" @click.stop="toggle">
      <span>{{ triggerLabel }}</span>
      <span class="chevron">▼</span>
    </button>
    <div class="model-dropdown-menu">
      <div
        v-for="m in models"
        :key="m.name"
        class="model-option"
        :class="{ selected: m.name === modelValue }"
        @click="select(m.name)"
      >
        <div class="model-option-title">{{ m.displayName }}</div>
        <div class="model-option-desc">{{ m.description }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.model-dropdown { position: relative; display: inline-flex; }
.model-dropdown-trigger {
  background: rgba(255,255,255,0.1); border: 1px solid rgba(255,255,255,0.25);
  border-radius: 8px; padding: 5px 12px; color: #fff; cursor: pointer; font-size: 13px;
  display: flex; align-items: center; gap: 6px; transition: background 0.2s; white-space: nowrap;
}
.model-dropdown-trigger:hover { background: rgba(255,255,255,0.2); }
.model-dropdown-trigger .chevron { font-size: 10px; opacity: 0.6; transition: transform 0.2s; }
.model-dropdown.open .chevron { transform: rotate(180deg); }
.model-dropdown-menu {
  display: none; position: absolute; top: calc(100% + 6px); left: 0;
  min-width: 260px; max-height: 400px; overflow-y: auto; background: #fff;
  border-radius: 10px; box-shadow: 0 8px 32px rgba(0,0,0,0.18);
  border: 1px solid rgba(0,0,0,0.08); z-index: 1000; padding: 4px;
}
.model-dropdown.open .model-dropdown-menu { display: block; }
.model-option { padding: 10px 14px; border-radius: 8px; cursor: pointer; transition: background 0.15s; }
.model-option:hover { background: #f0f4ff; }
.model-option.selected { background: #e8f0fe; }
.model-option-title { font-size: 14px; font-weight: 600; color: #1a1a2e; }
.model-option-desc { font-size: 12px; color: #888; margin-top: 2px; }

[data-theme="dark"] .model-dropdown-menu { background: #252526; border-color: rgba(255,255,255,0.12); }
[data-theme="dark"] .model-option-title { color: #e0e0e0; }
[data-theme="dark"] .model-option-desc { color: #999; }
[data-theme="dark"] .model-option:hover { background: #2a2d35; }
[data-theme="dark"] .model-option.selected { background: #2d3338; }
</style>
