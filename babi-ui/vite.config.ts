import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],

  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8901',
        changeOrigin: true,
      },
    },
  },

  build: {
    // 默认输出到 babi-ui/dist/（已被 .gitignore 忽略）
    // 想把新版 UI 装进 Spring Boot 静态目录，手动执行：
    //   rm -rf ../babi-common/src/main/resources/static/*
    //   cp -r dist/* ../babi-common/src/main/resources/static/
    outDir: 'dist',
    emptyOutDir: true,
  },
})
