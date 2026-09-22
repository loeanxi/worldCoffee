// login.ts —— 登录页：微信一键登录为主 + 账号密码兜底（与 web 端共用 JWT）
import { login, wxLogin } from '../../utils/auth'

Page({
  data: {
    username: '',
    password: '',
    submitting: false,
    wxLoading: false
  },

  /**
   * 微信一键登录：wx.login code → POST /api/user/wx-login → 存 JWT。
   * 服务端开发期为 mock 建档模式，生产配置 AppSecret 后自动切真实 code2Session，前端无需改动。
   */
  async handleWxLogin() {
    if (this.data.wxLoading) return
    this.setData({ wxLoading: true })
    try {
      await wxLogin()
      wx.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 600)
    } catch (e: any) {
      wx.showToast({ title: e.message || '微信登录失败', icon: 'none' })
    } finally {
      this.setData({ wxLoading: false })
    }
  },

  onUsernameInput(e: any) {
    this.setData({ username: e.detail.value })
  },

  onPasswordInput(e: any) {
    this.setData({ password: e.detail.value })
  },

  async handleSubmit() {
    const { username, password, submitting } = this.data
    if (submitting) return
    if (!username.trim() || !password) {
      wx.showToast({ title: '请输入账号和密码', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      const ok = await login(username.trim(), password)
      if (ok) {
        wx.showToast({ title: '登录成功', icon: 'success' })
        setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 600)
      } else {
        wx.showToast({ title: '用户名或密码错误', icon: 'none' })
      }
    } catch (e: any) {
      wx.showToast({ title: e.message || '登录失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
