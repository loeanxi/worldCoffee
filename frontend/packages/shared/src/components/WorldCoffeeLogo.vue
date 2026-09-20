<!-- ============================================================
     WorldCoffee 官方商标（loean worldcoffee · 绿环咖啡豆）
     资产由 loean-worldcoffee-logo/build-official-assets.ps1 生成的透明底 PNG
     使用： <WorldCoffeeLogo :size="120" variant="icon" />   仅图标
            <WorldCoffeeLogo :size="160" variant="full" />   图标 + 字标
            <WorldCoffeeLogo :size="200" variant="mark" />   同 full（商标自带字标）
============================================================ -->
<template>
  <img
    class="wc-brand-logo inline-block select-none"
    :src="variant === 'icon' ? logoMark : logoFull"
    :style="{ width: px, height: 'auto' }"
    alt="loean worldcoffee"
    draggable="false"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import logoMark from '../assets/logo-mark.png'
import logoFull from '../assets/logo-full.png'

const props = defineProps({
  // 尺寸（px），也支持预设 sm/md/lg
  size: { type: [Number, String], default: 120 },
  // icon: 仅图标; full|mark: 图标 + 字标
  variant: { type: String, default: 'icon' }
})

const presets: Record<string, number> = { sm: 72, md: 120, lg: 200 }
const px = computed(() => {
  const n = typeof props.size === 'number' ? props.size : (presets[String(props.size)] ?? 120)
  return n + 'px'
})
</script>
