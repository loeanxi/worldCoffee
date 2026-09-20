<!-- ============================================================
     WorldCoffee 登录页（loean worldcoffee · 左右分屏品牌叙事）
     左：品牌视觉 + 社交证明；右：登录表单
============================================================ -->
<template>
  <div class="min-h-screen bg-surface flex">
    <!-- ===== 左半：品牌叙事（桌面端显示） ===== -->
    <div class="wc-login-brand flex relative flex-col justify-between overflow-hidden">
      <!-- 背景装饰：暖棕渐变 + 光晕 + 网格 -->
      <div class="absolute inset-0 wc-login-brand-bg" />
      <div class="absolute -top-20 -left-16 w-96 h-96 rounded-full blur-3xl opacity-30 animate-float" style="background: radial-gradient(circle, #EEC27B, transparent 70%)" />
      <div class="absolute bottom-[-10%] right-[-6%] w-[26rem] h-[26rem] rounded-full blur-3xl opacity-25 animate-float" style="background: radial-gradient(circle, #7A9B84, transparent 70%); animation-delay: -2.5s" />

      <!-- 顶部 Logo -->
      <div class="relative z-10 px-12 pt-12">
        <WorldCoffeeLogo :size="52" variant="icon" class="wc-login-logo-drop" />
      </div>

      <!-- 中部主文案 -->
      <div class="relative z-10 px-12">
        <h1 class="wc-login-headline">loean<br />worldcoffee</h1>
        <p class="wc-login-tagline">发现你的那一杯好咖啡，记录城市里的咖啡时光</p>

        <!-- 社交证明 -->
        <div class="flex flex-wrap gap-3 mt-8">
          <div v-for="stat in brandStats" :key="stat.label" class="wc-login-stat">
            <strong>{{ stat.value }}</strong>
            <span>{{ stat.label }}</span>
          </div>
        </div>
      </div>

      <!-- 底部引言 -->
      <div class="relative z-10 px-12 pb-12">
        <p class="wc-login-quote">“ 每一杯咖啡，都值得被认真记录。 ”</p>
      </div>
    </div>

    <!-- ===== 右半：登录表单 ===== -->
    <div class="flex-1 flex items-center justify-center px-4 py-10">
      <div class="w-full max-w-sm">
        <!-- 移动端 Logo（分屏隐藏时显示） -->
        <div class="hidden text-center mb-8">
          <WorldCoffeeLogo :size="88" variant="full" />
        </div>

        <div class="bg-surface-elevated rounded-[28px] p-7 border border-line/40 animate-fade-up" style="box-shadow: var(--shadow-card);">
          <h2 class="text-[22px] font-bold text-ink text-center">欢迎回来</h2>
          <p class="mt-2 text-center text-[13px] text-ink-muted">用账号登录，发现你的好咖啡</p>

          <!-- 表单 -->
          <form class="mt-7 space-y-4" @submit.prevent="handleLogin">
            <div>
              <label class="text-[12px] font-semibold text-ink-soft mb-1.5 block">账号</label>
              <div class="relative">
                <Icon icon="material-symbols:person-outline" class="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-ink-muted pointer-events-none" />
                <input
                  v-model="form.username"
                  type="text"
                  placeholder="用户名 / 邮箱"
                  class="w-full h-11 pl-10 pr-4 rounded-xl bg-surface-soft border border-transparent focus:border-line focus:bg-surface-elevated text-[14px] text-ink placeholder:text-ink-muted outline-none transition-all"
                  required
                />
              </div>
            </div>

            <div>
              <label class="text-[12px] font-semibold text-ink-soft mb-1.5 block">密码</label>
              <div class="relative">
                <Icon icon="material-symbols:lock-outline" class="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-ink-muted pointer-events-none" />
                <input
                  v-model="form.password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="请输入密码"
                  class="w-full h-11 pl-10 pr-11 rounded-xl bg-surface-soft border border-transparent focus:border-line focus:bg-surface-elevated text-[14px] text-ink placeholder:text-ink-muted outline-none transition-all"
                  required
                />
                <button
                  type="button"
                  class="absolute right-3 top-1/2 -translate-y-1/2 p-1 text-ink-muted hover:text-ink transition-colors"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  @click="showPassword = !showPassword"
                >
                  <Icon :icon="showPassword ? 'material-symbols:visibility-off-outline' : 'material-symbols:visibility-outline'" class="w-4 h-4" />
                </button>
              </div>
            </div>

            <div class="flex items-center justify-between text-[12px] pt-1">
              <label class="flex items-center gap-2 text-ink-muted cursor-pointer">
                <input v-model="form.remember" type="checkbox" class="w-4 h-4 rounded" style="accent-color: var(--accent);" />
                <span>记住我</span>
              </label>
              <a class="text-ink font-medium hover:underline" href="javascript:;">忘记密码？</a>
            </div>

            <p v-if="loginError" class="text-[12px] text-red-500 text-center">{{ loginError }}</p>

            <button
              type="submit"
              class="w-full h-12 rounded-xl text-[15px] font-semibold active:scale-[0.98] transition-all tap-scale brand-gradient-btn"
            >
              登录
            </button>
          </form>

          <!-- 分隔 -->
          <div class="flex items-center gap-3 my-6">
            <span class="flex-1 h-px" style="background: var(--divider);" />
            <span class="text-[11px] text-ink-muted tracking-widest">或</span>
            <span class="flex-1 h-px" style="background: var(--divider);" />
          </div>

          <!-- 注册入口 -->
          <p class="text-center text-[13px] text-ink-muted">
            还没有账号？
            <router-link to="/register" class="ml-1 text-ink font-semibold hover:underline">立即注册</router-link>
          </p>

          <!-- 第三方 -->
          <div class="mt-5 flex items-center justify-center gap-4">
            <button class="wc-login-social tap-scale" title="微信登录">
              <Icon icon="simple-icons:wechat" class="w-5 h-5 text-[#07C160]" />
            </button>
            <button class="wc-login-social tap-scale" title="邮箱登录">
              <Icon icon="material-symbols:mail-outline" class="w-5 h-5 text-ink-soft" />
            </button>
            <button class="wc-login-social tap-scale" title="Apple 登录">
              <Icon icon="simple-icons:apple" class="w-5 h-5 text-ink" />
            </button>
          </div>
        </div>

        <!-- 返回首页 -->
        <div class="mt-6 text-center">
          <router-link to="/" class="text-[12px] text-ink-muted hover:text-ink transition-colors">
            ← 返回首页
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { useAuth } from '@wc/shared'
import { WorldCoffeeLogo } from '@wc/shared'

