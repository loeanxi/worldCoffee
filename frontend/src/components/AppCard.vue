<template>
  <div :class="['glass-card-solid overflow-hidden', wide ? 'max-w-xl' : 'max-w-md', 'w-full mx-auto']">
    <slot />
  </div>
</template>

<script setup>
defineProps({
  wide: Boolean
})
</script>
