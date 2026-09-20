<!-- ============================================================
     商城 Tab（移动端独立设计）
     顶栏：标题 + 搜索 + 购物车
     分类 chips + 领券 banner + 双列商品卡 + 无限加载
============================================================ -->
<template>
  <div class="min-h-screen" style="background: var(--m-bg);">
    <header class="m-topbar px-3 pt-3 pb-2">
      <div class="flex items-center gap-2.5">
        <h1 class="text-[17px] font-bold shrink-0" style="color: var(--m-ink);">商城</h1>
        <div v-if="!searchOpen" class="m-search-pill tap-scale" role="button" @click="openSearch">
          <Icon icon="material-symbols:search" class="w-4 h-4 shrink-0" />
          <span class="truncate">搜咖啡豆、器具、杯具…</span>
        </div>
        <div v-else class="flex-1 flex items-center gap-1.5">
          <input ref="searchInput" v-model="keyword" class="m-search-pill" style="color: var(--m-ink);" type="text" placeholder="搜索商品…" @keyup.enter="doSearch" />
          <button type="button" class="text-[13px] shrink-0 font-semibold tap-scale" style="color: var(--m-brand);" @click="doSearch">搜索</button>
          <button type="button" class="text-[13px] shrink-0 tap-scale m-ink-3" @click="clearSearch">取消</button>
        </div>
        <router-link to="/shop/cart" class="relative w-9 h-9 flex items-center justify-center rounded-full tap-scale shrink-0" style="background: var(--m-card);" aria-label="购物车">
          <Icon icon="material-symbols:shopping-cart-outline" class="w-[18px] h-[18px]" :style="{ color: 'var(--m-ink-2)' }" />
        </router-link>
      </div>

      <div class="m-scroll-x flex items-center gap-2 mt-2.5 pb-0.5">
        <MChip label="全部" :active="activeCategoryId === null && !keyword" @click="selectCategory(null)" />
        <MChip v-for="c in categories" :key="c.id" :label="c.name" :active="activeCategoryId === c.id" @click="selectCategory(c.id)" />
      </div>
    </header>

    <main class="px-2.5 pt-2.5">
      <!-- 领券 banner -->
      <router-link
        v-if="!keyword"
        to="/shop/coupons"
        class="m-card flex items-center justify-between px-4 py-3 mb-2.5 tap-scale"
        style="background: linear-gradient(100deg, var(--m-brand-soft), var(--m-accent-soft));"
      >
        <div>
          <p class="text-[14px] font-bold" style="color: var(--m-ink);">领券中心</p>
          <p class="text-[11px] mt-0.5" style="color: var(--m-ink-2);">新人券 / 满减券，下单更划算</p>
        </div>
        <span class="m-pill px-3 h-7 flex items-center text-[12px] font-semibold text-white" style="background: var(--m-brand);">去领券</span>
      </router-link>

      <div v-if="products.length" class="columns-2 gap-2.5">
        <article
          v-for="p in products"
          :key="p.id"
          class="m-card overflow-hidden mb-2.5 break-inside-avoid cursor-pointer tap-scale"
          @click="router.push(`/shop/product/${p.id}`)"
        >
          <img v-if="p.coverImage" :src="p.coverImage" class="w-full h-auto block" style="background: var(--m-brand-soft);" loading="lazy" alt="" />
          <div class="p-2.5">
            <h3 class="text-[13px] font-semibold leading-snug line-clamp-2" style="color: var(--m-ink);">{{ p.name }}</h3>
            <div class="mt-1.5 flex items-baseline justify-between">
              <span class="text-[15px] font-bold" style="color: var(--m-accent);">¥{{ p.price }}</span>
              <span class="text-[11px]" style="color: var(--m-ink-3);">已售 {{ p.salesCount || p.sales || 0 }}</span>
            </div>
          </div>
        </article>
      </div>

      <MEmpty v-else-if="!loading" text="暂时没有商品，换个分类看看" icon="material-symbols:storefront-outline" />

      <div v-if="loading" class="flex justify-center py-8">
        <Icon icon="svg-spinners:ring-resize" class="w-6 h-6" :style="{ color: 'var(--m-brand)' }" />
      </div>
      <p v-else-if="products.length && !hasMore" class="text-center py-6 text-[11px]" style="color: var(--m-ink-3);">— 没有更多了 —</p>
      <div ref="sentinel" style="height: 1px;" />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { shopApi, extractApiError } from '@wc/shared'
import { MChip, MEmpty } from '../../mui'

const router = useRouter()

const categories = ref<any[]>([])
const activeCategoryId = ref<number | null>(null)
const products = ref<any[]>([])
const loading = ref(false)
const hasMore = ref(true)
const page = ref(1)
const pageSize = 10
const sentinel = ref(null)
let observer: IntersectionObserver | null = null

const searchOpen = ref(false)
const searchInput = ref(null)
const keyword = ref('')

function openSearch() {
  searchOpen.value = true
  setTimeout(() => searchInput.value?.focus(), 50)
}
function doSearch() {
  if (!keyword.value.trim()) return
  activeCategoryId.value = null
  fetchProducts(true)
}
function clearSearch() {
  searchOpen.value = false
  keyword.value = ''
  fetchProducts(true)
}
function selectCategory(id) {
  if (activeCategoryId.value === id) return
  activeCategoryId.value = id
  keyword.value = ''
  fetchProducts(true)
}

function extractList(res) {
  if (!res || !res.data) return []
  if (Array.isArray(res.data)) return res.data
  if (Array.isArray(res.data.data)) return res.data.data
  if (Array.isArray(res.data.records)) return res.data.records
  if (Array.isArray(res.data.list)) return res.data.list
  return []
}

async function fetchProducts(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 1
    products.value = []
    hasMore.value = true
  }
  loading.value = true
  try {
    let res
    if (keyword.value.trim()) {
      res = await shopApi.searchProducts(keyword.value.trim(), { page: page.value, size: pageSize })
    } else {
      const params: any = { page: page.value, size: pageSize }
      if (activeCategoryId.value !== null) params.categoryId = activeCategoryId.value
      res = await shopApi.getProducts(params)
    }
    if (res && res.code !== 200) throw new Error(res.msg || '加载失败')
    const list = extractList(res)
    if (list.length > 0) {
      const exist = new Set(products.value.map(p => p.id))
      products.value = [...products.value, ...list.filter(p => !exist.has(p.id))]
      hasMore.value = list.length >= pageSize
    } else {
      hasMore.value = false
    }
  } catch (e) {
    hasMore.value = false
    console.error(extractApiError(e))
  } finally {
    loading.value = false
    nextTick(setupObserver)
  }
}

function setupObserver() {
  if (typeof window === 'undefined' || !window.IntersectionObserver) return
  if (!observer) {
    observer = new IntersectionObserver(entries => {
      if (!entries.some(e => e.isIntersecting)) return
      if (!hasMore.value || loading.value || !products.value.length) return
      page.value++
      fetchProducts()
    }, { rootMargin: '480px 0px' })
  }
  observer.disconnect()
  if (sentinel.value) observer.observe(sentinel.value)
}

onMounted(async () => {
  try {
    const res = await shopApi.listCategories()
    if (res && res.code === 200) categories.value = Array.isArray(res.data) ? res.data : []
  } catch { /* 分类失败不阻塞商品流 */ }
  fetchProducts(true)
})
onUnmounted(() => observer?.disconnect())
</script>
