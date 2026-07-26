<template>
  <ShopWeb v-if="isDesktop" />
  <ShopMobile v-else />
</template>

<script setup>
import { useViewportMode } from '../composables/useViewportMode'
import ShopWeb from './shop/ShopWeb.vue'
import ShopMobile from './shop/ShopMobile.vue'

const { isDesktop } = useViewportMode()
</script>
