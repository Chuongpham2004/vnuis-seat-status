import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// Dev server proxies everything to the local stack, so the browser only talks to
// one origin (same as the nginx container in production):
//   /api, /ws  -> backend-core (8080)
//   /sim       -> iot-simulator control API (8090), used by the admin demo panel
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backend = env.DEV_BACKEND_URL || 'http://localhost:8080'
  const simulator = env.DEV_SIMULATOR_URL || 'http://localhost:8090'

  return {
    plugins: [
      react({
        babel: {
          plugins: [['babel-plugin-react-compiler']],
        },
      }),
    ],
    server: {
      port: 5173,
      host: true, // listen on the LAN too (phones scanning the table QR)
      proxy: {
        // Keep the browser's Host header: backend-core then sees same-origin requests,
        // so the app also works from a phone on the LAN (http://<laptop-ip>:5173).
        '/api': { target: backend, changeOrigin: false },
        '/ws': { target: backend, ws: true, changeOrigin: false },
        '/sim': {
          target: simulator,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/sim/, ''),
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.js'],
    },
  }
})
