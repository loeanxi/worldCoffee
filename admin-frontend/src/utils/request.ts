import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 10000
})

request.interceptors.request.use(config => {
  const token = sessionStorage.getItem('admin_token')
  if (token) {
    // axios v1 中拦截器的 headers 恒为 AxiosHeaders 实例，断言安全
    ;(config.headers as any).Authorization = 'Bearer ' + token
  }
  return config
})

/**
 * HTTP 状态码 → 用户提示兜底映射
 * （与 C 端 @wc/shared 的 HTTP_STATUS_MESSAGES 保持一致；admin-frontend 为独立工程，各自维护一份）
 */
const HTTP_STATUS_MESSAGES: Record<number, string> = {
  400: '请求参数有误，请检查后重试',
  404: '请求的内容不存在或已删除',
  408: '请求超时，请稍后重试',
  429: '操作太频繁，请稍后再试',
  500: '服务器开小差了，请稍后重试',
  502: '服务暂时不可用，请稍后重试',
  503: '服务暂时不可用，请稍后重试',
  504: '服务响应超时，请稍后重试'
}

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    const message = res.message || res.msg || '请求失败'
    // 支持 config.silent：调用方自行处理错误（如 Dashboard 未就绪接口静默降级到 mock）
    if (!(response.config as any)?.silent) {
      ElMessage.error(message)
    }
    return Promise.reject(new Error(message))
  },
  error => {
    const status = error.response?.status
    const silent = (error.config as any)?.silent
    if (status === 401 || status === 403) {
      if (!silent) ElMessage.error('登录已过期，请重新登录')
      sessionStorage.removeItem('admin_token')
      router.push('/login')
    } else if (!silent) {
      const serverMsg = error.response?.data?.message || error.response?.data?.msg
      const fallback = typeof status === 'number'
        ? (HTTP_STATUS_MESSAGES[status] || `请求失败（${status}）`)
        : '网络异常，请检查网络连接'
      ElMessage.error(serverMsg || fallback)
    }
    return Promise.reject(error)
  }
)

/**
 * 响应拦截器在运行时已把 Result 解包为业务 data，
 * 这里把导出类型对齐为 Promise<any>（与运行时行为一致），
 * 视图层可直接 `const data = await request.get(...)`
 */
type UnwrappedHttp = {
  get: (url: string, config?: any) => Promise<any>
  post: (url: string, data?: any, config?: any) => Promise<any>
  put: (url: string, data?: any, config?: any) => Promise<any>
  delete: (url: string, config?: any) => Promise<any>
}

export default request as unknown as UnwrappedHttp

