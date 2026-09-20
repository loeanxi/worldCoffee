<!-- ============================================================
     我的 Tab（移动端独立设计）
     头部大卡（头像/昵称/数据行）→ 订单行 → 功能宫格 → 我的内容
============================================================ -->
<template>
  <div class="min-h-screen" style="background: var(--m-bg);">
    <header class="m-topbar flex items-center justify-between px-4 h-12">
      <h1 class="text-[17px] font-bold" style="color: var(--m-ink);">我的</h1>
      <router-link to="/settings" class="w-9 h-9 flex items-center justify-center rounded-full tap-scale" style="background: var(--m-card);" aria-label="设置">
        <Icon icon="material-symbols:settings-outline" class="w-[18px] h-[18px]" :style="{ color: 'var(--m-ink-2)' }" />
      </router-link>
    </header>

    <main class="px-2.5 pt-1.5 space-y-2.5">
      <!-- ===== 头部大卡 ===== -->
      <section class="m-card px-4 py-5" style="background: linear-gradient(135deg, var(--m-brand-soft), var(--m-card) 65%);">
        <div class="flex items-center gap-3.5">
          <img v-if="userAvatar" :src="userAvatar" class="w-16 h-16 rounded-full object-cover border-2" style="border-color: var(--m-card); background: var(--m-brand-soft);" alt="" />
          <div v-else class="w-16 h-16 rounded-full flex items-center justify-center text-[22px] font-bold text-white" style="background: var(--m-brand);">
            {{ usernameInitial }}
          </div>
          <div class="flex-1 min-w-0">
            <p class="text-[17px] font-bold truncate" style="color: var(--m-ink);">{{ username }}</p>
            <p class="mt-0.5 text-[12px] truncate" style="color: var(--m-ink-2);">{{ bio || '这个人很懒，什么都没写' }}</p>
          </div>
          <router-link to="/me" class="text-[12px] font-semibold shrink-0 tap-scale" style="color: var(--m-brand);">主页</router-link>
        </div>
        <div class="mt-4 flex items-center justify-around text-center">
          <div v-for="s in statItems" :key="s.label">
            <p class="text-[16px] font-bold" style="color: var(--m-ink);">{{ s.value }}</p>
            <p class="text-[11px] mt-0.5" style="color: var(--m-ink-3);">{{ s.label }}</p>
          </div>
        </div>
      </section>

      <!-- ===== 我的订单 ===== -->
      <section class="m-card px-4 py-3.5">
        <div class="flex items-center justify-between">
          <p class="text-[14px] font-bold" style="color: var(--m-ink);">我的订单</p>
          <router-link to="/shop/orders" class="flex items-center text-[12px] tap-scale" style="color: var(--m-ink-3);">
            全部
            <Icon icon="material-symbols:chevron-right" class="w-3.5 h-3.5" />
          </router-link>
        </div>
        <div class="mt-3 flex items-center justify-around">
          <router-link v-for="o in orderEntries" :key="o.label" to="/shop/orders" class="flex flex-col items-center gap-1.5 tap-scale">
            <Icon :icon="o.icon" class="w-6 h-6" :style="{ color: 'var(--m-ink-2)' }" />
            <span class="text-[11px]" style="color: var(--m-ink-2);">{{ o.label }}</span>
          </router-link>
        </div>
      </section>

      <!-- ===== 功能宫格 ===== -->
      <section class="m-card grid grid-cols-4 py-4">
        <router-link v-for="g in gridEntries" :key="g.label" :to="g.path" class="flex flex-col items-center gap-1.5 tap-scale">
          <span class="w-10 h-10 rounded-2xl flex items-center justify-center" style="background: var(--m-brand-soft);">
            <Icon :icon="g.icon" class="w-5 h-5" :style="{ color: 'var(--m-brand)' }" />
          </span>
          <span class="text-[11px]" style="color: var(--m-ink-2);">{{ g.label }}</span>
        </router-link>
      </section>

      <!-- ===== 我的内容 ===== -->
      <section class="m-card px-4 py-3.5">
        <div class="flex items-center gap-2">
          <button
            v-for="t in contentTabs"
            :key="t.key"
            type="button"
            class="m-pill h-7 px-3.5 text-[12px] font-medium tap-scale"
            :style="contentTab === t.key
              ? { background: 'var(--m-brand)', color: '#fff' }
              : { background: 'var(--m-brand-soft)', color: 'var(--m-ink-2)' }"
            @click="switchContent(t.key)"
          >{{ t.label }}</button>
        </div>
        <div v-if="contentList.length" class="mt-3 divide-y" style="border-color: var(--m-line);">
          <button
            v-for="p in contentList"
            :key="p.id"
            type="button"
            class="w-full flex items-center gap-2.5 py-2.5 text-left tap-scale"
            style="border-color: var(--m-line);"
            @click="router.push(`/posts/${p.id}`)"
          >
            <img v-if="coverOf(p)" :src="coverOf(p)" class="w-11 h-11 rounded-xl object-cover shrink-0" style="background: var(--m-brand-soft);" alt="" />
            <div class="flex-1 min-w-0">
              <p class="text-[13px] font-medium truncate" style="color: var(--m-ink);">{{ p.title || '无标题笔记' }}</p>
              <p class="text-[11px] mt-0.5" style="color: var(--m-ink-3);">{{ fmtTime(p.createTime || p.createdAt) }}</p>
            </div>
            <Icon icon="material-symbols:chevron-right" class="w-4 h-4 shrink-0" :style="{ color: 'var(--m-ink-3)' }" />
          </button>
        </div>
        <MEmpty v-else-if="!contentLoading" :text="contentTab === 'posts' ? '还没发过笔记' : '还没有收藏'" />
      </section>

      <button type="button" class="m-card w-full h-12 text-[14px] font-semibold tap-scale" style="color: #D46A3D;" @click="handleLogout">
        退出登录
      </button>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, inject } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { userApi, coffeeApi, normalizeUrl, useAuth } from '@wc/shared'