const router = useRouter()
const { login } = useAuth()

const form = reactive({
  username: '',
  password: '',
  remember: false
})

const loginError = ref('')
const showPassword = ref(false)

// 品牌社交证明数据
const brandStats = [
  { value: '10,000+', label: '咖啡笔记' },
  { value: '500+', label: '城市咖啡馆' },
  { value: 'AI', label: '咖啡助手' }
]

async function handleLogin() {
  loginError.value = ''
  try {
    const ok = await login({
      username: form.username,
      password: form.password,
      remember: form.remember
    })
    if (ok) {
      router.push('/')
    } else {
      loginError.value = '用户名或密码错误'
    }
  } catch (e) {
    console.error('登录失败', e)
    loginError.value = '网络错误，请稍后重试'
  }
}
</script>

<style scoped>
/* 左半品牌区背景：暖棕深焙渐变 */
.wc-login-brand {
  width: 46%;
  max-width: 620px;
  color: #FFF8E1;
}
.wc-login-brand-bg {
  background:
    radial-gradient(circle at 20% 12%, rgba(238, 194, 123, 0.20), transparent 42%),
    radial-gradient(circle at 85% 80%, rgba(122, 155, 132, 0.16), transparent 40%),
    linear-gradient(150deg, #4E342E 0%, #3E2723 45%, #2C1810 100%);
}
.wc-login-brand-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  opacity: .22;
  background-image:
    linear-gradient(rgba(255, 248, 225, .05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 248, 225, .05) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: linear-gradient(to bottom, rgba(0,0,0,.6), transparent 70%);
}
.wc-login-logo-drop {
  filter: drop-shadow(0 8px 24px rgba(0, 0, 0, 0.35));
}
.wc-login-headline {
  font-family: 'DM Serif Display', 'Noto Serif SC', Georgia, serif;
  font-size: 52px;
  line-height: 1.05;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #FFF8E1;
}
.wc-login-tagline {
  margin-top: 14px;
  max-width: 340px;
  font-size: 15px;
  line-height: 1.7;
  color: rgba(255, 248, 225, 0.78);
}
.wc-login-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 12px 18px;
  border-radius: 16px;
  background: rgba(255, 248, 225, 0.10);
  border: 1px solid rgba(255, 248, 225, 0.16);
  backdrop-filter: blur(8px);
}
.wc-login-stat strong {
  font-size: 18px;
  font-weight: 800;
  color: #EEC27B;
}
.wc-login-stat span {
  font-size: 11px;
  color: rgba(255, 248, 225, 0.7);
}
.wc-login-quote {
  font-size: 13px;
  font-style: italic;
  color: rgba(255, 248, 225, 0.55);
}
.wc-login-social {
  width: 42px;
  height: 42px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  transition: background .2s ease, transform .15s ease, box-shadow .2s ease;
}
.wc-login-social:hover {
  background: var(--bg-elevated);
  box-shadow: var(--shadow-xs);
}
</style>
