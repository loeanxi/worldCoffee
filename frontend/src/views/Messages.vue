<template>
  <div class="min-h-screen pb-24 md:pb-8 bg-surface">
    <!-- Header -->
    <header class="sticky top-0 z-40 bg-surface-elevated/90 backdrop-blur-xl border-b border-line/60">
      <div class="max-w-2xl mx-auto px-4 h-14 flex items-center gap-3">
        <button
          class="p-1.5 rounded-lg hover:bg-surface-soft transition-colors"
          @click="router.back()"
          aria-label="返回"
        >
          <Icon icon="material-symbols:arrow-back" class="w-5 h-5 text-ink-soft" />
        </button>
        <h1 class="text-base font-semibold text-ink flex-1">消息</h1>
        <button
          class="relative p-1.5 rounded-lg hover:bg-surface-soft transition-colors"
          @click="router.push('/notifications')"
          aria-label="通知中心"
        >
          <Icon icon="material-symbols:notifications-outline" class="w-5 h-5 text-ink-soft" />
          <span
            v-if="notifBadge > 0"
            class="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 text-[9.5px] font-bold rounded-full flex items-center justify-center shadow-sm"
            style="background: #EF4444; color: var(--text-inverse, #fff);"
          >
            {{ notifBadge > 99 ? '99+' : notifBadge }}
          </span>
        </button>
      </div>
    </header>

    <main class="max-w-2xl mx-auto px-4 pt-4 space-y-3">
      <!-- ✨ magic 助手（固定入口） -->
      <div
        class="bg-surface-elevated border border-line/60 rounded-2xl overflow-hidden animate-fade-up tap-scale cursor-pointer"
        style="box-shadow: var(--shadow-card);"
        @click="openAIChat"
      >
        <div class="flex items-center gap-3 p-4 hover:bg-surface-soft transition-colors">
          <!-- Avatar（带特殊 AI 渐变） -->
          <div class="relative w-12 h-12 rounded-2xl flex items-center justify-center shrink-0 shadow-sm overflow-hidden bg-surface-soft">
            <WorldCoffeeAiLogo :size="48" />
            <!-- AI 星光标识 -->
            <span class="absolute -bottom-0.5 -right-0.5 w-4 h-4 flex items-center justify-center">
              <svg viewBox="0 0 16 16" class="w-3.5 h-3.5 drop-shadow-sm">
                <path d="M8 1 L9.2 5.5 L13.5 7 L9.2 8.5 L8 13 L6.8 8.5 L2.5 7 L6.8 5.5 Z" fill="#FFD54F" stroke="#F9A825" stroke-width="0.5"/>
              </svg>
            </span>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-start gap-2 justify-between">
              <div class="min-w-0">
                <span class="text-[14.5px] font-semibold text-ink truncate flex items-center gap-1.5">
                  magic 助手
                  <span class="text-[9.5px] font-semibold px-1.5 py-0.5 rounded-full bg-coffee-brown/15 text-coffee-brown">magic</span>
                </span>
                <p class="text-[12.5px] mt-1 text-ink-muted truncate">
                  搜商品 · 找帖子 · 解答咖啡问题 ☕
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="space-y-3">
        <div v-for="i in 5" :key="i" class="bg-surface-elevated border border-line/40 rounded-2xl p-4 flex items-center gap-3 animate-pulse" style="box-shadow: var(--shadow-card);">
          <div class="skeleton w-12 h-12 rounded-2xl shrink-0" />
          <div class="flex-1 space-y-2">
            <div class="skeleton h-4 w-24 rounded" />
            <div class="skeleton h-3 w-40 rounded" />
          </div>
        </div>
      </div>

      <!-- Empty -->
      <div v-else-if="!sessions.length" class="bg-surface-elevated border border-line/40 rounded-2xl p-8 text-center animate-fade-up" style="box-shadow: var(--shadow-card);">
        <div class="flex justify-center mb-4">
          <WorldCoffeeLogo size="md" />
        </div>
        <h3 class="text-base font-bold text-ink mb-1.5">暂无其他消息</h3>
        <p class="text-[13px] text-ink-muted mb-5">去首页看看其他用户，开启一段咖啡对话吧 ☕</p>
        <router-link
          to="/"
          class="inline-flex items-center gap-2 px-5 py-2.5 rounded-2xl text-[13px] font-semibold shadow-sm tap-scale"
          style="background: linear-gradient(135deg, var(--coffee-brown, #6D4C41), var(--coffee-bean, #2C1810)); color: var(--text-inverse, #fff); box-shadow: 0 4px 14px rgba(109,76,65,0.25);"
        >
          <Icon icon="material-symbols:explore" class="w-4 h-4" />
          去首页发现
        </router-link>
      </div>

      <!-- Session List -->
      <div v-else class="bg-surface-elevated border border-line/40 rounded-2xl overflow-hidden animate-fade-up" style="box-shadow: var(--shadow-card);">
        <div
          v-for="(session, idx) in sessions"
          :key="session.userId"
          class="flex items-center gap-3 p-4 hover:bg-surface-soft transition-colors cursor-pointer tap-scale border-b border-line/50 last:border-b-0"
          :style="{ animationDelay: `${idx * 30}ms` }"
          @click="openChat(session.userId)"
        >
          <!-- Avatar -->
          <div class="w-12 h-12 rounded-2xl flex items-center justify-center text-lg font-semibold shrink-0 overflow-hidden shadow-sm" style="background: linear-gradient(135deg, var(--coffee-brown, #6D4C41), var(--coffee-dark, #3E2723)); color: var(--text-inverse, #fff);">
            <img
              v-if="sessionAvatar(session)"
              :src="sessionAvatar(session)"
              class="w-full h-full object-cover"
              @error="onAvatarError"
            />
            <span v-else>{{ initial(session.username) }}</span>
          </div>

          <!-- Info -->
          <div class="flex-1 min-w-0">
            <div class="flex items-start gap-2 justify-between">
              <div class="min-w-0">
                <span class="text-[14.5px] font-semibold text-ink truncate">{{ session.username }}</span>
                <p
                  class="text-[12.5px] mt-1 truncate"
                  :class="session.unreadCount > 0 ? 'text-ink font-medium' : 'text-ink-muted'"
                >
                  {{ session.lastMessage || '（还没有消息）' }}
                </p>
              </div>
              <div class="flex flex-col items-end gap-1.5 shrink-0">
                <span class="text-[10.5px] text-ink-muted">{{ formatTime(session.lastTime) }}</span>
                <span
                  v-if="session.unreadCount > 0"
                  class="min-w-[18px] h-[18px] px-1 text-[10px] font-bold rounded-full flex items-center justify-center shadow-sm"
                  style="background: #EF4444; color: var(--text-inverse, #fff);"
                >
                  {{ session.unreadCount > 99 ? '99+' : session.unreadCount }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, inject } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Icon } from '@iconify/vue'
