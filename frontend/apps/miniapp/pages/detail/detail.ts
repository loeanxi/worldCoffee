// detail.ts —— 笔记详情：点赞/收藏/关注/评论 + 微信转发裂变
import { request, imageUrl, parseImages } from '../../utils/request'
import { isLoggedIn } from '../../utils/auth'

interface CommentItem {
  id: number
  name: string
  content: string
}

Page({
  data: {
    id: 0,
    images: [] as string[],
    title: '',
    content: '',
    topics: [] as string[],
    authorId: 0,
    authorName: '咖啡爱好者',
    authorInitial: 'W',
    authorAvatar: '',
    createTime: '',
    likeCount: 0,
    liked: false,
    favorited: false,
    followed: false,
    comments: [] as CommentItem[],
    draft: ''
  },

  onLoad(query: Record<string, string | undefined>) {
    const id = Number(query.id || 0)
    this.setData({ id })
    if (id) this.load(id)
  },

  async load(id: number) {
    try {
      const res = await request<any>({ url: `/api/coffee/posts/${id}` })
      const d = res.data || {}
      const author = d.author || d.user || {}
      const rawImages = Array.isArray(d.images) ? d.images : parseImages(d.images)
      const comments = (Array.isArray(d.comments) ? d.comments : Array.isArray(d.commentList) ? d.commentList : [])
      this.setData({
        images: rawImages.map((i: any) => imageUrl(typeof i === 'string' ? i : i?.url || '')).filter(Boolean),
        title: d.title || '无标题笔记',
        content: d.content || '',
        topics: Array.isArray(d.topics) ? d.topics : [],
        authorId: Number(author.id || d.userId || 0),
        authorName: author.nickname || author.username || d.username || '咖啡爱好者',
        authorInitial: (author.nickname || author.username || 'W').charAt(0).toUpperCase(),
        authorAvatar: imageUrl(author.avatar || d.avatar || ''),
        createTime: String(d.createTime || '').slice(0, 16).replace('T', ' '),
        likeCount: Number(d.like_count ?? d.likeCount ?? 0) || 0,
        liked: !!(d.likedByMe ?? d.liked ?? false),
        favorited: !!(d.favoritedByMe ?? d.favorited ?? false),
        followed: !!(author.followed ?? author.followedByMe ?? false),
        comments: comments.map((c: any) => ({
          id: Number(c.id),
          name: c.author?.nickname || c.author?.username || c.username || '咖啡友',
          content: c.content || ''
        }))
      })
    } catch (e: any) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  requireLogin(): boolean {
    if (isLoggedIn()) return true
    wx.navigateTo({ url: '/pages/login/login' })
    return false
  },

  async toggleLike() {
    if (!this.requireLogin()) return
    try {
      const res = await request<boolean>({ url: `/api/coffee/posts/${this.data.id}/like`, method: 'POST' })
      const liked = typeof res.data === 'boolean' ? res.data : !this.data.liked
      this.setData({ liked, likeCount: Math.max(0, this.data.likeCount + (liked ? 1 : -1)) })
    } catch (e: any) {
      wx.showToast({ title: e.message || '操作失败', icon: 'none' })
    }
  },

  async toggleFavorite() {
    if (!this.requireLogin()) return
    try {
      const res = await request<boolean>({ url: `/api/coffee/posts/${this.data.id}/favorite`, method: 'POST' })
      const on = typeof res.data === 'boolean' ? res.data : !this.data.favorited
      this.setData({ favorited: on })
      wx.showToast({ title: on ? '已收藏' : '已取消收藏', icon: 'success' })
    } catch (e: any) {
      wx.showToast({ title: e.message || '操作失败', icon: 'none' })
    }
  },

  async toggleFollow() {
    if (!this.requireLogin()) return
    if (!this.data.authorId) return
    try {
      await request({ url: `/api/user/${this.data.authorId}/follow`, method: 'POST' })
      this.setData({ followed: !this.data.followed })
    } catch (e: any) {
      wx.showToast({ title: e.message || '操作失败', icon: 'none' })
    }
  },

  onDraft(e: any) {
    this.setData({ draft: e.detail.value })
  },

  async sendComment() {
    const text = this.data.draft.trim()
    if (!text) return
    if (!this.requireLogin()) return
    try {
      await request({ url: `/api/coffee/posts/${this.data.id}/comment`, method: 'POST', data: { content: text } })
      this.setData({ draft: '' })
      wx.showToast({ title: '评论成功', icon: 'success' })
      this.load(this.data.id)
    } catch (e: any) {
      wx.showToast({ title: e.message || '评论失败', icon: 'none' })
    }
  },

  /** 裂变：转发好友卡片 */
  onShareAppMessage() {
    return {
      title: this.data.title || 'WorldCoffee 咖啡社区',
      path: '/pages/detail/detail?id=' + this.data.id,
      imageUrl: this.data.images[0] || ''
    }
  }
})
