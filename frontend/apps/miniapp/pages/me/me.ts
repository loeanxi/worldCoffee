// me.ts —— 我的 Tab：头部大卡 / 订单入口 / 功能宫格 / 我的笔记与收藏
import { request, imageUrl, extractList, Result } from '../../utils/request'
import { isLoggedIn, getStoredUser, logout, LoginVO } from '../../utils/auth'

interface ContentItem {
  id: number
  title: string
  cover: string
  time: string
}

Page({
  data: {
    statusBarHeight: 20,
    navHeight: 64,
    loggedIn: false,
    avatar: '',
    nickname: '咖啡友',
    initial: 'W',
    bio: '',
    stats: { following: 0, followers: 0, likes: 0, posts: 0 },
    contentTab: 'posts',
    contentList: [] as ContentItem[],
    contentLoading: false
  },

  onLoad() {
    const sb = wx.getWindowInfo().statusBarHeight || 20
    this.setData({ statusBarHeight: sb, navHeight: sb + 44 })
  },

  onShow() {
    const loggedIn = isLoggedIn()
    const stored: LoginVO | null = loggedIn ? getStoredUser() : null
    this.setData({
      loggedIn,
      nickname: stored?.username || '咖啡友',
      initial: (stored?.username || 'W').charAt(0).toUpperCase()
    })
    if (loggedIn) {
      this.loadProfile()
      this.loadStats()
      this.loadContent()
    } else {
      this.setData({ avatar: '', bio: '', contentList: [], stats: { following: 0, followers: 0, likes: 0, posts: 0 } })
    }
  },

  async loadProfile() {
    try {
      const res = await request<any>({ url: '/api/user/me' })
      const u = res.data || {}
      this.setData({
        avatar: imageUrl(u.avatar || ''),
        nickname: u.nickname || u.username || this.data.nickname,
        initial: (u.nickname || u.username || 'W').charAt(0).toUpperCase(),
        bio: u.signature || u.bio || ''
      })
    } catch {
      /* 静默，保留本地登录态信息 */
    }
  },

  async loadStats() {
    try {
      const res = await request<any>({ url: '/api/user/me/stats' })
      const d = res.data || {}
      this.setData({
        stats: {
          following: Number(d.followCount ?? d.followingCount ?? 0),
          followers: Number(d.fansCount ?? d.followerCount ?? 0),
          likes: Number(d.likeCount ?? 0),
          posts: Number(d.postCount ?? 0)
        }
      })
    } catch {
      /* 静默 */
    }
  },

  switchContent(e: any) {
    const key = e.currentTarget.dataset.key as string
    if (this.data.contentTab === key) return
    this.setData({ contentTab: key }, () => this.loadContent())
  },

  async loadContent() {
    this.setData({ contentLoading: true })
    try {
      const url = this.data.contentTab === 'posts' ? '/api/coffee/posts/my' : '/api/coffee/favorites/my'
      const res = await request({ url, data: { page: 1, size: 20 } })
      const list: ContentItem[] = extractList(res as Result<any>).map((p: any) => ({
        id: Number(p.id),
        title: p.title || '无标题笔记',
        cover: imageUrl(
          (Array.isArray(p.images) && p.images.length ? p.images[0] : '') || p.coverImage || p.imageUrl || ''
        ),
        time: String(p.createTime || '').slice(0, 10)
      }))
      this.setData({ contentList: list })
    } catch {
      this.setData({ contentList: [] })
    } finally {
      this.setData({ contentLoading: false })
    }
  },

  // ─── 跳转 ───
  openPost(e: any) {
    wx.navigateTo({ url: '/pages/detail/detail?id=' + e.currentTarget.dataset.id })
  },
  goOrders(e: any) {
    if (!this.requireLogin()) return
    wx.navigateTo({ url: '/pages/orders/orders' })
  },
  goAddress() {
    if (!this.requireLogin()) return
    wx.navigateTo({ url: '/pages/address/address' })
  },
  goCoupons() {
    wx.showToast({ title: '领券中心为 P1，首版不做', icon: 'none' })
  },
  goAI() {
    wx.showToast({ title: 'AI 助手为 P1，首版不做', icon: 'none' })
  },
  goSettings() {
    wx.showToast({ title: '设置页为 P1，首版不做', icon: 'none' })
  },
  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' })
  },
  requireLogin(): boolean {
    if (isLoggedIn()) return true
    wx.navigateTo({ url: '/pages/login/login' })
    return false
  },

  handleLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定退出当前账号？',
      success: async r => {
        if (!r.confirm) return
        await logout()
        this.onShow()
        wx.showToast({ title: '已退出登录', icon: 'success' })
      }
    })
  }
})