import { messageApi, createSSESubscriber, normalizeUrl, extractApiError } from '../api'
import EmptyState from '../components/EmptyState.vue'
import WorldCoffeeLogo from '../components/WorldCoffeeLogo.vue'
import WorldCoffeeAiLogo from '../components/WorldCoffeeAiLogo.vue'

const router = useRouter()
const route = useRoute()
const toast = inject('toast', { show: () => {} })
const loading = ref(true)
const sessions = ref([])

// 红点（右上角铃铛）
const notifBadge = computed(() => {
  if (!toast.notifCount) return 0
  // toast.notifCount 可能是 ref 或直接是 number
  const val = typeof toast.notifCount === 'object' && toast.notifCount !== null && 'value' in toast.notifCount
    ? toast.notifCount.value
    : toast.notifCount
  return Number(val) || 0
})

// 头像 URL 归一化
function sessionAvatar(session) {
  if (!session) return ''
  const raw = session.avatar || session.userAvatar || (session.user && session.user.avatar) || ''
  return normalizeUrl(raw)
}

async function loadSessions() {
  loading.value = true
  try {
    const res = await messageApi.getSessions()
    if (res && res.code === 200) {
      const list = Array.isArray(res.data) ? res.data : []
      sessions.value = list.map(s => ({
        ...s,
        avatar: sessionAvatar(s)
      }))
    } else if (res && res.code === 401) {
      toast.show('请先登录', 'error')
    } else {
      toast.show(res?.msg || '加载会话失败', 'error')
    }
  } catch (e) {
    toast.show(extractApiError(e) || '加载会话失败', 'error')
    sessions.value = []
  } finally {
    loading.value = false
  }
}

function openChat(userId) {
  if (!userId) return
  router.push(`/messages/chat/${userId}`)
}

function openAIChat() {
  router.push('/ai-chat')
}

function initial(name) {
  if (!name) return 'U'
  return name.charAt(0).toUpperCase()
}

function formatTime(t) {
  if (!t) return ''
  const date = new Date(t)
  if (isNaN(date.getTime())) return ''
  const now = new Date()
  const diff = (now - date) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 7 * 86400) return `${Math.floor(diff / 86400)}天前`
  return date.toLocaleDateString()
}

function onAvatarError(e) {
  if (e.target) e.target.style.display = 'none'
}

// ✅ 页面自己建 SSE 连接（不依赖 App.vue 全局派发）
let sseSub = null
onMounted(async () => {
  await loadSessions()
  if (toast.refreshMessageCount) toast.refreshMessageCount()
  if (toast.refreshNotifCount) toast.refreshNotifCount()

  // 直接建立本页面独立的 SSE 连接
  sseSub = createSSESubscriber()
  if (sseSub) {
    sseSub.onmessage((data) => {
      if (typeof data !== 'string') return
      const trimmed = data.trim()
      // 兼容格式："chat:{senderId}" 或 "message:{senderId}"
      // 或 JSON: {"type":"chat"/"message"}
      if (trimmed.startsWith('chat:') || trimmed.startsWith('message:')) {
        loadSessions()
        if (toast.refreshMessageCount) toast.refreshMessageCount()
        return
      }
      try {
        const payload = JSON.parse(trimmed)
        if (payload && (payload.type === 'chat' || payload.type === 'message')) {
          loadSessions()
          if (toast.refreshMessageCount) toast.refreshMessageCount()
        } else {
          // 通知：点赞/收藏/评论
          if (toast.refreshNotifCount) toast.refreshNotifCount()
        }
      } catch {}
    })
  }
})

// 回到私信列表时刷新（比如从聊天详情返回）
watch(() => route.path, (newPath) => {
  if (newPath === '/messages' || newPath.startsWith('/messages?')) {
    loadSessions()
    if (toast.refreshMessageCount) toast.refreshMessageCount()
    if (toast.refreshNotifCount) toast.refreshNotifCount()
  }
})

onBeforeUnmount(() => {
  if (sseSub) { try { sseSub.close() } catch {} sseSub = null }
})
</script>
