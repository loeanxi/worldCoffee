// notify.ts —— 通知列表：GET /api/notifications，点击标读并跳对应内容
import { request, extractList, Result } from '../../utils/request'

interface NotifyItem {
  id: number
  icon: string
  bg: string
  senderName: string
  content: string
  time: string
  isRead: boolean
  postId: number
  type: string
}

const TYPE_META: Record<string, { icon: string; bg: string }> = {
  LIKE: { icon: '❤', bg: 'var(--accent-soft)' },
  COMMENT: { icon: '💬', bg: 'var(--brand-soft)' },
  ORDER: { icon: '📦', bg: '#EEEEEE' },
  SYSTEM: { icon: '🔔', bg: '#EEEEEE' }
}

Page({
  data: {
    list: [] as NotifyItem[],
    loading: false
  },

  onShow() {
    this.load()
  },

  async load() {
    this.setData({ loading: true })
    try {
      const res = await request({ url: '/api/notifications', data: { filter: 'all', page: 1, size: 30 } })
      const list: NotifyItem[] = extractList(res as Result<any>).map((n: any) => {
        const meta = TYPE_META[String(n.type || '').toUpperCase()] || TYPE_META.SYSTEM
        return {
          id: Number(n.id),
          icon: meta.icon,
          bg: meta.bg,
          senderName: n.senderName || '',
          content: n.content || '',
          time: String(n.createTime || '').slice(0, 16).replace('T', ' '),
          isRead: !!n.isRead,
          postId: Number(n.postId || 0),
          type: String(n.type || '')
        }
      })
      this.setData({ list })
    } catch (e: any) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async openItem(e: any) {
    const item = this.data.list[e.currentTarget.dataset.idx as number]
    if (!item) return
    if (!item.isRead) {
      request({ url: `/api/notifications/${item.id}/read`, method: 'PUT' }).catch(() => {})
      const list = this.data.list.slice()
      const idx = list.findIndex(x => x.id === item.id)
      if (idx >= 0) {
        list[idx] = { ...list[idx], isRead: true }
        this.setData({ list })
      }
    }
    if (item.postId) {
      wx.navigateTo({ url: '/pages/detail/detail?id=' + item.postId })
    } else if (/ORDER/i.test(item.type)) {
      wx.navigateTo({ url: '/pages/orders/orders' })
    }
  },

  async readAll() {
    try {
      await request({ url: '/api/notifications/read-all', method: 'PUT' })
      this.setData({ list: this.data.list.map(x => ({ ...x, isRead: true })) })
      wx.showToast({ title: '已全部标读', icon: 'success' })
    } catch (e: any) {
      wx.showToast({ title: e.message || '操作失败', icon: 'none' })
    }
  }
})
