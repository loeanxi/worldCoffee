<template>
  <article
    class="feed-post-card wc-feed-card group relative overflow-hidden cursor-pointer animate-fade-in"
    :data-post-id="postId"
    role="button"
    tabindex="0"
    @click="emit('click')"
    @keydown.enter.prevent="emit('click')"
  >
    <!-- 图片区：有图渲染图片，无图渲染品牌占位；徽标与悬停浮层共用一份 -->
    <div
      class="wc-feed-image-wrap relative overflow-hidden"
      :class="{ 'brand-placeholder': !(imageUrl && !imageFailed) }"
    >
      <img
        v-if="imageUrl && !imageFailed"
        :src="imageUrl"
        :alt="title"
        width="320"
        height="426"
        loading="lazy"
        decoding="async"
        class="wc-feed-cover w-full h-full object-cover block"
        @error="imageFailed = true"
      />
      <div v-else class="w-full h-full flex items-center justify-center">
        <WorldCoffeeLogoMini :size="48" :with-circle="false" />
      </div>

      <span
        v-if="likeCount >= 10"
        class="wc-hot-badge absolute top-2 left-2 flex items-center gap-1"
      >
        <Icon icon="material-symbols:local-fire-department" class="w-3 h-3" />
        热帖
      </span>
      <span
        v-if="isVideo"
        class="wc-video-badge absolute bottom-2 right-2 flex items-center gap-1"
      >
        <Icon icon="material-symbols:play-arrow" class="w-3 h-3" />
        {{ videoDurationText }}
      </span>

      <!-- 悬停浮层快捷操作（仅 hover 设备可见）：赞 / 藏 / 分享 -->
      <div class="wc-feed-hover-actions" @click.stop>
        <button
          class="wc-hover-act tap-scale"
          :class="{ 'is-on': liked }"
          :title="liked ? '取消点赞' : '点赞'"
          :aria-label="liked ? '取消点赞' : '点赞'"
          @click.stop="emit('like')"
        >
          <Icon :icon="liked ? 'material-symbols:favorite' : 'material-symbols:favorite-outline'" class="w-4 h-4" />
        </button>
        <button
          class="wc-hover-act tap-scale"
          :class="{ 'is-on': favored }"
          :title="favored ? '取消收藏' : '收藏'"
          :aria-label="favored ? '取消收藏' : '收藏'"
          @click.stop="emit('favorite')"
        >
          <Icon :icon="favored ? 'material-symbols:bookmark' : 'material-symbols:bookmark-outline'" class="w-4 h-4" />
        </button>
        <button
          class="wc-hover-act tap-scale"
          title="分享"
          aria-label="分享"
          @click.stop="emit('share')"
        >
          <Icon icon="material-symbols:share-outline" class="w-4 h-4" />
        </button>
      </div>
    </div>

    <div class="wc-feed-body">
      <h3 class="wc-feed-title text-[13px] text-ink leading-snug line-clamp-2 font-semibold">
        {{ title }}
      </h3>

      <div class="wc-feed-meta flex items-center justify-between">
        <div class="flex items-center gap-1.5 min-w-0">
          <img
            v-if="authorAvatar"
            :src="authorAvatar"
            width="20"
            height="20"
            loading="lazy"
            decoding="async"
            class="w-5 h-5 rounded-full object-cover shrink-0"
            :alt="authorName"
          />
          <span
            v-else
            class="w-5 h-5 rounded-full bg-surface-soft flex items-center justify-center shrink-0 text-[10px] font-bold text-ink-muted"
          >
            {{ authorName.slice(0, 1).toUpperCase() }}
          </span>
          <span class="text-[11.5px] text-ink-muted truncate">{{ authorName }}</span>
        </div>

        <div class="flex items-center gap-1 shrink-0 text-ink-muted">
          <Icon icon="material-symbols:favorite-border" class="w-3.5 h-3.5 text-[var(--brand-red)]" />
          <span class="text-[11.5px]">{{ formattedLikeCount }}</span>
        </div>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import { normalizeUrl } from '../api'
import WorldCoffeeLogoMini from './WorldCoffeeLogoMini.vue'

const props = defineProps({
  post: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['click', 'like', 'favorite', 'share'])
const imageFailed = ref(false)

const postId = computed(() => props.post?.id || props.post?.postId || '')

const title = computed(() => {
  const raw = props.post?.title || props.post?.content || '一杯咖啡的瞬间'
  return String(raw).replace(/<[^>]*>/g, '').trim() || '一杯咖啡的瞬间'
})

const imageUrl = computed(() => {
  const post = props.post
  if (!post) return ''
  if (Array.isArray(post.images) && post.images.length > 0) {
    const first = post.images[0]
    return normalizeUrl(typeof first === 'string' ? first : (first?.url || first?.imageUrl || ''))
  }
  if (typeof post.images === 'string' && post.images.trim()) {
    const raw = post.images.trim()
    if (raw.startsWith('[') || raw.startsWith('{')) {
      try {
        const parsed = JSON.parse(raw)
        if (Array.isArray(parsed) && parsed.length > 0) {
          const first = parsed[0]
          return normalizeUrl(typeof first === 'string' ? first : (first?.url || first?.imageUrl || ''))
        }
      } catch {}
    }
    return normalizeUrl(raw.split(',').map(item => item.trim()).filter(Boolean)[0] || '')
  }
  return normalizeUrl(post.imageUrl || post.coverImage || post.cover || post.coverUrl || '')
})

const authorAvatar = computed(() => normalizeUrl(
  props.post?.author?.avatar
  || props.post?.user?.avatar
  || props.post?.authorAvatar
  || props.post?.userAvatar
  || props.post?.avatar
  || ''
))

const authorName = computed(() => (
  props.post?.author?.nickname
  || props.post?.author?.username
  || props.post?.user?.nickname
  || props.post?.user?.username
  || props.post?.nickname
  || props.post?.username
  || props.post?.authorName
  || '咖啡爱好者'
))

const likeCount = computed(() => Number(props.post?.like_count ?? props.post?.likeCount ?? props.post?.likes ?? 0) || 0)

const liked = computed(() => !!(props.post?.likedByMe ?? props.post?.liked ?? false))

const favored = computed(() => !!(props.post?.favoritedByMe ?? props.post?.favorited ?? false))

/** 视频笔记：有 noteType=VIDEO 或带 videoUrl 即视为视频 */
const isVideo = computed(() => props.post?.noteType === 'VIDEO' || !!props.post?.videoUrl)

const videoDurationText = computed(() => {
  const d = Number(props.post?.videoDuration) || 0
  if (d <= 0) return '视频'
  return `${Math.floor(d / 60)}:${String(Math.floor(d % 60)).padStart(2, '0')}`
})

const formattedLikeCount = computed(() => {
  const n = likeCount.value
  if (n >= 10000) return `${(n / 10000).toFixed(1).replace(/\.0$/, '')}w`
  if (n >= 1000) return `${(n / 1000).toFixed(1).replace(/\.0$/, '')}k`
  return n
})

watch(imageUrl, () => {
  imageFailed.value = false
})
</script>
