<template>
  <AIChatWeb v-if="isDesktop" />
  <AIChatMobile v-else />
</template>

<script setup>
import { useViewportMode } from '../composables/useViewportMode'
import AIChatWeb from './ai-chat/AIChatWeb.vue'
import AIChatMobile from './ai-chat/AIChatMobile.vue'

const { isDesktop } = useViewportMode()
</script>
