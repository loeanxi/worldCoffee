<template>
  <div class="min-h-screen pb-20 md:pb-6">
    <!-- Header -->
    <header class="sticky top-0 z-40 bg-surface/90 backdrop-blur-xl border-b border-line/30">
      <div class="max-w-6xl mx-auto px-3 md:px-6 h-16 flex items-center gap-3">
        <!-- 返回按钮 -->
        <button
          class="flex items-center justify-center w-9 h-9 rounded-2xl bg-surface-elevated shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.05)] hover:shadow-[0_2px_12px_rgba(62,39,35,0.08)] transition-shadow flex-shrink-0 tap-scale"
          @click="router.push('/')"
        >
          <Icon icon="material-symbols:arrow-back" class="w-5 h-5 text-brand" />
        </button>

        <!-- 标题 -->
        <div class="flex items-center gap-2 flex-1 min-w-0">
          <WorldCoffeeLogoMini :size="36" :with-circle="false" />
          <div class="hidden sm:block">
            <div class="text-base font-bold text-brand leading-tight">咖啡商城</div>
            <div class="text-[11px] text-ink-muted">精选好豆 · 原厂直发</div>
          </div>
        </div>

        <!-- 搜索框 -->
        <div class="flex-1 relative min-w-0">
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索咖啡..."
            class="w-full h-9 pl-9 pr-8 rounded-xl bg-surface-elevated border border-line/30 text-sm text-ink placeholder:text-ink-muted/60 focus:outline-none focus:border-brand/50 focus:ring-2 focus:ring-brand/10 transition-all"
            @keyup.enter="handleSearch"
            @keydown.escape="clearSearch"
          />
          <Icon icon="material-symbols:search" class="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-ink-muted/60 pointer-events-none" />
          <button
            v-if="searchKeyword"
            class="absolute right-2 top-1/2 -translate-y-1/2 p-0.5 rounded-full hover:bg-line/30 transition-colors"
            @click="clearSearch"
          >
            <Icon icon="material-symbols:close" class="w-3.5 h-3.5 text-ink-muted" />
          </button>
        </div>

        <!-- 右侧操作 -->
        <div class="flex items-center gap-2 flex-shrink-0">
          <router-link
            to="/shop/coupons"
            class="p-2.5 rounded-2xl bg-surface-elevated shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.05)] hover:shadow-[0_2px_12px_rgba(62,39,35,0.08)] transition-shadow tap-scale"
          >
            <Icon icon="material-symbols:confirmation-number-outline" class="w-5 h-5 text-brand" />
          </router-link>
          <router-link
            v-if="isLoggedIn"
            to="/shop/cart"
            class="relative p-2.5 rounded-2xl bg-surface-elevated shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.05)] hover:shadow-[0_2px_12px_rgba(62,39,35,0.08)] transition-shadow tap-scale"
          >
            <Icon icon="material-symbols:shopping-cart-outline" class="w-5 h-5 text-brand" />
            <span
              v-if="cartCount > 0"
              class="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1.5 bg-[#EF4444] text-white text-[10px] font-bold rounded-full flex items-center justify-center"
            >
              {{ cartCount > 99 ? '99+' : cartCount }}
            </span>
          </router-link>
        </div>
      </div>
    </header>

    <!-- Main -->
    <main class="max-w-6xl mx-auto px-3 md:px-6 pt-4 pb-8">
      <!-- 横幅 -->
      <div class="rounded-[24px] overflow-hidden relative mb-5 shadow-[0_4px_20px_rgba(62,39,35,0.18)]" style="background: linear-gradient(135deg, #2C1810 0%, #3E2723 35%, #5D4037 70%, #4E342E 100%);">
        <!-- 装饰：细腻点阵纹理 -->
        <svg class="absolute inset-0 w-full h-full opacity-[0.07]" xmlns="http://www.w3.org/2000/svg"><defs><pattern id="shop-dots" x="0" y="0" width="18" height="18" patternUnits="userSpaceOnUse"><circle cx="2" cy="2" r="1.2" fill="white"/></pattern></defs><rect width="100%" height="100%" fill="url(#shop-dots)"/></svg>
        <!-- 装饰：渐变圆环 -->
        <div class="absolute -right-6 -top-6 w-36 h-36 rounded-full border-[6px] border-white/[0.07]"></div>
        <div class="absolute -right-2 top-10 w-24 h-24 rounded-full border-[3px] border-white/[0.05]"></div>
        <div class="absolute -left-4 -bottom-4 w-28 h-28 rounded-full border-[5px] border-white/[0.05]"></div>
        <!-- 装饰：品牌 Logo 水印 -->
        <div class="absolute right-4 bottom-3 opacity-[0.08]">
          <WorldCoffeeLogoMini :size="88" :with-circle="false" />
        </div>
        <div class="relative z-10 p-5 md:p-8">
          <div class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/15 backdrop-blur-sm mb-3">
            <span class="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse"></span>
            <span class="text-[11px] uppercase tracking-wider text-white/90 font-semibold">限时优惠</span>
          </div>
          <h2 class="text-2xl md:text-3xl font-bold text-white leading-tight mb-2">
            好豆 · 好味 · 好价格
          </h2>
          <p class="text-sm text-white/75 mb-4 max-w-md">精选世界各地咖啡豆，新鲜烘焙直送到家</p>
          <div class="flex flex-wrap items-center gap-2">
            <span class="text-[11px] font-semibold px-3 py-1.5 rounded-full bg-white/12 text-white/90 backdrop-blur-sm">#新鲜烘焙</span>
            <span class="text-[11px] font-semibold px-3 py-1.5 rounded-full bg-white/12 text-white/90 backdrop-blur-sm">#原厂直发</span>
            <span class="text-[11px] font-semibold px-3 py-1.5 rounded-full bg-white/12 text-white/90 backdrop-blur-sm">#包邮到家</span>
            <span class="mx-1 w-px h-4 bg-white/20 hidden sm:inline-block"></span>
            <button class="text-[11px] font-bold px-4 py-1.5 rounded-full bg-white text-[#3E2723] hover:bg-white/90 transition-colors tap-scale hidden sm:inline-flex items-center gap-1">
              立即选购
              <Icon icon="material-symbols:arrow-forward" class="w-3 h-3" />
            </button>
          </div>
        </div>
      </div>

      <!-- 分类过滤 -->
      <div class="flex gap-2 overflow-x-auto no-scrollbar mb-4">
        <button
          class="shrink-0 flex items-center gap-1.5 px-4 py-2.5 rounded-2xl text-[13px] font-semibold transition-all tap-scale"
          :class="activeCategoryId === null
            ? 'brand-gradient-btn shadow-[0_2px_12px_rgba(109,76,65,0.25)]'
            : 'bg-surface-elevated text-brand/80 hover:text-brand shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.05)] hover:shadow-[0_2px_12px_rgba(109,76,65,0.08)]'"
          @click="activeCategoryId = null; fetchProducts(true)"
        >
          <Icon icon="material-symbols:coffee" class="w-4 h-4" />
          全部
        </button>
        <button
          v-for="cat in categories"
          :key="cat.id"
          :class="[
            'shrink-0 px-4 py-2.5 rounded-2xl text-[13px] font-semibold transition-all tap-scale',
            activeCategoryId === cat.id
              ? 'brand-gradient-btn shadow-[0_2px_12px_rgba(109,76,65,0.25)]'
              : 'bg-surface-elevated text-brand/80 hover:text-brand shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.05)] hover:shadow-[0_2px_12px_rgba(109,76,65,0.08)]'
          ]"
          @click="activeCategoryId = cat.id; fetchProducts(true)"
        >
          {{ cat.name }}
        </button>
      </div>

      <!-- 骨架屏 -->
      <div v-if="loading && products.length === 0" class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 md:gap-4">
        <div v-for="n in 8" :key="n" class="rounded-2xl overflow-hidden bg-surface-elevated shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.05)]">
          <div class="skeleton aspect-square" />
          <div class="p-3 space-y-2.5">
            <div class="skeleton h-3.5 w-full rounded" />
            <div class="skeleton h-3.5 w-1/2 rounded" />
            <div class="flex items-center justify-between pt-1">
              <div class="skeleton h-4 w-16 rounded" />
              <div class="skeleton h-3 w-12 rounded" />
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else-if="!loading && products.length === 0" class="py-20 text-center">
        <div class="w-28 h-28 mx-auto mb-4 rounded-3xl brand-placeholder flex items-center justify-center shadow-inner">
          <WorldCoffeeLogoMini :size="56" :with-circle="false" />
        </div>
        <h3 class="text-[17px] font-bold text-brand mb-1">暂无商品</h3>
        <p class="text-[13px] text-ink-muted">商城正在备货中，敬请期待</p>
      </div>

      <!-- 商品网格 -->
      <div v-else class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 md:gap-4">
        <article
          v-for="(product, i) in products"
          :key="product.id"
          class="group bg-surface-elevated rounded-2xl overflow-hidden shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.05)] border border-transparent hover:border-line/50 hover:shadow-[0_4px_20px_rgba(62,39,35,0.12)] transition-all duration-300 cursor-pointer animate-fade-up"
          :style="{ animationDelay: `${(i % 12) * 40}ms` }"
          @click="goToDetail(product)"
        >
          <!-- 图片 -->
          <div class="relative overflow-hidden bg-surface-soft aspect-square">
            <img
              v-if="product.images && product.images.length"
              :src="product.images[0]"
              :alt="product.name"
              class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-[1.06]"
              loading="lazy"
              @error="handleImgError($event, product)"
            />
            <div v-if="imgErrors[product.id]" class="absolute inset-0 flex items-center justify-center brand-placeholder">
              <WorldCoffeeLogoMini :size="40" :with-circle="false" />
            </div>
            <div v-else-if="!product.images || !product.images.length" class="absolute inset-0 flex items-center justify-center brand-placeholder">
              <WorldCoffeeLogoMini :size="40" :with-circle="false" />
            </div>

            <!-- 热销徽章 -->
            <span
              v-if="(product.sales || 0) >= 50"
              class="absolute top-2 left-2 bg-gradient-to-br from-rose-500 to-orange-500 text-white text-[10px] font-bold px-2.5 py-1 rounded-full shadow-[0_2px_8px_rgba(232,139,139,0.4)] flex items-center gap-1"
            >
              <Icon icon="material-symbols:local-fire-department" class="w-3 h-3" />
              热销
            </span>

            <!-- 库存不足 -->
            <span
              v-else-if="product.stock !== undefined && product.stock > 0 && product.stock < 10"
              class="absolute top-2 left-2 bg-gradient-to-br from-amber-400 to-amber-600 text-white text-[10px] font-bold px-2.5 py-1 rounded-full shadow-sm"
            >
              仅剩 {{ product.stock }}
            </span>

            <!-- 售罄 -->
            <div v-if="product.stock === 0" class="absolute inset-0 bg-brand/30 backdrop-blur-[2px] flex items-center justify-center">
              <span class="text-white font-bold text-sm px-4 py-1.5 rounded-full bg-brand/70">已售罄</span>
            </div>
          </div>

          <!-- 内容区 -->
          <div class="p-3">
            <h3 class="text-[13px] font-bold text-brand leading-snug line-clamp-2 min-h-[36px] mb-2">
              {{ product.name }}
            </h3>

            <!-- 烘焙度/产地 -->
            <div class="flex flex-wrap items-center gap-1 mb-2.5">
              <span v-if="product.roastLevel" class="inline-flex items-center gap-1 bg-surface-soft text-brand text-[10.5px] px-1.5 py-1 rounded-lg font-medium">
                {{ product.roastLevel }}
              </span>
              <span v-if="product.origin" class="inline-flex items-center gap-1 bg-blue-50/70 text-blue-600 text-[10.5px] px-1.5 py-1 rounded-lg font-medium truncate">
                {{ product.origin.length > 8 ? product.origin.slice(0, 8) + '…' : product.origin }}
              </span>
            </div>

            <!-- 价格 + 销量 -->
            <div class="flex items-end justify-between mb-3">
              <div class="flex items-baseline gap-0.5">
                <span class="text-[11px] text-amber font-bold">¥</span>
                <span class="text-xl font-bold text-brand leading-none">{{ formatPrice(product.price) }}</span>
              </div>
              <span class="text-[11px] text-ink-muted">{{ formatSales(product.sales) }} 已售</span>
            </div>

            <!-- 加入购物车按钮 -->
            <button
              :disabled="!!addCartLoading[product.id] || product.stock === 0"
              class="w-full flex items-center justify-center gap-1.5 py-2.5 rounded-xl text-[12.5px] font-bold transition-all brand-gradient-btn shadow-[0_2px_8px_rgba(109,76,65,0.25)] hover:shadow-[0_4px_14px_rgba(109,76,65,0.35)] hover:-translate-y-0.5 disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:translate-y-0 tap-scale"
              @click.stop="handleAddToCart(product)"
            >
              <Icon v-if="!addCartLoading[product.id]" icon="material-symbols:shopping-bag" class="w-3.5 h-3.5" />
              <Icon v-else icon="material-symbols:refresh" class="w-3.5 h-3.5 animate-spin" />
              <span>{{ product.stock === 0 ? '已售罄' : '加入购物车' }}</span>
            </button>
          </div>
        </article>
      </div>

      <!-- 加载更多 -->
      <div v-if="hasMore && products.length > 0" class="flex justify-center py-8">
        <button
          class="inline-flex items-center gap-2 px-6 py-3 rounded-2xl bg-surface-elevated shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.05)] text-brand text-sm font-semibold tap-scale transition-all hover:shadow-[0_2px_12px_rgba(62,39,35,0.08)] disabled:opacity-50"
          :disabled="loading"
          @click="fetchProducts()"
        >
          <Icon v-if="loading" icon="material-symbols:refresh" class="w-4 h-4 animate-spin" />
          <Icon v-else icon="material-symbols:expand-more" class="w-4 h-4" />
          {{ loading ? '加载中...' : '加载更多' }}
        </button>
      </div>

      <!-- 到底啦 -->
      <div v-if="!hasMore && !loading && products.length > 0" class="flex justify-center py-8">
        <div class="flex items-center gap-2 text-[12px] text-ink-muted">
          <div class="w-12 h-px bg-line/50"></div>
          <span>— 已经到底啦 —</span>
          <div class="w-12 h-px bg-line/50"></div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, inject } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { shopApi, getApiError } from '../api'
