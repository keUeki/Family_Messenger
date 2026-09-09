import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const rootDir = path.dirname(fileURLToPath(import.meta.url))

/** 网关地址：所有 /api/** 请求都经由网关转发到各个微服务。 */
const GATEWAY_TARGET = process.env.VITE_PROXY_GATEWAY ?? 'http://127.0.0.1:10010'

/** Netty WebSocket 地址：与网关无关，前端直连 RealTimeService 的 Netty 端口。 */
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
      // 开发环境下走 Vite 代理，浏览器视角是同源请求，因此不存在跨域与预检问题。
      '/api': {
        target: GATEWAY_TARGET,
        changeOrigin: true,
      },
      // WebSocket 握手同样经由代理，避免依赖后端下发的局域网 IP。
      '/ws/netty': {
        target: NETTY_TARGET,
        ws: true,
        changeOrigin: true,
      },
    },
  },
})
