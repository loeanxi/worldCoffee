<!-- ============================================================
     MChip —— 分类/筛选胶囊（横滑排布由父容器 m-scroll-x 提供）
============================================================ -->
<template>
  <button
    type="button"
    class="m-pill shrink-0 h-8 px-3.5 text-[13px] font-medium transition-all tap-scale"
    :style="active
      ? { background: 'var(--m-brand)', color: '#fff' }
      : { background: 'var(--m-card)', color: 'var(--m-ink-2)', border: '1px solid var(--m-line)' }"
    @click="$emit('click')"
  >
    {{ label }}
  </button>
</template>

<script setup lang="ts">
defineProps({
  label: { type: String, default: '' },
  active: { type: Boolean, default: false }
})
defineEmits(['click'])
</script>
