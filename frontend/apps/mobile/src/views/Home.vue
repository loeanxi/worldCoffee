<!-- ============================================================
     发现页（移动端独立设计）
     吸顶：商标 + 搜索胶囊 + 通知铃
     分类 chips 横滑 → 双列瀑布流 → 无限加载
     AI 助手悬浮胶囊；底部 TabBar 由 App 全局渲染
============================================================ -->
<template>
  <div class="min-h-screen" style="background: var(--m-bg);">
    <!-- ===== 吸顶栏 ===== -->
    <header class="m-topbar px-3 pt-3 pb-2">
      <div class="flex items-center gap-2.5">
        <WorldCoffeeLogo :size="30" variant="icon" />
        <div v-if="!searchOpen" class="m-search-pill tap-scale" role="button" @click="openSearch">
          <Icon icon="material-symbols:search" class="w-4 h-4 shrink-0" />
          <span class="truncate">搜索咖啡、地点、话题…</span>
        </div>
        <div v-else class="flex-1 flex items-center gap-1.5">
          <input
            ref="searchInput"
            v-model="searchQuery"
            class="m-search-pill"
            style="color: var(--m-ink);"
            type="text"
            placeholder="搜索咖啡、地点、话题…"
            @keyup.enter="doSearch"
          />
          <button type="button" class="text-[13px] shrink-0 tap-scale" style="color: var(--m-brand); font-weight: 600;" @click="doSearch">搜索</button>
          <button type="button" class="text-[13px] shrink-0 tap-scale m-ink-3" @click="clearSearch">取消</button>
        </div>
        <router-link to="/notifications" class="relative w-9 h-9 flex items-center justify-center rounded-full tap-scale shrink-0" style="background: var(--m-card);" aria-label="通知">
          <Icon icon="material-symbols:notifications-outline" class="w-[18px] h-[18px]" :style="{ color: 'var(--m-ink-2)' }" />
          <span v-if="notifCount > 0" class="m-badge" style="top: 4px; right: 4px; transform: none;">{{ notifCount > 99 ? '99+' : notifCount }}</span>
        </router-link>
      </div>

      <!-- ===== 分类 chips ===== -->
      <div class="m-scroll-x flex items-center gap-2 mt-2.5 pb-0.5">
        <MChip v-for="t in tabs" :key="t.key" :label="t.label" :active="!activeTopic && activeTab === t.key" @click="switchTab(t.key)" />
        <span class="w-px h-4 shrink-0 mx-0.5" style="background: var(--m-line);" />
        <MChip v-for="tp in topicTabs" :key="tp" :label="tp" :active="activeTopic === tp" @click="searchTopic(tp)" />
      </div>
    </header>

    <!-- ===== 双列瀑布流 ===== -->
    <main class="px-2.5 pt-2.5">
      <div v-if="posts.length" class="columns-2 gap-2.5">
        <MFeedCard v-for="p in posts" :key="p.id" :post="p" @open="openPost" @like="toggleLike" />
      </div>

      <MEmpty v-else-if="!loading && !loadError" text="还没有笔记，点中间的 ＋ 发第一篇吧" />

      <div v-if="loading" class="flex justify-center py-8">
        <Icon icon="svg-spinners:ring-resize" class="w-6 h-6" :style="{ color: 'var(--m-brand)' }" />
      </div>
      <p v-else-if="loadError" class="text-center py-8 text-[13px]" style="color: var(--m-ink-3);">
        {{ loadError }}
        <button type="button" class="ml-2 font-semibold tap-scale" style="color: var(--m-brand);" @click="fetchPosts(true)">重试</button>
      </p>
      <p v-else-if="posts.length && !hasMore" class="text-center py-6 text-[11px]" style="color: var(--m-ink-3);">— 没有更多了 —</p>
      <div ref="sentinel" style="height: 1px;" />
    </main>

    <!-- ===== AI 助手悬浮胶囊 ===== -->
    <router-link
      to="/ai-chat"
      class="m-pill fixed z-40 flex items-center gap-1.5 h-9 pl-2.5 pr-3.5 tap-scale"
      style="bottom: 96px; right: max(16px, calc(50vw - 304px)); background: var(--m-card); box-shadow: 0 6px 18px rgba(44,38,32,.14);"
    >
      <Icon icon="material-symbols:smart-toy-rounded" class="w-4.5 h-4.5 w-[18px] h-[18px]" :style="{ color: 'var(--m-brand)' }" />
      <span class="text-[12px] font-semibold" style="color: var(--m-ink-2);">AI 助手</span>
    </router-link>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted, inject } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { coffeeApi, extractApiError, useAuth, WorldCoffeeLogo } from '@wc/shared'
import { MChip, MEmpty, MFeedCard } from '../mui'

const router = useRouter()
const toast = inject<any>('toast', { show: () => {} })
const { isLoggedIn } = useAuth()

