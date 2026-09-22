// publish.ts —— 发布笔记：选图 → 上传 /api/coffee/upload → POST /api/coffee/posts
import { request, uploadImage } from '../../utils/request'

Page({
  data: {
    images: [] as string[],
    title: '',
    content: '',
    topicOptions: ['咖啡馆', '手冲', '拉花', '甜品', '冷萃'],
    topics: [] as string[],
    submitting: false
  },

  onLoad() {
    this.restoreDraft()
  },

  restoreDraft() {
    try {
      const raw = wx.getStorageSync('wc_draft') as string
      if (!raw) return
      const d = JSON.parse(raw)
      this.setData({ title: d.title || '', content: d.content || '', topics: d.topics || [] })
    } catch {
      /* 无草稿 */
    }
  },

  chooseImages() {
    const remain = 9 - this.data.images.length
    wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      sizeType: ['compressed'],
      success: r => {
        const paths = r.tempFiles.map(f => f.tempFilePath)
        this.setData({ images: this.data.images.concat(paths) })
      }
    })
  },

  preview(e: any) {
    wx.previewImage({ urls: this.data.images, current: this.data.images[e.currentTarget.dataset.idx as number] })
  },

  onTitle(e: any) {
    this.setData({ title: e.detail.value })
  },
  onContent(e: any) {
    this.setData({ content: e.detail.value })
  },

  toggleTopic(e: any) {
    const t = e.currentTarget.dataset.topic as string
    const topics = this.data.topics.slice()
    const i = topics.indexOf(t)
    if (i >= 0) topics.splice(i, 1)
    else topics.push(t)
    this.setData({ topics })
  },

  saveDraft() {
    wx.setStorageSync('wc_draft', JSON.stringify({
      title: this.data.title,
      content: this.data.content,
      topics: this.data.topics
    }))
    wx.showToast({ title: '草稿已保存', icon: 'success' })
  },

  async submit() {
    if (this.data.submitting) return
    const title = this.data.title.trim()
    if (!title) {
      wx.showToast({ title: '请填写标题', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    wx.showLoading({ title: '上传中…', mask: true })
    try {
      const urls: string[] = []
      for (const p of this.data.images) {
        urls.push(await uploadImage(p))
      }
      await request({
        url: '/api/coffee/posts',
        method: 'POST',
        data: {
          title,
          content: this.data.content,
          images: urls,
          topics: this.data.topics,
          noteType: urls.length ? 'IMAGE' : 'TEXT'
        }
      })
      wx.removeStorageSync('wc_draft')
      wx.hideLoading()
      wx.showToast({ title: '发布成功，待审核', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 800)
    } catch (e: any) {
      wx.hideLoading()
      wx.showToast({ title: e.message || '发布失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
