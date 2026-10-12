import { ref, watchEffect } from 'vue'

type Theme = 'light' | 'dark'

const theme = ref<Theme>('light')

export function useTheme() {
  // Initialize from localStorage or system preference
  function init() {
    const saved = localStorage.getItem('babi-theme') as Theme | null
    if (saved) {
      theme.value = saved
    } else if (window.matchMedia?.('(prefers-color-scheme: dark)').matches) {
      theme.value = 'dark'
    }
    applyToDocument()
    // Listen for system changes (unless user explicitly set)
    window.matchMedia?.('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
      if (!localStorage.getItem('babi-theme')) {
        theme.value = e.matches ? 'dark' : 'light'
      }
    })
  }

  function toggle() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
    localStorage.setItem('babi-theme', theme.value)
  }

  function applyToDocument() {
    watchEffect(() => {
      document.documentElement.setAttribute('data-theme', theme.value)
    })
  }

  return { theme, init, toggle }
}
