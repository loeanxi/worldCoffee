// confirm.ts —— 确认订单：双模式 + 地址管理页联动 + Mock 微信支付回调
//   mode=cart（默认）：消费购物车，支持 cartIds 选中结算（未勾选商品留在购物车）
//   mode=buy：直购，传 productId/quantity，完全不动购物车
// 地址：点击地址卡跳 /pages/address/address?select=1 选择，onShow 回收 globalData.selectedAddress
// 支付链路：POST /orders → POST /orders/{id}/pay → POST /api/shop/pay/callback 落账
import { request, imageUrl, extractList, Result } from '../../utils/request'

interface ConfirmItem {
  id: number
  productName: string
  price: number
  image: string
  quantity: number
}

interface AddressVO {
  id: number
  receiverName: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  isDefault: boolean
}

Page({
  data: {
    mode: 'cart' as 'cart' | 'buy',
    buyProductId: 0,
    buyQty: 1,
    cartIds: [] as number[],
    items: [] as ConfirmItem[],
    goodsAmount: '0.00',
    addressId: 0,
    addressName: '',
    addressDetail: '',
    addressText: '',
    remark: '',
    loading: false,
    payOpen: false,
    paying: false,
    orderId: 0,
    orderNo: ''
  },

  onLoad(query: Record<string, string | undefined>) {
    const mode = query.mode === 'buy' ? 'buy' : 'cart'
    this.setData({
      mode,
      buyProductId: Number(query.productId || 0),
      buyQty: Math.max(1, Number(query.qty || 1))
    })
    if (mode === 'buy') this.loadBuyItem()
    else this.loadCart()
    this.loadDefaultAddress()
  },

  onShow() {
    const app = getApp() as any
    const picked: AddressVO | undefined = app.globalData?.selectedAddress
    if (picked) {
      this.applyAddress(picked)
      app.globalData.selectedAddress = null
    }
  },

  // ─── 商品清单 ───
  async loadBuyItem() {
    this.setData({ loading: true })
    try {
      const res = await request<any>({ url: `/api/shop/products/${this.data.buyProductId}` })
      const p = res.data || {}
      const item: ConfirmItem = {
        id: Number(p.id),
        productName: p.name || '商品',
        price: Number(p.price || 0),
        image: imageUrl((Array.isArray(p.images) ? p.images[0] : '') || ''),
        quantity: this.data.buyQty
      }
      this.setData({ items: [item], goodsAmount: (item.price * item.quantity).toFixed(2) })
    } catch (e: any) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async loadCart() {
    this.setData({ loading: true })
    try {
      const res = await request({ url: '/api/shop/cart' })
      const app = getApp() as any
      const pickedIds: number[] | undefined = app.globalData?.confirmCartIds
      app.globalData.confirmCartIds = null
      let list = extractList(res as Result<any>).map((c: any) => ({
        id: Number(c.id),
        productName: c.productName || '商品',
        price: Number(c.price || 0),
        image: imageUrl(c.image || ''),
        quantity: Number(c.quantity || 1)
      })) as ConfirmItem[]
      if (pickedIds && pickedIds.length) {
        const set = new Set(pickedIds)
        list = list.filter(i => set.has(i.id))
      }
      const sum = list.reduce((s, i) => s + i.price * i.quantity, 0)
      this.setData({ items: list, cartIds: list.map(i => i.id), goodsAmount: sum.toFixed(2) })
    } catch (e: any) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  // ─── 地址 ───
  async loadDefaultAddress() {
    try {
      const res = await request({ url: '/api/shop/addresses' })
      const list = extractList(res as Result<any>) as AddressVO[]
      const def = list.find(a => a.isDefault) || list[0]
      if (def && !this.data.addressId) this.applyAddress(def)
    } catch {
      /* 无地址时跳地址管理页新建 */
    }
  },

  applyAddress(a: AddressVO) {
    const region = [a.province, a.city, a.district].filter(Boolean).join(' ')
    this.setData({
      addressId: Number(a.id || 0),
      addressName: `${a.receiverName || ''} · ${a.phone || ''}`,
      addressDetail: `${region} ${a.detail || ''}`.trim(),
      addressText: `${region} ${a.detail || ''} ${a.receiverName || ''} ${a.phone || ''}`.trim()
    })
  },

  /** 跳地址管理页（选择模式），无地址时引导新建 */
  editAddress() {
    wx.navigateTo({ url: '/pages/address/address?select=1' })
  },

  onRemark(e: any) {
    this.setData({ remark: e.detail.value })
  },

  // ─── 提交与支付 ───
  async submit() {
    if (!this.data.items.length) {
      wx.showToast({ title: '没有可结算的商品', icon: 'none' })
      return
    }
    if (!this.data.addressText) {
      wx.showModal({
        title: '需要收货地址',
        content: '请先选择或新建一个收货地址',
        confirmText: '去选择',
        success: r => {
          if (r.confirm) this.editAddress()
        }
      })
      return
    }
    const payload: any = { address: this.data.addressText, remark: this.data.remark || undefined }
    if (this.data.mode === 'buy') {
      payload.productId = this.data.buyProductId
      payload.quantity = this.data.buyQty
    } else if (this.data.cartIds.length) {
      payload.cartIds = this.data.cartIds
    }
    try {
      const res = await request<any>({ url: '/api/shop/orders', method: 'POST', data: payload })
      const order = res.data || {}
      this.setData({ payOpen: true, orderId: Number(order.id || 0), orderNo: order.orderNo || '' })
    } catch (e: any) {
      wx.showToast({ title: e.message || '提交失败', icon: 'none' })
    }
  },

  /** 点「完成」：发起 Mock 支付并回调落账，随后跳订单列表 */
  async finishPay() {
    if (this.data.paying || !this.data.orderId) return
    this.setData({ paying: true })
    try {
      const pay = await request<any>({ url: `/api/shop/orders/${this.data.orderId}/pay`, method: 'POST' })
      const tx = pay.data?.transactionId || ('WX' + Date.now())
      await request({
        url: '/api/shop/pay/callback',
        method: 'POST',
        data: { orderNo: this.data.orderNo, transactionId: tx }
      })
      wx.showToast({ title: '支付成功', icon: 'success' })
      wx.redirectTo({ url: '/pages/orders/orders' })
    } catch (e: any) {
      this.setData({ paying: false })
      wx.showToast({ title: e.message || '支付失败', icon: 'none' })
    }
  }
})
