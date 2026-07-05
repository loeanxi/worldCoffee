<template>
  <!-- 移动端底部导航 -->
  <nav class="lg:hidden fixed bottom-0 inset-x-0 z-50">
    <div class="mx-auto max-w-3xl px-2 pb-[max(env(safe-area-inset-bottom,0px),8px)]">
      <div class="flex items-center justify-around gap-1 py-2 bg-surface-elevated/90 backdrop-blur-2xl rounded-3xl shadow-[0_-4px_24px_rgba(62,39,35,0.08),0_1px_2px_rgba(62,39,35,0.04)] border border-line/60">
        <router-link
          v-for="item in tabs"
          :key="item.path"
          :to="item.path"
          :class="[
            'relative flex flex-col items-center justify-center gap-0.5 py-1.5 px-3 rounded-2xl text-[10.5px] font-medium transition-all tap-scale',
            isActive(item.path)
              ? 'text-ink bg-surface-soft shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.06)]'
              : 'text-ink-muted hover:text-ink hover:bg-surface-soft/60'
          ]"
          :aria-label="item.label"
        >
          <Icon :icon="isActive(item.path) ? item.activeIcon : item.icon" class="w-6 h-6" />
          <span>{{ item.label }}</span>
          <span
            v-if="item.badge && item.badge > 0"
            class="absolute top-0 right-1.5 min-w-[16px] h-4 px-1 text-[9.5px] font-bold text-white rounded-full bg-[#EF4444] flex items-center justify-center shadow-[0_1px_3px_rgba(62,39,35,0.2)]"
          >
            {{ item.badge > 99 ? '99+' : item.badge }}
          </span>
        </router-link>
      </div>
    </div>
  </nav>

  <!-- 桌面端 / 平板侧边栏 (md breakpoint) -->
  <aside class="hidden md:flex flex-col w-[240px] shrink-0 sticky top-0 h-screen py-5 px-3 border-r border-line/60 bg-surface-elevated/90 backdrop-blur-2xl z-40">
    <!-- Logo -->
    <router-link
      to="/"
      class="flex items-center gap-3 px-3 mb-6 hover:opacity-90 transition-opacity"
    >
      <div class="w-11 h-11 rounded-2xl bg-gradient-to-br from-[#6D4C41] to-[#3E2723] flex items-center justify-center shadow-[0_4px_14px_rgba(109,76,65,0.22)]">
        <svg viewBox="0 0 40 40" class="w-6 h-6 text-white" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M8 14 Q8 12 10 12 L24 12 Q26 12 26 14 L25 28 Q25 32 21 32 L13 32 Q9 32 9 28 Z" fill="currentColor" opacity="0.95"/>
          <ellipse cx="17" cy="14" rx="8" ry="2" fill="#3E2723"/>
          <path d="M26 15 Q32 15 32 22 Q32 29 26 29" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>
        </svg>
      </div>
      <div>
        <div class="text-[15px] font-bold text-ink leading-tight">WorldCoffee</div>
        <div class="text-[11px] text-ink-muted mt-0.5">咖啡 · 生活</div>
      </div>
    </router-link>

    <!-- 导航项 -->
    <nav class="flex flex-col gap-0.5">
      <router-link
        v-for="item in sideNavItems"
        :key="item.path"
        :to="item.path"
        :aria-label="item.label"
        :class="[
          'flex items-center gap-3 px-3.5 py-2.5 rounded-2xl text-[13.5px] font-medium transition-all tap-scale',
          isActive(item.path)
            ? 'text-ink bg-surface-soft shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.06)]'
            : 'text-ink-muted hover:bg-surface-soft/60 hover:text-ink'
        ]"
      >
        <Icon :icon="isActive(item.path) ? item.activeIcon : item.icon" class="w-5 h-5 shrink-0" />
        <span class="truncate">{{ item.label }}</span>
        <span
          v-if="item.badge && item.badge > 0"
          class="ml-auto min-w-[20px] h-5 px-1.5 text-[10.5px] font-bold text-white rounded-full bg-[#EF4444] flex items-center justify-center shadow-sm"
        >
          {{ item.badge > 99 ? '99+' : item.badge }}
        </span>
      </router-link>
    </nav>

    <!-- 底部用户信息 -->
    <div v-if="isLoggedIn" class="mt-auto pt-4">
      <div class="flex items-center gap-3 p-3 rounded-2xl bg-surface-soft shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.05)] cursor-pointer tap-scale">
        <div class="w-9 h-9 rounded-2xl overflow-hidden avatar-gradient-light flex items-center justify-center text-white text-sm font-semibold shrink-0">
          <img v-if="userAvatar" :src="userAvatar" class="w-full h-full object-cover" @error="onAvatarError" />
          <span v-else>{{ userNameInitial }}</span>
        </div>
        <div class="flex-1 min-w-0">
          <div class="text-[12.5px] font-semibold text-ink truncate">{{ userName }}</div>
          <div class="text-[10.5px] text-ink-muted truncate">欢迎回来</div>
        </div>
        <Icon icon="material-symbols:chevron-right" class="ml-auto w-4 h-4 text-ink-muted" />
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Icon } from '@iconify/vue'
import { useAuth } from '../composables/useAuth'

