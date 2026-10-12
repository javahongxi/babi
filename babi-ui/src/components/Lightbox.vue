<script setup lang="ts">
import { useLightbox } from '../composables/useLightbox'

const { lightboxSrc, hide } = useLightbox()
</script>

<template>
  <Teleport to="body">
    <div v-if="lightboxSrc" class="lightbox" @click="hide">
      <img :src="lightboxSrc" alt="preview" @click.stop />
    </div>
  </Teleport>
</template>

<style scoped>
.lightbox {
  position: fixed; inset: 0; background: rgba(0,0,0,0.85);
  z-index: 9999; display: flex; align-items: center; justify-content: center;
  cursor: zoom-out;
}
.lightbox img { max-width: 90vw; max-height: 90vh; border-radius: 8px; box-shadow: 0 4px 32px rgba(0,0,0,0.5); cursor: default; }
</style>
