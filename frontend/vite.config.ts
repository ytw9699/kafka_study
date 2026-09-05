import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 백엔드가 세 포트로 나뉘어 있으므로 dev 서버 프록시로 처리한다.
// 백엔드마다 CORS 설정을 흩뿌리지 않기 위함이다.
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // ledger-api — 거래 발행 + SSE 게이트웨이
      '/api/tx':     { target: 'http://localhost:8081', changeOrigin: true },
      '/api/config': { target: 'http://localhost:8081', changeOrigin: true },
      '/api/stream': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        // SSE 는 응답을 버퍼링하면 안 된다. 버퍼링되면 이벤트가 뭉쳐서 늦게 도착한다.
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            proxyRes.headers['cache-control'] = 'no-cache'
            proxyRes.headers['x-accel-buffering'] = 'no'
          })
        },
      },
      // balance-worker — 잔액 / 워커 배정 현황 (3~4단계)
      '/api/balance': { target: 'http://localhost:8082', changeOrigin: true },
      '/api/workers': { target: 'http://localhost:8082', changeOrigin: true },
      // fraud-service (6단계)
      '/api/fraud':   { target: 'http://localhost:8083', changeOrigin: true },
    },
  },
})