import { MEmpty } from '../../mui'

const router = useRouter()
const toast = inject<any>('toast', { show: () => {}, logout: () => {} })
const { user, avatar: authAvatar } = useAuth()

const username = computed(() => user.value?.username || user.value?.nickname || '咖啡友')
const usernameInitial = computed(() => username.value.charAt(0).toUpperCase())
const userAvatar = computed(() => normalizeUrl(user.value?.avatar || authAvatar.value || ''))
const bio = computed(() => user.value?.signature || user.value?.bio || '')

const stats = ref<any>({})
const statItems = computed(() => [
  { label: '关注', value: stats.value.following ?? 0 },
  { label: '粉丝', value: stats.value.followers ?? 0 },
  { label: '获赞', value: stats.value.likes ?? 0 },
  { label: '笔记', value: stats.value.posts ?? 0 }
])

const orderEntries = [
  { label: '待付款', icon: 'material-symbols:payments-outline' },
  { label: '待发货', icon: 'material-symbols:package-2-outline' },
  { label: '待收货', icon: 'material-symbols:local-shipping-outline' },
  { label: '售后', icon: 'material-symbols:support-agent-outline' }
]

const gridEntries = [
  { label: '优惠券', icon: 'material-symbols:confirmation-number-outline', path: '/shop/coupons' },
  { label: 'AI 助手', icon: 'material-symbols:smart-toy-outline', path: '/ai-chat' },
  { label: '设置', icon: 'material-symbols:settings-outline', path: '/settings' },
  { label: '关于', icon: 'material-symbols:info-outline', path: '/settings/about' }
]

// ─── 我的内容（笔记 / 收藏） ───
const contentTabs = [
  { key: 'posts', label: '我的笔记' },
  { key: 'favorites', label: '我的收藏' }
]
const contentTab = ref('posts')
const contentList = ref<any[]>([])
const contentLoading = ref(false)

function coverOf(p) {
  if (Array.isArray(p.images) && p.images.length) {
    const first = p.images[0]
    return normalizeUrl(typeof first === 'string' ? first : (first?.url || ''))
  }
  return normalizeUrl(p.imageUrl || p.coverImage || '')
}

function fmtTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return isNaN(d.getTime()) ? '' : `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function switchContent(key) {
  if (contentTab.value === key) return
  contentTab.value = key
  await loadContent()
}

async function loadContent() {
  contentLoading.value = true
  try {
    const res = contentTab.value === 'posts'
      ? await coffeeApi.getMyPosts({ page: 1, size: 20 })
      : await coffeeApi.getMyFavorites({ page: 1, size: 20 })
    const d = res?.data
    contentList.value = Array.isArray(d) ? d : (Array.isArray(d?.data) ? d.data : (Array.isArray(d?.records) ? d.records : []))
  } catch {
    contentList.value = []
  } finally {
    contentLoading.value = false
  }
}

function handleLogout() {
  toast.logout?.()
}

onMounted(async () => {
  try {
    const res = await userApi.getMeStats()
    if (res && res.code === 200 && res.data) {
      const d = res.data
      stats.value = {
        posts: d.postCount ?? 0,
        likes: d.likeCount ?? 0,
        favorites: d.favoriteCount ?? 0,
        following: d.followCount ?? d.followingCount ?? 0,
        followers: d.fansCount ?? d.followerCount ?? 0
      }
    }
  } catch { /* 静默 */ }
  loadContent()
})
</script>
