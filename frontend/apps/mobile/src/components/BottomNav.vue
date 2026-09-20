<!-- ============================================================
     底部 TabBar（移动端独立设计）
     发现 / 商城 / (＋发布) / 消息 / 我的
     品牌绿激活态 + 中央凸出发布键；宽屏也保持居中窄列
============================================================ -->
<template>
  <nav class="fixed bottom-0 inset-x-0 z-50">
    <div class="m-col m-tabbar-inner flex items-center px-2" :style="{ paddingBottom: 'max(env(safe-area-inset-bottom,0px), 6px)' }">
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        class="m-tab-item relative tap-scale"
        :class="{ 'is-active': isActive(item) }"
        @click="go(item)"
      >
        <span class="relative">
          <Icon :icon="isActive(item) ? item.activeIcon : item.icon" class="w-[22px] h-[22px]" />
          <span v-if="badgeOf(item) > 0" class="m-badge">{{ badgeOf(item) > 99 ? '99+' : badgeOf(item) }}</span>
        </span>
        <span>{{ item.label }}</span>
      </button>

      <!-- 中央凸出发布键：插在商城与消息之间 -->
      <button type="button" class="m-tab-center tap-scale shrink-0" aria-label="发布笔记" @click="goCreate">
        <Icon icon="material-symbols:add" class="w-6 h-6" />
      </button>

      <button
        v-for="item in itemsRight"
        :key="item.key"
        type="button"
        class="m-tab-item relative tap-scale"
        :class="{ 'is-active': isActive(item) }"
        @click="go(item)"
      >
        <span class="relative">
          <Icon :icon="isActive(item) ? item.activeIcon : item.icon" class="w-[22px] h-[22px]" />
          <span v-if="badgeOf(item) > 0" class="m-badge">{{ badgeOf(item) > 99 ? '99+' : badgeOf(item) }}</span>
        </span>
        <span>{{ item.label }}</span>
      </button>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Icon } from '@iconify/vue'
import { useAuth } from '@wc/shared'

const props = defineProps({
  notifCount: { type: Number, default: 0 },
  messageCount: { type: Number, default: 0 }
})

const router = useRouter()
const route = useRoute()
const { isLoggedIn } = useAuth()

const items = computed(() => [
  { key: 'Home', path: '/', label: '发现', icon: 'material-symbols:explore-outline', activeIcon: 'material-symbols:explore' },
  { key: 'Shop', path: '/shop', label: '商城', icon: 'material-symbols:storefront-outline', activeIcon: 'material-symbols:storefront' }
])
const itemsRight = computed(() => [
  { key: 'Messages', path: '/messages', label: '消息', icon: 'material-symbols:chat-bubble-outline', activeIcon: 'material-symbols:chat-bubble', badge: props.messageCount },
  { key: 'Me', path: '/me', label: '我的', icon: 'material-symbols:person-outline', activeIcon: 'material-symbols:person' }
])

function isActive(item) {
  return route.name === item.key
}

function badgeOf(item) {
  return item.badge || 0
}

function go(item) {
  if (item.key === 'Messages' && !isLoggedIn.value) {
    router.push('/login')
    return
  }
  router.push(item.path)
}

function goCreate() {
  router.push(isLoggedIn.value ? '/create' : '/login')
}
</script>
