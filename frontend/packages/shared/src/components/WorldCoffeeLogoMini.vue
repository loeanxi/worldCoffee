<!-- ============================================================
     WorldCoffee 极简 Logo（兼容层：内部复用官方商标组件）
     保留旧 props（size / variant / withCircle）以兼容各端调用；
     withCircle 已废弃——新官方商标自带圆环，无需额外描边背景
============================================================ -->
<template>
  <WorldCoffeeLogo :size="size" :variant="variant === 'icon' ? 'icon' : 'full'" />
</template>

<script setup lang="ts">
import WorldCoffeeLogo from './WorldCoffeeLogo.vue'

defineProps({
  size: { type: Number, default: 120 },
  // icon: 纯图标; full|mark: 图标 + 字标
  variant: { type: String, default: 'icon' },
  // 兼容旧 API，不再生效
  withCircle: { type: Boolean, default: true }
})
</script>