import { useAuth } from '../composables/useAuth'
import WorldCoffeeLogoMini from '../components/WorldCoffeeLogoMini.vue'

const router = useRouter()
const toast = inject('toast')
const { isLoggedIn } = useAuth()

const products = ref([])
const imgErrors = reactive({})
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const pageSize = 12
const addCartLoading = reactive({})
const cartCount = ref(0)
const activeCategoryId = ref(null)
const categories = ref([])
const searchKeyword = ref('')
const isSearchMode = ref(false)

function handleImgError(e, product) {
  if (product?.id != null) imgErrors.value[product.id] = true
}

function formatPrice(price) {
  if (price == null) return '0.00'
  const num = Number(price)
  if (Number.isInteger(num)) return num.toFixed(0)
  return num.toFixed(2)
}

function formatSales(sales) {
  if (!sales) return 0
  if (sales >= 10000) return (sales / 10000).toFixed(1) + 'w'
  if (sales >= 1000) return (sales / 1000).toFixed(1) + 'k'
  return sales
}

function extractList(res) {
  if (!res || !res.data) return []
  if (Array.isArray(res.data)) return res.data
  if (Array.isArray(res.data.data)) return res.data.data
  if (Array.isArray(res.data.records)) return res.data.records
  if (Array.isArray(res.data.list)) return res.data.list
  return []
}

