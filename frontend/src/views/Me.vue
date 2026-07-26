<template>
  <MeWeb v-if="isDesktop" />
  <MeMobile v-else />
</template>

<script setup>
import { useViewportMode } from '../composables/useViewportMode'
import MeWeb from './me/MeWeb.vue'
import MeMobile from './me/MeMobile.vue'

const { isDesktop } = useViewportMode()
</script>
