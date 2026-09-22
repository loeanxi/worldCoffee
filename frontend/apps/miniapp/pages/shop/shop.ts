// shop.ts —— 商城 Tab：分类筛选 / ES 搜索 / 双列商品 / 购物车角标
import { request, imageUrl, parseImages, extractList, Result } from '../../utils/request'
import { isLoggedIn } from '../../utils/auth'

interface ProdItem {
  id: number
  name: string
  price: string
  cover: string
  sales: number
}

Page({
  data: {
    statusBarHeight: 20,
    navHeight: 64,
    searchOpen: false,
    keyword: '',
    searching: false,
    categoryId: '' as '' | number,
    categories: [] as { id: number; name: string }[],
    products: [] as ProdItem[],
    leftCol: [] as ProdItem[],
    rightCol: [] as ProdItem[],
    page: 1,
    pageSize: 10,
    hasMore: true,
    loading: false,
    cartCount: 0
  },

  onLoad() {
    const sb = wx.getWindowInfo().statusBarHeight || 20
    this.setData({ statusBarHeight: sb, navHeight: sb + 44 })
    this.loadCategories()
    this.loadProducts(true)
  },

  onShow() {
    this.loadCartCount()
  },

  onPullDownRefresh() {
    this.loadProducts(true).finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading && !this.data.searching) this.loadProducts(false)
  },

  async loadCartCount() {
    if (!isLoggedIn()) {
      this.setData({ cartCount: 0 })
      return
    }
    try {
      const res = await request({ url: '/api/shop/cart' })
      this.setData({ cartCount: extractList(res as Result<any>).length })
    } catch {
      this.setData({ cartCount: 0 })
    }
  },

  async loadCategories() {
    try {
      const res = await request({ url: '/api/shop/categories' })
      this.setData({ categories: extractList(res as Result<any>) })
    } catch {
      /* 分类失败不阻塞列表 */
    }
  },

  // ─── 搜索 / 分类 ───
  openSearch() {
    this.setData({ searchOpen: true })
  },
  onInput(e: any) {
    this.setData({ keyword: e.detail.value })
  },
  doSearch() {
    const kw = this.data.keyword.trim()
    if (!kw) return
    this.setData({ searching: true }, () => this.loadProducts(true))
  },
  clearSearch() {
    const was = this.data.searching
    this.setData({ searchOpen: false, keyword: '', searching: false }, () => {
      if (was) this.loadProducts(true)
    })
  },
  pickCategory(e: any) {
    const id = e.currentTarget.dataset.id
    const next = id === '' || id === undefined ? '' : Number(id)
    if (this.data.categoryId === next) return
    this.setData({ categoryId: next, searching: false }, () => this.loadProducts(true))
  },

  // ─── 商品列表 ───
  async loadProducts(reset: boolean): Promise<void> {
    if (this.data.loading) return
    const page = reset ? 1 : this.data.page + 1
    this.setData({ loading: true })
    try {
      let res: Result<any>
      if (this.data.searching) {
        res = await request({ url: '/api/shop/products/search?keyword=' + encodeURIComponent(this.data.keyword) })
      } else {
        const params: any = { page, size: this.data.pageSize }
        if (this.data.categoryId !== '') params.categoryId = this.data.categoryId
        res = await request({ url: '/api/shop/products', data: params })
      }
      const items: ProdItem[] = extractList(res).map((p: any) => ({
        id: Number(p.id),
        name: p.name || '未命名商品',
        price: String(p.price ?? 0),
        cover: imageUrl(parseImages(p.images)[0] || ''),
        sales: Number(p.sales ?? p.salesCount ?? 0)
      }))
      const products = reset || this.data.searching ? items : this.data.products.concat(items)
      this.setData({
        products,
        leftCol: products.filter((_, i) => i % 2 === 0),
        rightCol: products.filter((_, i) => i % 2 === 1),
        page,
        hasMore: !this.data.searching && items.length >= this.data.pageSize
      })
    } catch (e: any) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
      if (reset) this.setData({ products: [], leftCol: [], rightCol: [], hasMore: false })
    } finally {
      this.setData({ loading: false })
    }
  },

  // ─── 交互 ───
  openProduct(e: any) {
    wx.navigateTo({ url: '/pages/product/product?id=' + e.currentTarget.dataset.id })
  },
  goCart() {
    if (!isLoggedIn()) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    wx.navigateTo({ url: '/pages/cart/cart' })
  },
  goBrowse() {
    wx.pageScrollTo({ selector: '.wf', offsetTop: -80 })
  }
})
