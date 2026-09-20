<!-- ============================================================
     移动端登录页（独立设计）
     官方商标全标 + 胶囊输入 + 品牌绿主按钮
============================================================ -->
<template>
  <div class="min-h-screen flex flex-col items-center justify-center px-8" style="background: var(--m-bg);">
    <WorldCoffeeLogo :size="150" variant="full" />
    <p class="mt-1 text-[11px] tracking-[0.28em]" style="color: var(--m-ink-3);">一杯咖啡，一个社区</p>

    <form class="mt-9 w-full max-w-sm space-y-3" @submit.prevent="handleLogin">
      <input v-model="form.username" class="m-input" type="text" placeholder="用户名 / 邮箱" required />
      <input v-model="form.password" class="m-input" type="password" placeholder="密码" required />
      <p v-if="loginError" class="text-[12px] text-center" style="color: #D46A3D;">{{ loginError }}</p>
      <button type="submit" class="m-btn-primary w-full h-12" :disabled="loading">
        {{ loading ? '登录中…' : '登 录' }}
      </button>
    </form>

    <p class="mt-6 text-[13px]" style="color: var(--m-ink-2);">
      还没有账号？
      <router-link to="/register" class="font-semibold" style="color: var(--m-brand);">立即注册</router-link>
    </p>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth, WorldCoffeeLogo } from '@wc/shared'

const router = useRouter()
const { login } = useAuth()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const loginError = ref('')

async function handleLogin() {
  loginError.value = ''
  loading.value = true
  try {
    const ok = await login({ username: form.username, password: form.password, remember: true })
    if (ok) router.push('/')
    else loginError.value = '用户名或密码错误'
  } catch (e) {
    loginError.value = '网络错误，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>