const props = defineProps({
  notifCount: { type: Number, default: 0 },
  messageCount: { type: Number, default: 0 }
})

const route = useRoute()
const { isLoggedIn, user, avatar: authAvatar } = useAuth()

const activePath = computed(() => route.path)

function isActive(path) {
  if (activePath.value === path) return true
  if (path !== '/' && activePath.value.startsWith(path)) return true
  return false
}

function onAvatarError(e) { if (e.target) e.target.style.display = 'none' }

// 移动端
const tabs = computed(() => {
  const base = [
    { path: '/', label: '首页', icon: 'material-symbols:home-outline', activeIcon: 'material-symbols:home' },
    { path: '/shop', label: '商城', icon: 'material-symbols:shopping-bag-outline', activeIcon: 'material-symbols:shopping-bag' }
  ]
  if (isLoggedIn.value) base.push({ path: '/create', label: '发布', icon: 'material-symbols:add-circle-outline', activeIcon: 'material-symbols:add-circle' })
  if (isLoggedIn.value) base.push({
    path: '/messages',
    label: '消息',
    icon: 'material-symbols:chat-outline',
    activeIcon: 'material-symbols:chat',
    badge: (Number(props.notifCount) || 0) + (Number(props.messageCount) || 0)
  })
  base.push({ path: '/me', label: '我的', icon: 'material-symbols:person-outline', activeIcon: 'material-symbols:person' })
  return base
})

// 桌面端 / 平板侧边栏
const sideNavItems = computed(() => {
  const items = [
    { path: '/', label: '首页', icon: 'material-symbols:home-outline', activeIcon: 'material-symbols:home' },
    { path: '/shop', label: '商城', icon: 'material-symbols:shopping-bag-outline', activeIcon: 'material-symbols:shopping-bag' }
  ]
  if (isLoggedIn.value) {
    items.push({ path: '/create', label: '发布新帖', icon: 'material-symbols:edit-note-outline', activeIcon: 'material-symbols:edit-note' })
    items.push({ path: '/messages', label: '私信', icon: 'material-symbols:chat-outline', activeIcon: 'material-symbols:chat', badge: props.messageCount })
    items.push({ path: '/notifications', label: '消息中心', icon: 'material-symbols:notifications-outline', activeIcon: 'material-symbols:notifications', badge: props.notifCount })
    items.push({ path: '/shop/cart', label: '购物车', icon: 'material-symbols:shopping-cart-outline', activeIcon: 'material-symbols:shopping-cart' })
    items.push({ path: '/shop/orders', label: '我的订单', icon: 'material-symbols:receipt-long-outline', activeIcon: 'material-symbols:receipt-long' })
    items.push({ path: '/me', label: '个人中心', icon: 'material-symbols:person-outline', activeIcon: 'material-symbols:person' })
  }
  return items
})
</script>
