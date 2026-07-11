import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, URL } from 'node:url'

const webRoot = fileURLToPath(new URL('.', import.meta.url))
const projectRoot = path.resolve(webRoot, '..')

const readBackendEnv = () => {
  const envPath = path.join(projectRoot, 'backend', '.env.local')
  if (!fs.existsSync(envPath)) return {} as Record<string, string>

  return fs.readFileSync(envPath, 'utf8')
    .split(/\r?\n/)
    .reduce<Record<string, string>>((env, rawLine) => {
      const line = rawLine.trim()
      if (!line || line.startsWith('#')) return env
      const index = line.indexOf('=')
      if (index <= 0) return env
      const name = line.slice(0, index).trim()
      const value = line.slice(index + 1).trim().replace(/^['"]|['"]$/g, '')
      env[name] = value
      return env
    }, {})
}

const backendEnv = readBackendEnv()
const devHost = process.env.VITE_DEV_HOST || backendEnv.FRONTEND_HOST || '127.0.0.1'
const devPort = Number(process.env.VITE_DEV_PORT || backendEnv.FRONTEND_PORT || 5173)
const backendHost = backendEnv.BACKEND_HOST || '127.0.0.1'
const backendPort = backendEnv.SERVER_PORT || '8080'
const backendUrl = process.env.VITE_BACKEND_URL || `http://${backendHost}:${backendPort}`

export default defineConfig({
  plugins: [vue()],
  define: {
    __PV_BACKEND_URL__: JSON.stringify(backendUrl)
  },
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    host: devHost,
    port: devPort,
    strictPort: true,
    allowedHosts: ['localhost', '127.0.0.1'],
    fs: {
      strict: true,
      allow: [webRoot, projectRoot]
    },
    proxy: {
      '^/api/': {
        target: backendUrl,
        changeOrigin: true
      },
      '/openapi': {
        target: backendUrl,
        changeOrigin: true
      }
    }
  }
})