const notifCount = computed(() => {
  const v = toast?.notifCount?.value
  return typeof v === 'number' ? v : 0
})

// ─── 分类 ───
const tabs = computed(() => [
  { key: 'recommend', label: '推荐' },
  { key: 'latest', label: '最新' },
  ...(isLoggedIn.value ? [{ key: 'following', label: '关注' }] : [])
])
const topicTabs = ['咖啡馆', '手冲', '拉花', '甜品', '冷萃']
const activeTab = ref('recommend')
const activeTopic = ref('')

// ─── 搜索 ───
const searchOpen = ref(false)
const searchQuery = ref('')
const isSearching = ref(false)
const searchInput = ref(null)

function openSearch() {
  searchOpen.value = true
  setTimeout(() => searchInput.value?.focus(), 50)
}
function doSearch() {
  if (!searchQuery.value.trim()) return
  activeTopic.value = ''
  isSearching.value = true
  fetchPosts(true)
}
function clearSearch() {
  searchOpen.value = false
  searchQuery.value = ''
  if (isSearching.value) {
    isSearching.value = false
    fetchPosts(true)
  }
}
function switchTab(key) {
  if (activeTab.value === key && !activeTopic.value && !isSearching.value) return
  activeTab.value = key
  activeTopic.value = ''
  isSearching.value = false
  fetchPosts(true)
}
function searchTopic(topic) {
  if (activeTopic.value === topic) return
  activeTopic.value = topic
  isSearching.value = true
  searchQuery.value = topic
  fetchPosts(true)
}

// ─── Feed 数据 ───
const posts = ref<any[]>([])
const loading = ref(false)
const hasMore = ref(true)
const page = ref(1)
const pageSize = 12
const loadError = ref('')
const sentinel = ref(null)
let observer: IntersectionObserver | null = null

function extractList(res) {
  if (!res || !res.data) return []
  if (Array.isArray(res.data)) return res.data
  if (Array.isArray(res.data.data)) return res.data.data
  if (Array.isArray(res.data.records)) return res.data.records
  if (Array.isArray(res.data.list)) return res.data.list
  return []
}

async function fetchPosts(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 1
    posts.value = []
    hasMore.value = true
    loadError.value = ''
  }
  loading.value = true
  try {
    const params: any = { page: page.value, size: pageSize }
    let res
    if (activeTopic.value) {
      res = await coffeeApi.getTopicPosts({ ...params, topic: activeTopic.value })
    } else if (isSearching.value) {
      res = await coffeeApi.search({ ...params, keyword: searchQuery.value })
    } else if (activeTab.value === 'following' && isLoggedIn.value) {
      res = await coffeeApi.getFollowingPosts(params)
    } else if (activeTab.value === 'recommend') {
      res = await coffeeApi.getRecommendedPosts(params)
    } else {
      res = await coffeeApi.getPosts(params)
    }
    if (res && res.code !== 200) throw new Error(res.msg || '加载失败')
    const list = extractList(res)
    if (list.length > 0) {
      const exist = new Set(posts.value.map(p => p.id))
      posts.value = [...posts.value, ...list.filter(p => !exist.has(p.id))]
      hasMore.value = list.length >= pageSize
    } else {
      hasMore.value = false
    }
  } catch (err) {
    hasMore.value = false
    loadError.value = extractApiError(err)
  } finally {
    loading.value = false
    nextTick(setupObserver)
  }
}

function loadMore() {
  if (!hasMore.value || loading.value || !posts.value.length) return
  page.value++
  fetchPosts()
}

function setupObserver() {
  if (typeof window === 'undefined' || !window.IntersectionObserver) return
  if (!observer) {
    observer = new IntersectionObserver(entries => {
      if (entries.some(e => e.isIntersecting)) loadMore()
    }, { rootMargin: '480px 0px' })
  }
  observer.disconnect()
  if (sentinel.value) observer.observe(sentinel.value)
}

// ─── 交互 ───
function openPost(post) {
  coffeeApi.recordFeedEvent({ postId: post.id, eventType: 'CLICK', source: activeTopic.value ? `topic:${activeTopic.value}` : activeTab.value }).catch(() => {})
  router.push(`/posts/${post.id}`)
}

async function toggleLike(post) {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  try {
    const res = await coffeeApi.toggleLike(post.id)
    if (res && res.code === 200) {
      const d = res.data
      const nowLiked = typeof d === 'object' ? !!(d?.likedByMe ?? d?.liked) : !post.likedByMe
      post.likedByMe = nowLiked
      post.likeCount = Math.max(0, (post.likeCount || post.likes || 0) + (nowLiked ? 1 : -1))
    }
  } catch (e) {
    toast.show(extractApiError(e) || '操作失败', 'error')
  }
}

onMounted(() => fetchPosts(true))
onUnmounted(() => observer?.disconnect())
</script>
