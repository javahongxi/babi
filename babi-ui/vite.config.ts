import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],

  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8900',
        changeOrigin: true,
      },
    },
  },

  build: {
    // 输出到 babi-ui/dist/（已被 .gitignore 忽略），可部署到任意静态服务器
    outDir: 'dist',
    emptyOutDir: true,
  },
})
