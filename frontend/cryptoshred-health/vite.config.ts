import { defineConfig, Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

function prometheusMetricsPlugin(): Plugin {
  return {
    name: 'prometheus-metrics-endpoint',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url === '/metrics' || req.url === '/actuator/prometheus') {
          res.setHeader('Content-Type', 'text/plain; version=0.0.4')
          res.end(
            '# HELP frontend_up Indicates whether the Vite React frontend dev server is active\n' +
            '# TYPE frontend_up gauge\n' +
            'frontend_up 1\n'
          )
          return
        }
        next()
      })
    },
  }
}

export default defineConfig({
  plugins: [react(), prometheusMetricsPlugin()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})

