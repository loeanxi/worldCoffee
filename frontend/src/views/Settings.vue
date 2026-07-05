<template>
  <div class="min-h-screen pb-24 md:pb-8 bg-coffee-foam/30">
    <!-- Header -->
    <header class="sticky top-0 z-40 bg-white/90 backdrop-blur-xl border-b border-coffee-latte/30">
      <div class="max-w-2xl mx-auto px-4 h-14 flex items-center gap-3">
        <button class="p-2 rounded-xl hover:bg-coffee-foam transition-colors" @click="router.back()">
          <Icon icon="material-symbols:arrow-back" class="w-5 h-5 text-coffee-brown" />
        </button>
        <h1 class="text-base font-semibold text-coffee-espresso flex-1">设置</h1>
      </div>
    </header>

    <main class="max-w-2xl mx-auto px-4 pt-5 space-y-4">

      <!-- ─── 用户卡片 ─────────────────────────── -->
      <div class="bg-white rounded-3xl shadow-[0_6px_28px_rgba(62,39,35,0.10)] overflow-hidden animate-fade-up">
        <div class="p-5 flex items-center gap-4">
          <div class="w-14 h-14 rounded-2xl overflow-hidden bg-gradient-to-br from-coffee-brown to-coffee-dark shadow-md flex-shrink-0">
            <img v-if="userAvatar" :src="userAvatar" class="w-full h-full object-cover" />
            <span v-else class="w-full h-full flex items-center justify-center text-white text-xl font-bold">
              {{ userName?.charAt(0)?.toUpperCase() || '?' }}
            </span>
          </div>
          <div class="flex-1 min-w-0">
            <p class="text-base font-bold text-coffee-espresso truncate">{{ userName || '—' }}</p>
            <p class="text-xs text-coffee-mocha/50 mt-0.5 truncate">{{ userPhone || '未设置手机号' }}</p>
          </div>
        </div>
      </div>

      <!-- ─── 功能菜单 ─────────────────────────── -->
      <div class="bg-white rounded-3xl shadow-[0_6px_28px_rgba(62,39,35,0.10)] overflow-hidden animate-fade-up" style="animation-delay: 0.04s">

        <!-- 账号管理 -->
        <button
          class="w-full px-5 py-4 flex items-center gap-3.5 hover:bg-coffee-foam/50 transition-colors border-b border-coffee-latte/20"
          @click="router.push('/settings/account')"
        >
          <div class="w-9 h-9 rounded-xl bg-coffee-brown/10 flex items-center justify-center flex-shrink-0">
            <Icon icon="material-symbols:edit-note" class="w-5 h-5 text-coffee-brown" />
          </div>
          <div class="flex-1 text-left">
            <p class="text-sm font-semibold text-coffee-espresso">账号管理</p>
            <p class="text-[11px] text-coffee-mocha/50 mt-0.5">修改资料、修改密码</p>
          </div>
          <Icon icon="material-symbols:chevron-right" class="w-5 h-5 text-coffee-mocha/30" />
        </button>

        <!-- 关于我们 -->
        <button
          class="w-full px-5 py-4 flex items-center gap-3.5 hover:bg-coffee-foam/50 transition-colors"
          @click="router.push('/settings/about')"
        >
          <div class="w-9 h-9 rounded-xl bg-coffee-brown/10 flex items-center justify-center flex-shrink-0">
            <Icon icon="material-symbols:info-outline" class="w-5 h-5 text-coffee-brown" />
          </div>
          <div class="flex-1 text-left">
            <p class="text-sm font-semibold text-coffee-espresso">关于我们</p>
            <p class="text-[11px] text-coffee-mocha/50 mt-0.5">版本信息、联系方式</p>
          </div>
          <Icon icon="material-symbols:chevron-right" class="w-5 h-5 text-coffee-mocha/30" />
        </button>
      </div>

      <!-- ─── 退出登录 ─────────────────────────── -->
      <div class="pt-2 animate-fade-up" style="animation-delay: 0.08s">
        <button
          class="w-full py-3.5 rounded-2xl bg-white border border-red-200/60 text-red-500 font-semibold text-sm transition-all hover:bg-red-50 flex items-center justify-center gap-1.5"
          @click="handleLogout"
        >
          <Icon icon="material-symbols:logout" class="w-4 h-4" /> 退出登录
        </button>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, inject } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { userApi, getApiError } from '../api'
import { useAuth } from '../composables/useAuth'

const router = useRouter()
const toast = inject('toast')
const { avatar: authAvatar } = useAuth()

const userName = ref('')
const userPhone = ref('')
const userAvatar = ref('')

async function handleLogout() {
  try {
    await userApi.logout()
  } catch (e) { /* 即使后端调用失败也继续本地清理 */ }
  toast.logout()
  toast.show('已退出登录')
  router.push('/login')
}

async function fetchUser() {
  try {
    const res = await userApi.getMe()
    if (res && res.code === 200 && res.data) {
      userName.value = res.data.username || ''
      userPhone.value = res.data.phone || ''
      userAvatar.value = res.data.avatar || authAvatar.value || ''
    }
  } catch (e) {
    // 静默失败，设置页不弹错误
  }
}

onMounted(() => {
  fetchUser()
})
</script>
