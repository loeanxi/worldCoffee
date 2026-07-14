import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 10000
})

request.interceptors.request.use(config => {
  const token = sessionStorage.getItem('admin_token')
  config.headers = config.headers || {}
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    const message = res.message || res.msg || '请求失败'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  },
  error => {
    if (error.response?.status === 401 || error.response?.status === 403) {
      ElMessage.error('登录已过期，请重新登录')
      sessionStorage.removeItem('admin_token')
      router.push('/login')
    } else {
      ElMessage.error(error.response?.data?.message || error.response?.data?.msg || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
