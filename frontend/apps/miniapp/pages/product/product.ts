// product.ts —— 商品详情 + 规格半屏 Sheet
// 「立即购买」走后端直购（CreateOrderFrom.productId/quantity），不再污染购物车；
// 购物车结算支持 cartIds 选中结算，二者互不干扰。
import { request, imageUrl, parseImages } from '../../utils/request'
import { isLoggedIn } from '../../utils/auth'

Page({
  data: {
    id: 0,
    images: [] as string[],
    price: '0',
    name: '',
    description: '',
    origin: '',
    roastLevel: '',
    weight: '',
    stock: null as number | null,
    sales: 0,
    qty: 1,
    sheetOpen: false,
    pendingBuy: false
  },

  onLoad(query: Record<string, string | undefined>) {
    const id = Number(query.id || 0)
    this.setData({ id })
    if (id) this.loadDetail(id)
  },

  async loadDetail(id: number) {
    try {
      const res = await request<any>({ url: `/api/shop/products/${id}` })
      const p = res.data || {}
      this.setData({
        images: parseImages(p.images).map(imageUrl),
        price: String(p.price ?? 0),
        name: p.name || '',
        description: p.description || '',
        origin: p.origin || '',
        roastLevel: p.roastLevel || '',
        weight: p.weight || '',
        stock: p.stock ?? null,
        sales: Number(p.sales ?? 0)
      })
    } catch (e: any) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  // ─── Sheet ──
  openSheet() {
    this.setData({ sheetOpen: true, pendingBuy: false })
  },
  openSheetForBuy() {
    this.setData({ sheetOpen: true, pendingBuy: true })
  },
  closeSheet() {
    this.setData({ sheetOpen: false })
  },
  noop() {
    /* 阻止冒泡到 mask */
  },
  decQty() {
    if (this.data.qty > 1) this.setData({ qty: this.data.qty - 1 })
  },
  incQty() {
    const max = this.data.stock === null ? 99 : this.data.stock
    if (this.data.qty < max) this.setData({ qty: this.data.qty + 1 })
    else wx.showToast({ title: '库存不足', icon: 'none' })
  },

  // ─── 加购 / 立即购买 ───
  requireLogin(): boolean {
    if (isLoggedIn()) return true
    wx.navigateTo({ url: '/pages/login/login' })
    return false
  },

  async addToCartOnly() {
    if (!this.requireLogin()) return
    try {
      await request({ url: '/api/shop/cart', method: 'POST', data: { productId: this.data.id, quantity: this.data.qty } })
      this.setData({ sheetOpen: false })
      wx.showToast({ title: '已加入购物车', icon: 'success' })
    } catch (e: any) {
      wx.showToast({ title: e.message || '加购失败', icon: 'none' })
    }
  },

  async buyNow() {
    if (!this.requireLogin()) return
    this.setData({ sheetOpen: false })
    wx.navigateTo({
      url: `/pages/confirm/confirm?mode=buy&productId=${this.data.id}&qty=${this.data.qty}`
    })
  },

  addCart() {
    this.addToCartOnly()
  },

  goCart() {
    if (!this.requireLogin()) return
    wx.navigateTo({ url: '/pages/cart/cart' })
  }
})
