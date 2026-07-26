<template>
  <CartWeb v-if="isDesktop" />
  <CartMobile v-else />
</template>

<script setup>
import { useViewportMode } from '../composables/useViewportMode'
import CartWeb from './cart/CartWeb.vue'
import CartMobile from './cart/CartMobile.vue'

const { isDesktop } = useViewportMode()
</script>
