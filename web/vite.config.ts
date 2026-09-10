import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const rootDir = path.dirname(fileURLToPath(import.meta.url))

/** Gateway address: every /api/** request is forwarded through the gateway to the microservices. */
const GATEWAY_TARGET = process.env.VITE_PROXY_GATEWAY ?? 'http://127.0.0.1:10010'

/** Netty WebSocket address: independent of the gateway; the client connects straight to RealTimeService's Netty port. */
const NETTY_TARGET = process.env.VITE_PROXY_NETTY ?? 'ws://127.0.0.1:9101'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(rootDir, 'src'),
    },
  },
  server: {
    port: 5173,
    host: '127.0.0.1',
    proxy: {
      // In development everything goes through the Vite proxy, so the browser sees same-origin requests and there is no CORS or preflight to deal with.
      '/api': {
        target: GATEWAY_TARGET,
        changeOrigin: true,
      },
      // The WebSocket handshake goes through the proxy too, so it does not depend on the LAN IP the backend hands out.
      '/ws/netty': {
        target: NETTY_TARGET,
        ws: true,
        changeOrigin: true,
      },
    },
  },
})
