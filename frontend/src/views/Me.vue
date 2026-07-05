<template>
  <div class="min-h-screen pb-24 md:pb-8 bg-surface">
    <!-- Header (sticky) -->
    <header class="sticky top-0 z-40 bg-surface-elevated/90 backdrop-blur-xl border-b border-line/40">
      <div class="max-w-2xl mx-auto px-4 h-14 flex items-center justify-between">
        <h1 class="text-base font-semibold text-ink">我的</h1>
        <button
          class="p-2 rounded-xl hover:bg-surface-soft transition-colors"
          @click="router.push('/settings')"
        >
          <Icon icon="material-symbols:settings" class="w-5 h-5 text-ink-soft" />
        </button>
      </div>
    </header>

    <main class="max-w-2xl mx-auto px-4 pt-6 space-y-5">
      <!-- Profile Hero -->
      <div v-if="user" class="animate-fade-up">
        <div class="relative overflow-hidden rounded-3xl bg-surface-elevated border border-line/40" style="box-shadow: var(--shadow-card);">
          <!-- Decorative gradient top -->
          <div class="absolute inset-x-0 top-0 h-28" style="background: linear-gradient(135deg, rgba(109,76,65,0.15), transparent);"></div>

          <div class="relative p-6">
            <div class="flex items-start gap-4">
              <!-- 大头像 -->
              <div class="relative">
                <div class="w-20 h-20 rounded-3xl flex items-center justify-center text-3xl font-bold shadow-lg overflow-hidden" style="background: linear-gradient(135deg, var(--coffee-brown, #6D4C41), var(--coffee-espresso, #2C1810)); color: var(--text-inverse, #fff);">
                  <img v-if="user.avatar" :src="user.avatar" :alt="user.username" class="w-full h-full object-cover" />
                  <span v-else>{{ user.username?.charAt(0)?.toUpperCase() }}</span>
                </div>
              </div>

              <div class="flex-1 min-w-0 pt-1">
                <h2 class="text-lg font-bold text-ink leading-tight truncate">{{ user.username }}</h2>
                <p class="text-xs text-ink-muted mt-1">{{ greeting }}，欢迎回来 ☕</p>
                <p class="text-[11px] text-ink-muted mt-0.5">加入于 {{ formatDate(user.createTime) }}</p>
              </div>
            </div>

            <!-- Quick stats -->
            <div class="grid grid-cols-6 gap-2 mt-5">
              <div class="col-span-2 rounded-2xl p-3 text-center" style="background: var(--bg-secondary);">
                <div class="text-base font-bold text-ink">{{ stats.posts }}</div>
                <div class="text-[10px] text-ink-muted mt-0.5">帖子</div>
              </div>
              <div class="col-span-2 rounded-2xl p-3 text-center" style="background: var(--bg-secondary);">
                <div class="text-base font-bold text-ink">{{ stats.likes }}</div>
                <div class="text-[10px] text-ink-muted mt-0.5">获赞</div>
              </div>
              <div class="col-span-2 rounded-2xl p-3 text-center" style="background: var(--bg-secondary);">
                <div class="text-base font-bold text-ink">{{ stats.favorites }}</div>
                <div class="text-[10px] text-ink-muted mt-0.5">被收藏</div>
              </div>
              <div class="col-span-2 rounded-2xl p-3 text-center" style="background: var(--bg-secondary);">
                <div class="text-base font-bold text-ink">{{ stats.comments }}</div>
                <div class="text-[10px] text-ink-muted mt-0.5">收到评论</div>
              </div>
              <div class="col-span-2 rounded-2xl p-3 text-center" style="background: var(--bg-secondary);">
                <div class="text-base font-bold text-ink">{{ stats.following }}</div>
                <div class="text-[10px] text-ink-muted mt-0.5">关注</div>
              </div>
              <div class="col-span-2 rounded-2xl p-3 text-center" style="background: var(--bg-secondary);">
                <div class="text-base font-bold text-ink">{{ stats.followers }}</div>
                <div class="text-[10px] text-ink-muted mt-0.5">粉丝</div>
              </div>
            </div>

            <!-- User status chips -->
            <div class="flex flex-wrap gap-2 mt-4">
              <span
                class="inline-flex items-center gap-1.5 text-[11px] font-medium px-2.5 py-1 rounded-lg"
                :style="user.status === 1 ? {background: 'rgba(124,174,140,0.18)', color: '#3E7C57'} : {background: 'rgba(232,139,139,0.18)', color: '#C96B6B'}"
              >
                <Icon :icon="user.status === 1 ? 'material-symbols:check-circle' : 'material-symbols:error-outline'" class="w-3.5 h-3.5" />
                {{ user.status === 1 ? '账号正常' : '已冻结' }}
              </span>
              <span v-if="user.phone" class="inline-flex items-center gap-1.5 text-[11px] font-medium px-2.5 py-1 rounded-lg" style="background: rgba(141,180,199,0.18); color: #2563EB;">
                <Icon icon="material-symbols:phone" class="w-3.5 h-3.5" />
                {{ user.phone }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Skeleton -->
      <div v-else class="bg-surface-elevated border border-line/40 rounded-3xl overflow-hidden animate-pulse" style="box-shadow: var(--shadow-card);">
        <div class="p-6">
          <div class="flex items-start gap-4">
            <div class="skeleton w-20 h-20 rounded-3xl" />
            <div class="flex-1 space-y-2.5 pt-1">
              <div class="skeleton h-5 w-36 rounded" />
              <div class="skeleton h-3 w-28 rounded" />
              <div class="skeleton h-3 w-20 rounded" />
            </div>
          </div>
          <div class="grid grid-cols-3 gap-2 mt-5">
            <div class="skeleton h-14 rounded-2xl" />
            <div class="skeleton h-14 rounded-2xl" />
            <div class="skeleton h-14 rounded-2xl" />
          </div>
        </div>
      </div>

      <!-- Shop Quick Links -->
      <div class="grid grid-cols-3 gap-3 animate-fade-up" style="animation-delay: 0.05s;">
        <button
          class="rounded-3xl p-4 text-left transition-shadow tap-scale bg-surface-elevated border border-line/40"
          style="box-shadow: var(--shadow-sm);"
          @click="router.push('/shop/cart')"
        >
          <div class="w-10 h-10 rounded-2xl flex items-center justify-center mb-3" style="background: rgba(238,194,123,0.20);">
            <Icon icon="material-symbols:shopping-cart" class="w-5 h-5" style="color: var(--coffee-brown, #6D4C41);" />
          </div>
          <div class="text-sm font-bold text-ink">购物车</div>
          <div class="text-[11px] text-ink-muted mt-0.5">去逛逛想买的</div>
        </button>
        <button
          class="rounded-3xl p-4 text-left transition-shadow tap-scale bg-surface-elevated border border-line/40"
          style="box-shadow: var(--shadow-sm);"
          @click="router.push('/shop/coupons')"
        >
          <div class="w-10 h-10 rounded-2xl flex items-center justify-center mb-3" style="background: rgba(109,76,65,0.12);">
            <Icon icon="material-symbols:confirmation-number" class="w-5 h-5" style="color: var(--coffee-brown, #6D4C41);" />
          </div>
          <div class="text-sm font-bold text-ink">优惠券</div>
          <div class="text-[11px] text-ink-muted mt-0.5">领取专属福利</div>
        </button>
        <button
          class="rounded-3xl p-4 text-left transition-shadow tap-scale bg-surface-elevated border border-line/40"
          style="box-shadow: var(--shadow-sm);"
          @click="router.push('/shop/orders')"
        >
          <div class="w-10 h-10 rounded-2xl flex items-center justify-center mb-3" style="background: rgba(245,230,211,0.5);">
            <Icon icon="material-symbols:receipt-long" class="w-5 h-5" style="color: var(--coffee-brown, #6D4C41);" />
          </div>
          <div class="text-sm font-bold text-ink">我的订单</div>
          <div class="text-[11px] text-ink-muted mt-0.5">查看订单记录</div>
        </button>
      </div>

      <!-- Tabs (Posts / Likes / Favorites) -->
      <div class="bg-surface-elevated border border-line/40 rounded-3xl overflow-hidden animate-fade-up" style="box-shadow: var(--shadow-card); animation-delay: 0.08s;">
        <div class="flex border-b border-line/40">
          <button
            v-for="tab in contentTabs"
            :key="tab.key"
            :class="[
              'relative flex-1 py-3.5 text-sm font-medium transition-all',
              activeContentTab === tab.key ? 'text-ink' : 'text-ink-muted hover:text-ink-soft'
            ]"
            @click="switchContentTab(tab.key)"
          >
            <Icon :icon="tab.icon" class="w-4 h-4 mr-1 -mt-0.5 inline" />
            {{ tab.label }}
            <span
              v-if="activeContentTab === tab.key"
              class="absolute bottom-0 left-1/2 -translate-x-1/2 w-8 h-0.5 rounded-t-full transition-all"
              style="background: var(--coffee-brown, #6D4C41);"
            />
          </button>
        </div>

        <!-- Post cards grid (2-col) -->
        <div v-if="contentLoading && contentPosts.length === 0" class="grid grid-cols-2 gap-3 p-4">
          <div v-for="n in 4" :key="n" class="rounded-2xl overflow-hidden" style="background: var(--bg-secondary);">
            <div class="skeleton aspect-square w-full" />
            <div class="p-3 space-y-1.5">
              <div class="skeleton h-3 w-full rounded" />
              <div class="skeleton h-3 w-2/3 rounded" />
            </div>
          </div>
        </div>

        <EmptyState v-else-if="!contentLoading && contentPosts.length === 0" icon="📭" title="空空如也" description="这里还没有内容" />

        <div v-else class="grid grid-cols-2 gap-3 p-4">
          <div
            v-for="post in contentPosts"
            :key="post.id"
            class="rounded-2xl overflow-hidden cursor-pointer tap-scale bg-surface-elevated border border-line/40"
            style="box-shadow: var(--shadow-sm);"
            @click="router.push(`/posts/${post.id}`)"
          >
            <div class="relative">
              <div class="w-full aspect-square overflow-hidden" style="background: var(--bg-secondary);">
                <img v-if="post.images && post.images.length" :src="post.images[0]" class="w-full h-full object-cover" loading="lazy" />
                <div v-else class="w-full h-full flex items-center justify-center"><WorldCoffeeLogo size="sm" variant="bare" /></div>
              </div>
              <div v-if="post.likeCount >= 10" class="absolute top-2 right-2 backdrop-blur-sm text-[10px] font-semibold px-2 py-0.5 rounded-lg flex items-center gap-0.5" style="background: rgba(255,255,255,0.9); color: var(--coffee-brown, #6D4C41);">
                <Icon icon="material-symbols:favorite" class="w-3 h-3" style="color: #EF4444;" />
                {{ post.likeCount }}
              </div>
            </div>
            <div class="p-3">
              <h4 class="text-xs font-semibold text-ink leading-snug line-clamp-2">{{ post.title }}</h4>
              <div class="flex items-center justify-between mt-2 text-[10px] text-ink-muted">
                <span>{{ formatDate(post.createTime) }}</span>
                <span class="flex items-center gap-0.5">
                  <Icon icon="material-symbols:favorite-outline" class="w-3 h-3" />
                  {{ post.likeCount || 0 }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="contentHasMore" class="px-4 py-3 text-center border-t border-line/40">
          <button
            class="text-xs font-medium hover:underline"
            style="color: var(--coffee-brown, #6D4C41);"
            :disabled="contentLoading"
            @click="loadMoreContent"
          >
            {{ contentLoading ? '加载中...' : '加载更多' }}
          </button>
        </div>
      </div>

    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, inject } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { userApi, coffeeApi, getApiError } from '../api'
