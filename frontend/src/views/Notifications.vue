<template>
  <NotificationsWeb v-if="isDesktop" />
  <NotificationsMobile v-else />
</template>

<script setup>
import { useViewportMode } from '../composables/useViewportMode'
import NotificationsWeb from './notifications/NotificationsWeb.vue'
import NotificationsMobile from './notifications/NotificationsMobile.vue'

const { isDesktop } = useViewportMode()
</script>
