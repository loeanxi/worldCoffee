<!-- ============================================================
     MSheet —— 底部半屏弹层（规格选择 / 评论输入 / 菜单等）
     点遮罩关闭；面板圆角 24px 顶部
============================================================ -->
<template>
  <Teleport to="body">
    <Transition name="m-sheet">
      <div v-if="modelValue" class="fixed inset-0 z-[80] flex items-end justify-center">
        <div class="absolute inset-0 bg-black/35" @click="$emit('update:modelValue', false)" />
        <section class="m-sheet-panel relative w-full max-w-[640px] max-h-[82vh] flex flex-col">
          <header class="flex items-center justify-between px-5 pt-4 pb-3 shrink-0">
            <h2 class="text-[15px] font-bold" :style="{ color: 'var(--m-ink)' }">{{ title }}</h2>
            <button type="button" class="w-8 h-8 flex items-center justify-center rounded-full tap-scale" :style="{ background: 'var(--m-brand-soft)', color: 'var(--m-ink-2)' }" aria-label="关闭" @click="$emit('update:modelValue', false)">
              <Icon icon="material-symbols:close" class="w-4 h-4" />
            </button>
          </header>
          <div class="overflow-y-auto px-5 pb-[max(env(safe-area-inset-bottom,0px),20px)]" style="overscroll-behavior: contain;">
            <slot />
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { Icon } from '@iconify/vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' }
})
defineEmits(['update:modelValue'])
</script>
