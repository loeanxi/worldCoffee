<template>
  <MessagesWeb v-if="isDesktop" />
  <MessagesMobile v-else />
</template>

<script setup>
import { useViewportMode } from '../composables/useViewportMode'
import MessagesWeb from './messages/MessagesWeb.vue'
import MessagesMobile from './messages/MessagesMobile.vue'

const { isDesktop } = useViewportMode()
</script>
