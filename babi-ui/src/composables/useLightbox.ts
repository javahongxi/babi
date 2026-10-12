import { ref } from 'vue'

const lightboxSrc = ref<string | null>(null)

export function useLightbox() {
  function show(src: string) { lightboxSrc.value = src }
  function hide() { lightboxSrc.value = null }
  return { lightboxSrc, show, hide }
}

export function emitImageClick(src: string) {
  lightboxSrc.value = src
}

export function getLightboxSrc() {
  return lightboxSrc
}