async function fetchCategories() {
  try {
    const res = await shopApi.listCategories()
    if (res && res.code === 200) {
      categories.value = Array.isArray(res.data) ? res.data : []
    }
  } catch (e) {
    console.warn('获取分类失败', e)
  }
}

async function fetchProducts(reset = false) {
  if (loading.value) return
  if (reset) { page.value = 1; products.value = []; hasMore.value = true }

  loading.value = true
  try {
    // 搜索模式
    if (isSearchMode.value && searchKeyword.value.trim()) {
      const res = await shopApi.searchProducts(searchKeyword.value.trim())
      if (res && res.code === 200) {
        products.value = Array.isArray(res.data) ? res.data : []
        hasMore.value = false // 搜索结果不分页
      }
    } else {
      // 正常浏览模式
      const params = { page: page.value, size: pageSize }
      if (activeCategoryId.value !== null) params.categoryId = activeCategoryId.value
      const res = await shopApi.getProducts(params)
      if (res && res.code === 200) {
        const newProducts = extractList(res)
        products.value = [...products.value, ...newProducts]
        hasMore.value = newProducts.length >= pageSize
      }
    }
  } catch (e) {
    toast.show(getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  const keyword = searchKeyword.value.trim()
  if (keyword) {
    isSearchMode.value = true
    fetchProducts(true)
  } else {
    clearSearch()
  }
}

function clearSearch() {
  searchKeyword.value = ''
  isSearchMode.value = false
  fetchProducts(true)
}

function goToDetail(product) {
  if (product?.id) router.push(`/shop/product/${product.id}`)
}

async function handleAddToCart(product) {
  if (!product?.id) return
  if (!isLoggedIn.value) {
    toast.show('请先登录后再加入购物车', 'warn')
    router.push('/login')
    return
  }
  if (product.stock === 0) {
    toast.show('商品已售罄', 'warn')
    return
  }

  addCartLoading[product.id] = true
  try {
    const res = await shopApi.addToCart(product.id, 1)
    if (res && res.code === 200) {
      toast.show('已加入购物车', 'success')
      cartCount.value = (cartCount.value || 0) + 1
    } else {
      toast.show(res?.msg || '加入购物车失败', 'error')
    }
  } catch (e) {
    toast.show(getApiError(e), 'error')
  } finally {
    addCartLoading[product.id] = false
  }
}

onMounted(() => {
  fetchCategories()
  fetchProducts(true)
})
</script>

<style scoped>
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.no-scrollbar::-webkit-scrollbar {
  display: none;
}
</style>
