<!-- ============================================================
     消息 Tab（移动端独立设计）
     会话列表：头像 + 昵称 + 最后一条 + 时间 + 未读绿点
============================================================ -->
<template>
  <div class="min-h-screen" style="background: var(--m-bg);">
    <header class="m-topbar flex items-center justify-between px-4 h-12">
      <h1 class="text-[17px] font-bold" style="color: var(--m-ink);">消息</h1>
      <router-link to="/ai-chat" class="flex items-center gap-1 m-pill h-8 px-3 tap-scale" style="background: var(--m-brand-soft);" aria-label="AI 助手">
        <Icon icon="material-symbols:smart-toy-rounded" class="w-4 h-4" :style="{ color: 'var(--m-brand)' }" />
        <span class="text-[12px] font-semibold" :style="{ color: 'var(--m-brand)' }">AI 助手</span>
      </router-link>
    </header>

    <main class="px-2.5 pt-1.5">
      <div v-if="sessions.length" class="m-card overflow-hidden divide-y" style="border-color: var(--m-line);">
        <button
          v-for="s in sessions"
          :key="s.userId || s.id"
          type="button"
          class="w-full flex items-center gap-3 px-3 py-3 text-left tap-scale"
          style="border-color: var(--m-line);"
          @click="openChat(s.userId || s.id)"
        >
          <div class="relative shrink-0">
            <img v-if="avatarOf(s)" :src="avatarOf(s)" class="w-12 h-12 rounded-full object-cover" style="background: var(--m-brand-soft);" alt="" />
            <div v-else class="w-12 h-12 rounded-full flex items-center justify-center text-[16px] font-bold text-white" style="background: var(--m-brand);">
              {{ nameOf(s).charAt(0).toUpperCase() }}
            </div>
            <span
              v-if="unreadOf(s) > 0"
              class="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 rounded-full text-[9px] font-bold text-white flex items-center justify-center"
              style="background: var(--m-brand);"
            >{{ unreadOf(s) > 99 ? '99+' : unreadOf(s) }}</span>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-baseline justify-between gap-2">
              <span class="text-[14px] font-semibold truncate" style="color: var(--m-ink);">{{ nameOf(s) }}</span>
              <span class="text-[11px] shrink-0" style="color: var(--m-ink-3);">{{ timeOf(s) }}</span>
            </div>
            <p class="mt-0.5 text-[12px] truncate" style="color: var(--m-ink-3);">{{ lastOf(s) || '打个招呼吧' }}</p>
          </div>
        </button>
      </div>

      <MEmpty v-else-if="!loading" text="还没有会话，去帖子下评论认识同好吧" icon="material-symbols:chat-bubble-outline">
        <router-link to="/" class="m-btn-primary inline-flex items-center h-9 px-5 mt-4 text-[13px]">去发现逛逛</router-link>
      </MEmpty>

      <div v-if="loading" class="flex justify-center py-8">
        <Icon icon="svg-spinners:ring-resize" class="w-6 h-6" :style="{ color: 'var(--m-brand)' }" />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { messageApi, normalizeUrl } from '@wc/shared'
import { MEmpty } from '../../mui'

const router = useRouter()
const sessions = ref<any[]>([])
const loading = ref(false)

function avatarOf(s) {
  return normalizeUrl(s.avatar || s.userAvatar || s.lastMessageAvatar || s.otherUser?.avatar || s.user?.avatar || '')
}
function nameOf(s) {
  return s.username || s.nickname || s.otherUser?.username || s.user?.username || '咖啡友'
}
function lastOf(s) {
  return s.lastMessage || s.lastContent || s.content || ''
}
function unreadOf(s) {
  return s.unreadCount || s.unread || 0
}
function timeOf(s) {
  const t = s.lastMessageTime || s.updateTime || s.lastTime || s.createdAt
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return `${d.getMonth() + 1}-${String(d.getDate()).padStart(2, '0')}`
}

function openChat(userId) {
  if (!userId) return
  router.push(`/messages/chat/${userId}`)
}

async function loadSessions() {
  loading.value = true
  try {
    const res = await messageApi.getSessions()
    if (res && res.code === 200) sessions.value = Array.isArray(res.data) ? res.data : []
  } catch { /* 静默 */ } finally {
    loading.value = false
  }
}

onMounted(loadSessions)
</script>
