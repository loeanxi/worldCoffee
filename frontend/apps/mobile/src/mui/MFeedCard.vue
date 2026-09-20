<!-- ============================================================
     MFeedCard —— 发现页双列瀑布流卡片
     图（自然比例）+ 标题两行 + 作者头像昵称 + 点赞
============================================================ -->
<template>
  <article class="m-card overflow-hidden mb-2.5 break-inside-avoid cursor-pointer tap-scale" @click="$emit('open', post)">
    <img
      v-if="cover"
      :src="cover"
      class="w-full h-auto block"
      style="background: var(--m-brand-soft);"
      loading="lazy"
      alt=""
    />
    <div class="p-2.5">
      <h3 class="text-[13px] font-semibold leading-snug line-clamp-2" :style="{ color: 'var(--m-ink)' }">
        {{ post.title || '无标题笔记' }}
      </h3>
      <div class="mt-2 flex items-center gap-1.5">
        <img v-if="avatar" :src="avatar" class="w-4.5 h-4.5 w-[18px] h-[18px] rounded-full object-cover shrink-0" alt="" />
        <span class="flex-1 truncate text-[11px]" :style="{ color: 'var(--m-ink-3)' }">{{ authorName }}</span>
        <button type="button" class="flex items-center gap-0.5 tap-scale" :style="{ color: liked ? 'var(--m-accent)' : 'var(--m-ink-3)' }" @click.stop="$emit('like', post)">
          <Icon :icon="liked ? 'material-symbols:favorite' : 'material-symbols:favorite-outline'" class="w-3.5 h-3.5" />
          <span class="text-[11px]">{{ formatCount(likeCount) }}</span>
        </button>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Icon } from '@iconify/vue'
import { normalizeUrl } from '@wc/shared'

const props = defineProps({
  post: { type: Object, default: () => ({}) }
})
defineEmits(['open', 'like'])

const cover = computed(() => {
  const p = props.post
  if (Array.isArray(p.images) && p.images.length) {
    const first = p.images[0]
    return normalizeUrl(typeof first === 'string' ? first : (first?.url || first?.imageUrl || ''))
  }
  if (typeof p.images === 'string' && p.images) return normalizeUrl(p.images.split(',')[0])
  return normalizeUrl(p.imageUrl || p.coverImage || p.cover || '')
})

const avatar = computed(() => normalizeUrl(
  props.post._avatar || props.post.avatar || props.post.authorAvatar
  || props.post.user?.avatar || props.post.author?.avatar || ''
))

const authorName = computed(() =>
  props.post.username || props.post.authorName || props.post.nickname
  || props.post.user?.username || props.post.author?.username || '用户'
)

const liked = computed(() => !!props.post.likedByMe)
const likeCount = computed(() => props.post.likeCount || props.post.likes || 0)

function formatCount(n) {
  if (!n) return 0
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return n
}
</script>