import { useAuth } from '../composables/useAuth'
import EmptyState from '../components/EmptyState.vue'
import WorldCoffeeLogo from '../components/WorldCoffeeLogo.vue'

const router = useRouter()
const toast = inject('toast')
const { updateUser, avatar: authAvatar } = useAuth()

// ─── User Info ───────────────────────────────────────
function extractList(res) {
  if (!res || !res.data) return []
  if (Array.isArray(res.data)) return res.data
  if (Array.isArray(res.data.data)) return res.data.data
  if (Array.isArray(res.data.records)) return res.data.records
  if (Array.isArray(res.data.list)) return res.data.list
  return []
}

const user = ref(null)
const stats = ref({ posts: 0, likes: 0, favorites: 0 })

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早安'
  if (h < 14) return '午安'
  if (h < 18) return '下午好'
  return '晚上好'
})

function formatDate(t) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 改用统一的 /users/me/stats（后端 UserStatsVO）
async function fetchStatsFor(userId) {
  try {
    const res = await userApi.getMeStats()
    if (res && res.code === 200 && res.data) {
      const d = res.data
      stats.value = {
        posts: d.postCount ?? 0,
        likes: d.likeCount ?? 0,
        favorites: d.favoriteCount ?? 0,
        comments: d.commentCount ?? 0,
        following: d.followingCount ?? 0,
        followers: d.followerCount ?? 0
      }
    }
  } catch (e) { /* 静默失败 */ }
}

