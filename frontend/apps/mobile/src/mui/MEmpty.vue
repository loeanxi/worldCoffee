<!-- ============================================================
     MEmpty —— 空状态占位（图标 + 文案）
============================================================ -->
<template>
  <div class="flex flex-col items-center justify-center py-16 px-6 text-center">
    <Icon :icon="icon" class="w-12 h-12 mb-3" :style="{ color: 'var(--m-ink-3)' }" />
    <p class="text-[13px] leading-relaxed" :style="{ color: 'var(--m-ink-3)' }">{{ text }}</p>
    <slot />
  </div>
</template>

<script setup lang="ts">
import { Icon } from '@iconify/vue'

defineProps({
  text: { type: String, default: '这里空空如也' },
  icon: { type: String, default: 'material-symbols:coffee-outline' }
})
</script>
