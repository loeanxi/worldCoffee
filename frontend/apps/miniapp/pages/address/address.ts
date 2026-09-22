// address.ts —— 地址管理：列表 / 选择回填 / 编辑 / 删除 / 设默认
import { request, extractList, Result } from '../../utils/request'

interface AddrItem {
  id: number
  receiverName: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  isDefault: boolean
  region: string
}

Page({
  data: {
    selectMode: false,
    list: [] as AddrItem[],
    loading: false
  },

  onLoad(query: Record<string, string | undefined>) {
    this.setData({ selectMode: query.select === '1' })
  },

  onShow() {
    this.load()
  },

  async load() {
    this.setData({ loading: true })
    try {
      const res = await request({ url: '/api/shop/addresses' })
      const list: AddrItem[] = extractList(res as Result<any>).map((a: any) => ({
        id: Number(a.id),
        receiverName: a.receiverName || '',
        phone: a.phone || '',
        province: a.province || '',
        city: a.city || '',
        district: a.district || '',
        detail: a.detail || '',
        isDefault: !!a.isDefault,
        region: [a.province, a.city, a.district, a.detail].filter(Boolean).join(' ')
      }))
      this.setData({ list })
    } catch (e: any) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  /** 选择模式：回填确认订单页 */
  pick(e: any) {
    if (!this.data.selectMode) return
    const item = this.data.list[e.currentTarget.dataset.idx as number]
    const app = getApp() as any
    app.globalData = app.globalData || {}
    app.globalData.selectedAddress = item
    wx.navigateBack()
  },

  add() {
    wx.navigateTo({ url: '/pages/address-edit/address-edit' })
  },

  edit(e: any) {
    const item = this.data.list[e.currentTarget.dataset.idx as number]
    wx.navigateTo({ url: '/pages/address-edit/address-edit?id=' + item.id })
  },

  remove(e: any) {
    const item = this.data.list[e.currentTarget.dataset.idx as number]
    wx.showModal({
      title: '删除地址',
      content: `确定删除「${item.receiverName} ${item.region}」？`,
      success: async r => {
        if (!r.confirm) return
        try {
          await request({ url: `/api/shop/addresses/${item.id}`, method: 'DELETE' })
          this.load()
        } catch (err: any) {
          wx.showToast({ title: err.message || '删除失败', icon: 'none' })
        }
      }
    })
  },

  /** 设默认：PUT 全量更新（AddressForm 无局部更新接口） */
  async setDefault(e: any) {
    const item = this.data.list[e.currentTarget.dataset.idx as number]
    if (item.isDefault) return
    try {
      await request({
        url: `/api/shop/addresses/${item.id}`,
        method: 'PUT',
        data: {
          receiverName: item.receiverName,
          phone: item.phone,
          province: item.province,
          city: item.city,
          district: item.district,
          detail: item.detail,
          isDefault: true
        }
      })
      wx.showToast({ title: '已设为默认', icon: 'success' })
      this.load()
    } catch (err: any) {
      wx.showToast({ title: err.message || '设置失败', icon: 'none' })
    }
  }
})
