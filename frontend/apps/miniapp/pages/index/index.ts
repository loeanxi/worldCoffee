// index.ts —— 发现 Tab：推荐/最新/关注 + 话题搜索 + 双列瀑布流
// 吸顶栏右侧 100px 为微信胶囊避让区（见 index.wxml .nav-pad-r）
import { request, imageUrl, extractList, Result } from '../../utils/request'
import { isLoggedIn } from '../../utils/auth'

interface FeedItem {
  id: number
  title: string
  content: string
  cover: string
  authorName: string
  authorAvatar: string
  likeCount: number
  likedByMe: boolean
}

const BASE_TABS = [
  { key: 'recommend', label: '推荐' },
  { key: 'latest', label: '最新' }
]

Page({
  data: {
    statusBarHeight: 20,
    navHeight: 106,
    leftCol: [] as FeedItem[],
    rightCol: [] as FeedItem[],
    posts: [] as FeedItem[],
    page: 1,
    pageSize: 10,
    hasMore: true,
    loading: false,
    unread: 0,
    searchOpen: false,
    searchQuery: '',
    searching: false,
    activeTab: 'recommend',
    activeTopic: '',
    tabs: BASE_TABS,
    topicTabs: ['咖啡馆', '手冲', '拉花', '甜品', '冷萃']
  },

  onLoad() {
    const info = wx.getWindowInfo()
    const sb = info.statusBarHeight || 20
    // 状态栏 + 导航行 44 + chips 行 42
    this.setData({ statusBarHeight: sb, navHeight: sb + 44 + 42 })
    this.loadPosts(true)
  },

  onShow() {
    const tabs = isLoggedIn()
      ? BASE_TABS.concat([{ key: 'following', label: '关注' }])
      : BASE_TABS
    this.setData({ tabs })
    this.loadUnread()
  },

  onPullDownRefresh() {
    this.loadPosts(true).finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) this.loadPosts(false)
  },

  /** 未读通知数（铃铛红点），未登录跳过 */
  async loadUnread() {
    if (!isLoggedIn()) return
    try {
      const res = await request<number>({ url: '/api/notifications/unread-count' })
      this.setData({ unread: Number(res.data) || 0 })
    } catch {
      /* 静默 */
    }
  },

  // ─── 搜索 / 分类 ───
  openSearch() {
    this.setData({ searchOpen: true })
  },
  onSearchInput(e: any) {
    this.setData({ searchQuery: e.detail.value })
  },
  doSearch() {
    const kw = this.data.searchQuery.trim()
    if (!kw) return
    this.setData({ searching: true, activeTopic: '' }, () => this.loadPosts(true))
  },
  clearSearch() {
    const was = this.data.searching
    this.setData({ searchOpen: false, searchQuery: '', searching: false }, () => {
      if (was) this.loadPosts(true)
    })
  },
  switchTab(e: any) {
    const key = e.currentTarget.dataset.key as string
    if (this.data.activeTab === key && !this.data.activeTopic && !this.data.searching) return
    this.setData({ activeTab: key, activeTopic: '', searching: false }, () => this.loadPosts(true))
  },
  searchTopic(e: any) {
    const topic = e.currentTarget.dataset.topic as string
    if (this.data.activeTopic === topic) return
    this.setData({ activeTopic: topic, searching: false, searchQuery: topic }, () => this.loadPosts(true))
  },

  // ─── Feed 数据 ───
  async loadPosts(reset: boolean): Promise<void> {
    if (this.data.loading) return
    const page = reset ? 1 : this.data.page + 1
    this.setData({ loading: true })
    try {
      const params = { page, size: this.data.pageSize }
      let url = '/api/coffee/posts/recommend'
      if (this.data.activeTopic) url = '/api/coffee/posts/topic?topic=' + encodeURIComponent(this.data.activeTopic)
      else if (this.data.searching) url = '/api/coffee/search?keyword=' + encodeURIComponent(this.data.searchQuery)
      else if (this.data.activeTab === 'following') url = '/api/coffee/posts/following'
      else if (this.data.activeTab === 'latest') url = '/api/coffee/posts'
      const res = await request({ url, data: params })
      const items: FeedItem[] = extractList(res as Result<any>).map((p: any) => ({
        id: Number(p.id || p.postId || 0),
        title: p.title || (p.content || '').slice(0, 40) || '一杯咖啡的瞬间',
        content: p.content || '',
        cover: imageUrl(
          (Array.isArray(p.images) && p.images.length ? p.images[0] : '') ||
          p.coverImage || p.imageUrl || ''
        ),
        authorName:
          p.author?.nickname || p.author?.username ||
          p.user?.nickname || p.user?.username ||
          p.nickname || p.username || '咖啡爱好者',
        authorAvatar: imageUrl(p.author?.avatar || p.user?.avatar || p.avatar || ''),
        likeCount: Number(p.like_count ?? p.likeCount ?? p.likes ?? 0) || 0,
        likedByMe: !!(p.likedByMe ?? p.liked ?? false)
      }))
      const posts = reset ? items : this.data.posts.concat(items)
      this.setData({
        posts,
        leftCol: posts.filter((_, i) => i % 2 === 0),
        rightCol: posts.filter((_, i) => i % 2 === 1),
        page,
        hasMore: items.length >= this.data.pageSize
      })
    } catch (e: any) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
      if (reset) this.setData({ posts: [], leftCol: [], rightCol: [], hasMore: false })
    } finally {
      this.setData({ loading: false })
    }
  },

  // ─── 交互 ───
  openPost(e: any) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/detail/detail?id=' + id })
  },

  async onLike(e: any) {
    if (!isLoggedIn()) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    const { id, col, idx } = e.currentTarget.dataset
    const list: FeedItem[] = col === 0 ? this.data.leftCol.slice() : this.data.rightCol.slice()
    const item = list[idx]
    if (!item) return
    try {
      const res = await request<boolean>({ url: `/api/coffee/posts/${id}/like`, method: 'POST' })
      const liked = typeof res.data === 'boolean' ? res.data : !item.likedByMe
      item.likedByMe = liked
      item.likeCount = Math.max(0, item.likeCount + (liked ? 1 : -1))
      const patch: any = col === 0 ? { leftCol: list } : { rightCol: list }
      // 同步 posts 主表，保证翻页/刷新后状态一致
      const posts = this.data.posts.map(p => (p.id === item.id ? { ...p, ...item } : p))
      patch.posts = posts
      this.setData(patch)
    } catch (err: any) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' })
    }
  },

  goPublish() {
    if (!isLoggedIn()) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    wx.navigateTo({ url: '/pages/publish/publish' })
  },

  goNotify() {
    if (!isLoggedIn()) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    wx.navigateTo({ url: '/pages/notify/notify' })
  },

  goAI() {
    wx.showToast({ title: 'AI 助手为 P1，首版不做', icon: 'none' })
  }
})
