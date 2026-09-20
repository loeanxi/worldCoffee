<!-- ============================================================
     MNavBar —— 移动端二级页顶栏（返回 + 标题 + 右槽）
============================================================ -->
<template>
  <header class="m-topbar flex items-center gap-2 px-3 h-12">
    <button v-if="back" type="button" class="w-9 h-9 -ml-1 flex items-center justify-center rounded-full tap-scale" aria-label="返回" @click="goBack">
      <Icon icon="material-symbols:arrow-back-rounded" class="w-5 h-5" :style="{ color: 'var(--m-ink)' }" />
    </button>
    <h1 class="flex-1 truncate text-[16px] font-bold" :style="{ color: 'var(--m-ink)' }">{{ title }}</h1>
    <slot name="right" />
  </header>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'

defineProps({
  title: { type: String, default: '' },
  back: { type: Boolean, default: true }
})

const router = useRouter()
function goBack() {
  if (window.history.state?.back) router.back()
  else router.push('/')
}
</script>
