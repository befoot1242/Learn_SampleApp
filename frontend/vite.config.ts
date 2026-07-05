import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: Number(process.env.PORT) || 5173,
    proxy: {
      // BFF(Spring Boot)へプロキシ。Cookie/CSRFは同一オリジン扱いになる
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