async function fetchUser() {
  try {
    const res = await userApi.getMe()
    if (res && res.code === 200) {
      // 后端 avatar 可能为空（上传未写入 DB），用 localStorage 中已保存的 avatar 兜底
      if (!res.data?.avatar && authAvatar.value) {
        res.data.avatar = authAvatar.value
      }
      user.value = res.data

      // 同步到全局 useAuth（保证下次登录前后一致）
      if (res.data?.avatar) {
        updateUser({ avatar: res.data.avatar })
      }

      // 同时获取统计数据
      const uid = res.data?.id ?? res.data?.userId
      if (uid) fetchStatsFor(uid)
    } else {
      toast.show(res?.msg || '获取用户信息失败', 'error')
    }
  } catch (e) {
    toast.show(getApiError(e), 'error')
  }
}

// ─── Content Tabs ────────────────────────────────────
const contentTabs = [
  { key: 'posts', label: '我的帖子', icon: 'material-symbols:article' },
  { key: 'likes', label: '我的赞', icon: 'material-symbols:favorite-outline' },
  { key: 'favorites', label: '我的收藏', icon: 'material-symbols:star-outline' }
]

const activeContentTab = ref('posts')
const contentPosts = ref([])
const contentLoading = ref(false)
const contentPage = ref(1)
const contentHasMore = ref(false)

async function fetchContent(reset = false) {
  if (contentLoading.value) return
  if (reset) { contentPage.value = 1; contentPosts.value = []; contentHasMore.value = true }

  contentLoading.value = true
  try {
    const params = { page: contentPage.value, size: 10 }
    let res
    if (activeContentTab.value === 'likes') {
      res = await coffeeApi.getMyLikes(params)
    } else if (activeContentTab.value === 'favorites') {
      res = await coffeeApi.getMyFavorites(params)
    } else {
      res = await coffeeApi.getMyPosts(params)
    }
    if (res && res.code === 200) {
      const data = extractList(res)
      if (reset) contentPosts.value = data
      else contentPosts.value = [...contentPosts.value, ...data]
      contentHasMore.value = data.length >= 10
    } else {
      toast.show(res?.msg || '加载失败', 'error')
    }
  } catch (e) {
    toast.show(getApiError(e), 'error')
  } finally {
    contentLoading.value = false
  }
}

function switchContentTab(tab) {
  activeContentTab.value = tab
  fetchContent(true)
}

function loadMoreContent() {
  contentPage.value++
  fetchContent()
}

// ─── Init ───────────────────────────────────────────
onMounted(() => {
  fetchUser()
  fetchContent(true)
})
</script>
