<template>
  <OrdersWeb v-if="isDesktop" />
  <OrdersMobile v-else />
</template>

<script setup>
import { useViewportMode } from '../composables/useViewportMode'
import OrdersWeb from './orders/OrdersWeb.vue'
import OrdersMobile from './orders/OrdersMobile.vue'

const { isDesktop } = useViewportMode()
</script>
