// address-edit.ts —— 地址新建/编辑：GET /addresses/{id} 回填，POST/PUT 保存
import { request } from '../../utils/request'

interface AddrForm {
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
    id: 0,
    saving: false,
    form: {
      receiverName: '',
      phone: '',
      province: '',
      city: '',
      district: '',
      detail: '',
      isDefault: false
    } as AddrForm
  },

  onLoad(query: Record<string, string | undefined>) {
    const id = Number(query.id || 0)
    if (id) {
      this.setData({ id })
      wx.setNavigationBarTitle({ title: '编辑地址' })
      this.load(id)
    } else {
      wx.setNavigationBarTitle({ title: '新增地址' })
    }
  },

  async load(id: number) {
    try {
      const res = await request<any>({ url: `/api/shop/addresses/${id}` })
      const a = res.data || {}
      this.setData({
        form: {
          receiverName: a.receiverName || '',
          phone: a.phone || '',
          province: a.province || '',
          city: a.city || '',
          district: a.district || '',
          detail: a.detail || '',
          isDefault: !!a.isDefault
        }
      })
    } catch (e: any) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  onInput(e: any) {
    const key = e.currentTarget.dataset.key as keyof AddrForm
    this.setData({ [`form.${key}`]: e.detail.value } as any)
  },

  onDefault(e: any) {
    this.setData({ 'form.isDefault': e.detail.value })
  },

  validate(): string {
    const f = this.data.form
    if (!f.receiverName.trim()) return '请填写收货人'
    if (!/^1\d{10}$/.test(f.phone.trim())) return '请填写 11 位手机号'
    if (!f.province.trim() || !f.city.trim()) return '请填写省份和城市'
    if (!f.detail.trim()) return '请填写详细地址'
    return ''
  },

  async save() {
    if (this.data.saving) return
    const err = this.validate()
    if (err) {
      wx.showToast({ title: err, icon: 'none' })
      return
    }
    this.setData({ saving: true })
    try {
      const payload = {
        receiverName: this.data.form.receiverName.trim(),
        phone: this.data.form.phone.trim(),
        province: this.data.form.province.trim(),
        city: this.data.form.city.trim(),
        district: this.data.form.district.trim(),
        detail: this.data.form.detail.trim(),
        isDefault: this.data.form.isDefault
      }
      if (this.data.id) {
        await request({ url: `/api/shop/addresses/${this.data.id}`, method: 'PUT', data: payload })
      } else {
        await request({ url: '/api/shop/addresses', method: 'POST', data: payload })
      }
      wx.showToast({ title: '已保存', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 600)
    } catch (e: any) {
      wx.showToast({ title: e.message || '保存失败', icon: 'none' })
    } finally {
      this.setData({ saving: false })
    }
  }
})
