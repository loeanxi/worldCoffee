/**
 * auth.ts —— 小程序登录态管理
 * 对齐 web 端 useAuth：token 存 wx storage（wc_token / wc_user）
 * 登录走 POST /api/user/login（账号密码），返回 { token, userId, username }
 */
import { request } from './request'

const TOKEN_KEY = 'wc_token'
const USER_KEY = 'wc_user'

export interface LoginVO {
  token: string
  userId: number
  username: string
}

export function getToken(): string {
  try {
    return (wx.getStorageSync(TOKEN_KEY) as string) || ''
  } catch {
    return ''
  }
}

export function isLoggedIn(): boolean {
  return !!getToken()
}

export function getStoredUser(): LoginVO | null {
  try {
    const raw = wx.getStorageSync(USER_KEY) as string
    return raw ? (JSON.parse(raw) as LoginVO) : null
  } catch {
    return null
  }
}

/** 账号密码登录（与 web 端共用同一接口与 JWT 体系） */
export async function login(username: string, password: string): Promise<boolean> {
  const res = await request<LoginVO>({
    url: '/api/user/login',
    method: 'POST',
    data: { username, password }
  })
  if (res.data && res.data.token) {
    wx.setStorageSync(TOKEN_KEY, res.data.token)
    wx.setStorageSync(USER_KEY, JSON.stringify(res.data))
    return true
  }
  return false
}

/**
 * 微信一键登录：wx.login 拿 code → POST /api/user/wx-login。
 * 服务端 mock 模式（开发期）直接建档绑定；生产走 code2Session。
 */
export function wxLoginWithCode(code: string): Promise<LoginVO> {
  return request<LoginVO>({
    url: '/api/user/wx-login',
    method: 'POST',
    data: { code }
  }).then(res => {
    if (res.data && res.data.token) {
      wx.setStorageSync(TOKEN_KEY, res.data.token)
      wx.setStorageSync(USER_KEY, JSON.stringify(res.data))
      return res.data
    }
    throw new Error('微信登录失败')
  })
}

/** 封装 wx.login + 后端换 token，页面直接 await 即可 */
export function wxLogin(): Promise<LoginVO> {
  return new Promise((resolve, reject) => {
    wx.login({
      success: r => {
        if (!r.code) {
          reject(new Error('微信授权失败'))
          return
        }
        wxLoginWithCode(r.code).then(resolve).catch(reject)
      },
      fail: () => reject(new Error('微信授权失败'))
    })
  })
}

/** 退出登录：调后端拉黑 token（失败不阻塞），并清本地登录态 */
export async function logout(): Promise<void> {
  try {
    await request({ url: '/api/user/logout', method: 'POST' })
  } catch {
    // 后端拉黑失败不阻塞本地退出
  }
  try {
    wx.removeStorageSync(TOKEN_KEY)
    wx.removeStorageSync(USER_KEY)
  } catch { /* ignore */ }
}
