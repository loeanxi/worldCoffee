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
  /** 额外的 Vite 配置覆盖 */
  overrides?: UserConfig
}

/**
 * 创建共享的 Vite 基础配置
 * 所有 app（pc/mobile/miniapp）复用此配置，仅通过参数区分端口等差异
 */
export function createBaseConfig(options: BaseViteConfigOptions): UserConfig {
  const { port, apiTarget = 'http://localhost:8080', overrides = {} } = options

  return defineConfig({
    plugins: [vue()],
    server: {
      port,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true
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
