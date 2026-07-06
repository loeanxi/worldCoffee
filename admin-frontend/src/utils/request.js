import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// axios 实例，统一管理请求/响应拦截
const request = axios.create({
  baseURL: 'http://localhost:8080',  // 后端地址
  timeout: 10000
})

// 请求拦截：自动带上管理员 JWT
request.interceptors.request.use(config => {
  const token = sessionStorage.getItem('admin_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：统一处理错误
request.interceptors.response.use(
  response => {
    const res = response.data
    // 后端统一返回格式：{ code: 200, data: ..., message: ... }
    if (res.code === 200) {
      return res.data
    }
    // 业务错误
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  },
  error => {
    if (error.response?.status === 401 || error.response?.status === 403) {
      ElMessage.error('登录已过期，请重新登录')
      sessionStorage.removeItem('admin_token')
      router.push('/login')
    } else {
      ElMessage.error(error.response?.data?.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
