// cart.ts —— 购物车：勾选 / 数量步进 / 删除 / 结算
// 勾选态通过 globalData.confirmCartIds 传给确认订单页，
// 后端 CreateOrderFrom.cartIds 只消费勾选项，未勾选商品留在购物车。
import { request, imageUrl, extractList, Result } from '../../utils/request'

interface CartItem {
  id: number
  productId: number
  productName: string
  price: number
  image: string
  quantity: number
  stock: number
  selected: boolean
}

Page({
  data: {
    items: [] as CartItem[],
    total: '0.00',
    selectedCount: 0,
    allSelected: true,
    loading: false
  },

  onShow() {
    this.load()
  },

  async load() {
    this.setData({ loading: true })
    try {
      const res = await request({ url: '/api/shop/cart' })
      const items: CartItem[] = extractList(res as Result<any>).map((c: any) => ({
        id: Number(c.id),
        productId: Number(c.productId),
        productName: c.productName || '商品',
        price: Number(c.price || 0),
        image: imageUrl(c.image || ''),
        quantity: Number(c.quantity || 1),
        stock: Number(c.stock ?? 99),
        selected: true
      }))
      this.setData({ items })
      this.recompute()
    } catch (e: any) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  recompute() {
    const sel = this.data.items.filter(i => i.selected)
    const sum = sel.reduce((s, i) => s + i.price * i.quantity, 0)
    this.setData({
      total: sum.toFixed(2),
      selectedCount: sel.length,
      allSelected: sel.length === this.data.items.length && this.data.items.length > 0
    })
  },

  toggleSelect(e: any) {
    const idx = e.currentTarget.dataset.idx as number
    const items = this.data.items.slice()
    items[idx].selected = !items[idx].selected
    this.setData({ items })
    this.recompute()
  },

  toggleAll() {
    const next = !this.data.allSelected
    this.setData({ items: this.data.items.map(i => ({ ...i, selected: next })) })
    this.recompute()
  },

  async setQty(idx: number, qty: number) {
    const item = this.data.items[idx]
    if (!item || qty < 1) return
    if (qty > item.stock) {
      wx.showToast({ title: '库存不足', icon: 'none' })
      return
    }
    try {
      await request({ url: `/api/shop/cart/${item.id}?quantity=${qty}`, method: 'PUT' })
      const items = this.data.items.slice()
      items[idx].quantity = qty
      this.setData({ items })
      this.recompute()
    } catch (e: any) {
      wx.showToast({ title: e.message || '修改失败', icon: 'none' })
    }
  },

  inc(e: any) {
    const idx = e.currentTarget.dataset.idx as number
    this.setQty(idx, this.data.items[idx].quantity + 1)
  },

  dec(e: any) {
    const idx = e.currentTarget.dataset.idx as number
    this.setQty(idx, this.data.items[idx].quantity - 1)
  },

  remove(e: any) {
    const idx = e.currentTarget.dataset.idx as number
    const item = this.data.items[idx]
    wx.showModal({
      title: '删除商品',
      content: `确定把「${item.productName}」移出购物车？`,
      success: async r => {
        if (!r.confirm) return
        try {
          await request({ url: `/api/shop/cart/${item.id}`, method: 'DELETE' })
          this.load()
        } catch (err: any) {
          wx.showToast({ title: err.message || '删除失败', icon: 'none' })
        }
      }
    })
  },

  settle() {
    if (!this.data.selectedCount) {
      wx.showToast({ title: '请先勾选商品', icon: 'none' })
      return
    }
    const app = getApp() as any
    app.globalData = app.globalData || {}
    app.globalData.confirmCartIds = this.data.items
      .filter(i => i.selected)
      .map(i => i.id)
    wx.navigateTo({ url: '/pages/confirm/confirm' })
  },

  openProduct(e: any) {
    wx.navigateTo({ url: '/pages/product/product?id=' + e.currentTarget.dataset.id })
  }
})
