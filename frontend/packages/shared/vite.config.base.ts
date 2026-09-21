import { defineConfig, type UserConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import tailwindcss from 'tailwindcss'
import autoprefixer from 'autoprefixer'

export interface BaseViteConfigOptions {
  /** 开发服务器端口 */
  port: number
  /** 后端 API 地址 */
  apiTarget?: string
  /** 大文件上传直连地址（绕过 reactive 网关） */
  uploadTarget?: string
  /** 额外的 Vite 配置覆盖 */
  overrides?: UserConfig
}

/**
 * 创建共享的 Vite 基础配置
 * 所有 app（pc/mobile/miniapp）复用此配置，仅通过参数区分端口等差异
 */
export function createBaseConfig(options: BaseViteConfigOptions): UserConfig {
  const { port, apiTarget = 'http://localhost:8080', uploadTarget = 'http://localhost:8083', overrides = {} } = options

  return defineConfig({
    plugins: [vue()],
    server: {
      port,
      proxy: {
        // 大文件上传直连 wc-community，绕过 reactive 网关：
        // SCG 的 Netty 写缓冲/codec 内存上限会对 500MB 级 multipart 施加背压，导致上传流卡死
        '/api/coffee/upload': {
          target: uploadTarget,
          changeOrigin: true,
          timeout: 15 * 60 * 1000,
          proxyTimeout: 15 * 60 * 1000
        },
        '/api': {
          target: apiTarget,
          changeOrigin: true,
          // 放开代理超时，支持大请求（默认 Node http.Server 超时 120s）
          timeout: 15 * 60 * 1000,
          proxyTimeout: 15 * 60 * 1000
        },
        '/uploads': {
          target: apiTarget,
          changeOrigin: true
        }
      }
    },
    resolve: {
      alias: {
        '@wc/shared': fileURLToPath(new URL('../../packages/shared/src', import.meta.url))
      }
    },
    optimizeDeps: {
      exclude: ['@wc/shared']
    },
    css: {
      postcss: {
        plugins: [tailwindcss(), autoprefixer()]
      }
    },
    ...overrides
  })
}
