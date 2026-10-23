/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Dev server: same-origin proxies mirror what nginx does in the container.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/otlp': { target: 'http://localhost:4318', changeOrigin: true, rewrite: (p) => p.replace(/^\/otlp/, '') },
      '/media': { target: 'http://localhost:9000', changeOrigin: true, rewrite: (p) => p.replace(/^\/media/, '/linkedin-media') },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test-setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
