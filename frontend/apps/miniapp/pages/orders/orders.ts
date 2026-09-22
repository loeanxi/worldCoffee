// orders.ts —— 订单列表：状态筛选 + 按状态流转操作
// 状态：0-待支付 1-已支付(待发货) 2-已发货(待收货) 3-已完成 4-已取消
import { request, imageUrl, extractList, Result } from '../../utils/request'

interface OrderItem {
  id: number
  orderNo: string
  status: number
  statusText: string
  statusColor: string
  total: string
  itemCount: number
  thumbs: string[]
  firstProductId: number
}

const STATUS_META: Record<number, { text: string; color: string }> = {
  0: { text: '待付款', color: 'var(--danger)' },
  1: { text: '待发货', color: 'var(--brand-deep)' },
  2: { text: '待收货', color: 'var(--accent)' },
  3: { text: '已完成', color: 'var(--ink3)' },
  4: { text: '已取消', color: 'var(--ink3)' }
}

Page({
  data: {
    chips: [
      { key: -1, label: '全部' },
      { key: 0, label: '待付款' },
      { key: 1, label: '待发货' },
      { key: 2, label: '待收货' },
      { key: 3, label: '已完成' }
    ],
    active: -1,
    orders: [] as OrderItem[],
    page: 1,
    pageSize: 10,
    hasMore: true,
    loading: false
  },

  onShow() {
    this.load(true)
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) this.load(false)
  },

  pickStatus(e: any) {
    const key = Number(e.currentTarget.dataset.key)
    if (this.data.active === key) return
    this.setData({ active: key }, () => this.load(true))
  },

  async load(reset: boolean) {
    if (this.data.loading) return
    const page = reset ? 1 : this.data.page + 1
    this.setData({ loading: true })
    try {
      const params: any = { page, size: this.data.pageSize }
      if (this.data.active >= 0) params.status = this.data.active
      const res = await request({ url: '/api/shop/orders', data: params })
      const items: OrderItem[] = extractList(res as Result<any>).map((o: any) => {
        const meta = STATUS_META[Number(o.status)] || STATUS_META[4]
        const lines = Array.isArray(o.items) ? o.items : []
        return {
          id: Number(o.id),
          orderNo: o.orderNo || '',
          status: Number(o.status),
          statusText: meta.text,
          statusColor: meta.color,
          total: String(o.totalAmount ?? 0),
          itemCount: lines.reduce((s: number, it: any) => s + Number(it.quantity || 1), 0) || lines.length,
          thumbs: lines.slice(0, 3).map((it: any) => imageUrl(it.image || it.productImage || '')).filter(Boolean),
          firstProductId: Number(lines[0]?.productId || 0)
        }
      })
      this.setData({
        orders: reset ? items : this.data.orders.concat(items),
        page,
        hasMore: items.length >= this.data.pageSize
      })
    } catch (e: any) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
      if (reset) this.setData({ orders: [], hasMore: false })
    } finally {
      this.setData({ loading: false })
    }
  },

  // ─── 操作 ───
  cancelOrder(e: any) {
    const order = this.data.orders[e.currentTarget.dataset.idx as number]
    wx.showModal({
      title: '取消订单',
      content: `确定取消订单 ${order.orderNo}？`,
      success: async r => {
        if (!r.confirm) return
        try {
          await request({ url: `/api/shop/orders/${order.id}/cancel`, method: 'PATCH' })
          wx.showToast({ title: '已取消', icon: 'success' })
          this.load(true)
        } catch (err: any) {
          wx.showToast({ title: err.message || '取消失败', icon: 'none' })
        }
      }
    })
  },

  async payOrder(e: any) {
    const order = this.data.orders[e.currentTarget.dataset.idx as number]
    try {
      const pay = await request<any>({ url: `/api/shop/orders/${order.id}/pay`, method: 'POST' })
      const tx = pay.data?.transactionId || ('WX' + Date.now())
      await request({
        url: '/api/shop/pay/callback',
        method: 'POST',
        data: { orderNo: order.orderNo, transactionId: tx }
      })
      wx.showToast({ title: '支付成功', icon: 'success' })
      this.load(true)
    } catch (err: any) {
      wx.showToast({ title: err.message || '支付失败', icon: 'none' })
    }
  },

  remindShip() {
    wx.showToast({ title: '已提醒商家发货', icon: 'success' })
  },

  async confirmReceipt(e: any) {
    const order = this.data.orders[e.currentTarget.dataset.idx as number]
    try {
      await request({ url: `/api/shop/orders/${order.id}/confirm`, method: 'PATCH' })
      wx.showToast({ title: '已确认收货', icon: 'success' })
      this.load(true)
    } catch (err: any) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' })
    }
  },

  async buyAgain(e: any) {
    const order = this.data.orders[e.currentTarget.dataset.idx as number]
    if (!order.firstProductId) {
      wx.showToast({ title: '商品已下架', icon: 'none' })
      return
    }
    try {
      await request({ url: '/api/shop/cart', method: 'POST', data: { productId: order.firstProductId, quantity: 1 } })
      wx.navigateTo({ url: '/pages/cart/cart' })
    } catch (err: any) {
      wx.showToast({ title: err.message || '加购失败', icon: 'none' })
    }
  }
})
