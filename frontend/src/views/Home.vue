<template>
  <div class="min-h-screen">
    <!-- ================== 顶部栏 (小红书风格) ================== -->
    <header class="fixed top-0 left-0 right-0 z-40 bg-surface-elevated/90 backdrop-blur-xl border-b border-line">
      <div class="max-w-7xl mx-auto px-3 md:px-6 py-2.5">
        <div class="flex items-center gap-3">
          <!-- 左上角 Logo（连点三次跳转关于我们） -->
          <div
            class="shrink-0 flex items-center gap-2 group cursor-pointer hover:opacity-80 transition-opacity select-none"
            @click="handleLogoClick"
          >
            <WorldCoffeeAiLogo :size="36" />
            <span class="hidden md:inline text-[15px] font-bold text-ink tracking-wide group-hover:text-ink-soft/90 transition-colors">WorldCoffee</span>
          </div>

          <!-- 桌面端：搜索框 + Tab -->
          <div class="hidden lg:flex items-center gap-6 flex-1">
            <div class="relative flex-1 max-w-md">
              <Icon icon="material-symbols:search" class="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-ink-muted pointer-events-none"/>
              <input
                v-model="searchQuery"
                type="text"
                placeholder="搜索咖啡、地点、话题..."
                class="w-full min-h-[40px] pl-11 pr-4 rounded-full bg-surface-soft text-[13px] text-ink outline-none transition-all focus:bg-surface-elevated focus:ring-2 focus:ring-line/30 border border-line/60"
                @keyup.enter="doSearch"
              />
              <button
                v-if="isSearching"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-[11px] text-ink-soft font-medium bg-surface-elevated rounded-full px-2.5 py-1 shadow-sm"
                @click="clearSearch"
              >
                清除
              </button>
            </div>
            <!-- 桌面端 tab -->
            <div class="flex gap-1">
              <button
                v-for="tab in tabs"
                :key="tab.key"
                :class="[
                  'relative shrink-0 px-4 py-1.5 text-[14px] font-bold transition-all duration-200 tap-scale',
                  activeTab === tab.key
                    ? 'text-ink'
                    : 'text-ink-muted hover:text-ink-soft'
                ]"
                @click="switchTab(tab.key)"
              >
                {{ tab.label }}
                <span
                  v-if="activeTab === tab.key"
                  class="absolute left-1/2 -translate-x-1/2 -bottom-1 w-5 h-1 bg-brand rounded-full"
                />
              </button>
            </div>
          </div>

          <!-- 移动端：Tab 占左边，搜索图标贴右边（单行置顶） -->
          <div class="lg:hidden flex items-center gap-2 flex-1 overflow-x-auto scrollbar-hide">
            <div class="flex gap-0.5">
              <button
                v-for="tab in tabs"
                :key="tab.key"
                :class="[
                  'relative shrink-0 px-4 py-1.5 text-[15px] font-bold transition-all duration-200 tap-scale',
                  activeTab === tab.key
                    ? 'text-ink'
                    : 'text-ink-muted hover:text-ink-soft'
                ]"
                @click="switchTab(tab.key)"
              >
                {{ tab.label }}
                <span
                  v-if="activeTab === tab.key"
                  class="absolute left-1/2 -translate-x-1/2 -bottom-1 w-5 h-1 bg-brand rounded-full"
                />
              </button>
            </div>
          </div>

          <!-- 右侧按钮 -->
          <div class="flex items-center gap-1 shrink-0">
            <!-- AI 助手入口 -->
            <router-link
              to="/ai-chat"
              class="inline-flex items-center gap-1 px-3 h-9 rounded-full bg-surface-soft text-[13px] font-medium text-brand tap-scale hover:bg-surface hover:shadow-sm transition-colors"
              aria-label="magic 助手"
            >
              <Icon icon="material-symbols:smart-toy-outline" class="w-4 h-4" />
              助手
            </router-link>

            <!-- 主题切换按钮 (全局可见) -->
            <button
              class="w-9 h-9 rounded-full bg-surface-soft flex items-center justify-center tap-scale hover:bg-surface hover:shadow-sm transition-colors"
              @click="toggleTheme"
              :aria-label="isDark ? '切换到浅色主题' : '切换到深色主题'"
            >
              <Icon v-if="isDark" icon="material-symbols:light-mode-outline" class="w-5 h-5 text-ink" />
              <Icon v-else icon="material-symbols:dark-mode-outline" class="w-5 h-5 text-ink" />
            </button>

            <!-- 移动端：搜索图标 -->
            <button
              class="lg:hidden w-9 h-9 rounded-full bg-surface-soft flex items-center justify-center tap-scale hover:bg-surface transition-colors"
              @click="openSearch"
              aria-label="搜索"
            >
              <Icon icon="material-symbols:search" class="w-5 h-5 text-ink" />
            </button>

            <!-- 桌面端：通知 / 发布 / 头像 -->
            <router-link v-if="isLoggedIn" to="/notifications" class="hidden lg:flex relative w-9 h-9 rounded-full bg-surface-soft items-center justify-center tap-scale hover:bg-surface transition-colors">
              <Icon icon="material-symbols:notifications-outline" class="w-5 h-5 text-ink" />
              <span v-if="notifCount > 0" class="absolute top-0 right-0 min-w-[16px] h-4 px-1 bg-[#EF4444] text-white text-[9px] font-bold rounded-full flex items-center justify-center">
                {{ notifCount > 99 ? '99+' : notifCount }}
              </span>
            </router-link>
            <router-link v-if="isLoggedIn" to="/create" class="hidden lg:flex items-center gap-1.5 px-3.5 h-9 rounded-full brand-gradient-btn text-[13px] font-semibold tap-scale hover:brightness-95 transition-colors">
              <Icon icon="material-symbols:add" class="w-4 h-4" />
              发布
            </router-link>
            <router-link v-if="isLoggedIn" to="/me" class="hidden lg:flex w-9 h-9 rounded-full overflow-hidden tap-scale ring-1 ring-line">
              <img v-if="userAvatar" :src="userAvatar" class="w-full h-full object-cover" alt="avatar" />
              <div v-else class="w-full h-full avatar-gradient-light flex items-center justify-center text-ink text-sm font-bold">
                {{ usernameInitial }}
              </div>
            </router-link>
            <router-link v-else to="/login" class="hidden lg:flex items-center gap-1.5 px-3.5 h-9 rounded-full brand-gradient-btn text-[13px] font-semibold tap-scale">
              登录
            </router-link>
          </div>
        </div>
      </div>
    </header>

    <!-- 移动端搜索弹窗 -->
    <Transition name="modal">
      <div v-if="searchModalOpen" class="fixed inset-0 z-50 bg-surface" @click.self="closeSearchModal">
        <!-- 搜索栏 -->
        <div class="sticky top-0 bg-surface-elevated border-b border-line">
          <div class="max-w-2xl mx-auto px-3 py-3 flex items-center gap-2">
            <button @click="closeSearchModal" class="w-9 h-9 rounded-full hover:bg-surface-soft flex items-center justify-center shrink-0">
              <Icon icon="material-symbols:arrow-back" class="w-5 h-5 text-ink" />
            </button>
            <div class="relative flex-1">
              <Icon icon="material-symbols:search" class="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-ink-muted pointer-events-none" />
              <input
                v-model="searchQuery"
                ref="searchInput"
                type="text"
                placeholder="搜索咖啡、地点、话题..."
                class="w-full min-h-[44px] pl-11 pr-4 rounded-full bg-surface-soft text-[14px] text-ink outline-none transition-all focus:bg-surface-elevated focus:ring-2 focus:ring-line/30 border border-line/60"
                @keyup.enter="doSearchFromModal"
                autofocus
              />
            </div>
            <button
              v-if="searchQuery"
              class="w-9 h-9 rounded-full hover:bg-surface-soft flex items-center justify-center shrink-0"
              @click="searchQuery = ''"
              aria-label="清除"
            >
              <Icon icon="material-symbols:close" class="w-5 h-5 text-ink-muted" />
            </button>
            <button
              class="text-[14px] font-semibold text-ink-soft px-2 shrink-0"
              @click="doSearchFromModal"
            >
              搜索
            </button>
          </div>
        </div>

        <!-- 搜索中/搜索结果 -->
        <div v-if="isSearching" class="max-w-2xl mx-auto px-3 pt-4">
          <div class="text-[13px] text-gray-500 mb-3">搜索「<strong class="text-gray-900">{{ searchQuery }}</strong>」的结果</div>
          <!-- 这里复用一套简化的瀑布流结果 -->
          <div v-if="loading && posts.length === 0" class="text-center text-gray-400 text-sm py-8">加载中...</div>
          <div v-else-if="posts.length === 0" class="text-center text-gray-400 text-sm py-12">没有找到相关内容</div>
          <div v-else class="masonry">
            <article
              v-for="post in posts"
              :key="post.id"
              class="bg-surface-elevated rounded-[18px] overflow-hidden mb-3 tap-scale"
              @click="openPost(post)"
            >
              <div v-if="getPostImage(post)" class="masonry-img-wrap">
                <img :src="getPostImage(post)" :alt="post.title" loading="lazy" @load="handleImgLoad($event, post)" />
              </div>
              <div class="px-3 py-2.5 space-y-1.5">
                <div class="text-[13px] font-semibold text-gray-900 leading-snug line-clamp-2">{{ post.title }}</div>
                <div class="text-[11.5px] text-gray-500 line-clamp-2">{{ stripTags(post.content) }}</div>
              </div>
            </article>
          </div>
        </div>

        <!-- 搜索热词 / 推荐 -->
        <div v-else class="max-w-2xl mx-auto px-4 pt-4">
          <h4 class="text-[13px] font-bold text-gray-900 mb-3">热门搜索</h4>
          <div class="flex flex-wrap gap-2 mb-6">
            <button
              v-for="tag in hotTags"
              :key="tag"
              class="px-3 py-1.5 rounded-full bg-gray-100 text-[12.5px] text-gray-700 font-medium tap-scale hover:bg-gray-200 transition-colors"
              @click="searchQuery = tag; doSearchFromModal()"
            >
              {{ tag }}
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- ================== 主体内容 ================== -->
    <div class="max-w-7xl mx-auto flex">
      <!-- 桌面端左侧导航 (Sidebar) -->
      <div class="hidden lg:block flex-1 max-w-[160px] shrink-0"></div>

      <!-- 中间主内容 -->
      <main class="flex-1 min-w-0 px-3 md:px-6 pt-20 pb-28 lg:pb-12 bg-surface">
        <!-- 搜索提示条 -->
        <div v-if="isSearching" class="mb-4 animate-fade-in">
          <div class="flex items-center gap-2 bg-surface-elevated shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.05)] text-ink-soft px-4 py-3 rounded-2xl text-sm border border-line">
            <Icon icon="material-symbols:search" class="w-4 h-4 text-ink-muted" />
            <span>搜索「<strong class="text-ink">{{ searchQuery }}</strong>」的结果</span>
            <button class="ml-auto text-ink font-medium text-xs hover:underline" @click="clearSearch">清除搜索</button>
          </div>
        </div>

        <!-- 骨架屏 -->
        <div v-if="loading && posts.length === 0" class="masonry mt-3">
          <div v-for="n in 6" :key="n" class="bg-surface-elevated rounded-[20px] overflow-hidden shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.05)] border border-line/40">
            <div class="skeleton aspect-square w-full" :style="{ animationDelay: `${n * 60}ms` }" />
            <div class="p-3 space-y-2.5">
              <div class="skeleton h-4 w-full rounded-lg" :style="{ animationDelay: `${n * 60 + 80}ms` }" />
              <div class="skeleton h-4 w-2/3 rounded-lg" :style="{ animationDelay: `${n * 60 + 120}ms` }" />
              <div class="flex items-center justify-between pt-2">
                <div class="flex items-center gap-1.5">
                  <div class="skeleton w-5 h-5 rounded-full" />
                  <div class="skeleton h-3 w-16 rounded" />
                </div>
                <div class="skeleton h-3 w-8 rounded" />
              </div>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else-if="!loading && posts.length === 0" class="mt-12 text-center">
          <div class="w-24 h-24 mx-auto mb-4 rounded-3xl brand-placeholder flex items-center justify-center shadow-inner">
            <WorldCoffeeLogoMini :size="48" :with-circle="false" />
          </div>
          <h3 class="text-[17px] font-bold text-ink mb-1">
            {{ isSearching ? '没有找到相关内容' : '暂无内容' }}
          </h3>
          <p class="text-[13px] text-ink-muted">
            {{ isSearching ? '换个关键词试试吧' : '来发布第一篇咖啡帖子' }}
          </p>
          <router-link v-if="isLoggedIn && !isSearching" to="/create" class="inline-flex mt-5 items-center gap-2 px-5 py-3 rounded-2xl brand-gradient-btn text-sm font-semibold shadow-[0_4px_14px_rgba(109,76,65,0.25)] tap-scale">
            <Icon icon="material-symbols:add" class="w-4 h-4" />
            发布第一篇
          </router-link>
        </div>

        <!-- 瀑布流帖子 -->
        <div v-else class="masonry mt-3">
          <article
            v-for="(post, i) in posts"
            :key="post.id"
            class="group bg-surface-elevated rounded-[20px] overflow-hidden shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.05)] hover:shadow-[0_4px_16px_rgba(62,39,35,0.08),0_8px_32px_rgba(62,39,35,0.08)] transition-all duration-300 cursor-pointer animate-fade-up border border-line/40"
            :style="{ animationDelay: `${(i % 8) * 40}ms` }"
            @click="router.push('/posts/' + post.id)"
          >
            <!-- 图片区（小红书风格：原比例 + 超长图限制） -->
            <div class="relative">
              <div v-if="getPostImage(post)" class="masonry-img-wrap">
                <img
                  :src="getPostImage(post)"
                  :alt="post.title"
                  loading="lazy"
                  @error="handleImgError($event, post)"
                  @load="handleImgLoad($event, post)"
                />
                <div v-if="imgErrors[post.id]" class="absolute inset-0 flex items-center justify-center brand-placeholder p-8">
                  <WorldCoffeeLogoMini :size="40" :with-circle="false" />
                </div>
              </div>
              <div v-else class="w-full aspect-square flex items-center justify-center brand-placeholder p-8">
                <WorldCoffeeLogoMini :size="48" :with-circle="false" />
              </div>

              <!-- 右上角：热度徽章 -->
              <span
                v-if="(post.likeCount || post.likes || 0) >= 20"
                class="absolute top-2.5 right-2.5 bg-black/40 backdrop-blur-md text-white text-[11px] font-semibold px-2.5 py-1 rounded-full flex items-center gap-1"
              >
                <Icon icon="material-symbols:local-fire-department" class="w-3 h-3" />
                {{ formatCount(post.likeCount || post.likes || 0) }}
              </span>
            </div>

            <!-- 内容区 -->
            <div class="p-3.5">
              <h3 class="text-[14px] font-bold text-ink leading-snug line-clamp-2 mb-2.5">
                {{ post.title || post.content || '分享一杯好咖啡' }}
              </h3>

              <!-- 咖啡/地点标签 -->
              <div v-if="post.coffeeName || post.location" class="flex flex-wrap gap-1.5 mb-3">
                <span v-if="post.coffeeName" class="inline-flex items-center gap-1 bg-surface-soft text-brand text-[10.5px] px-2 py-1 rounded-xl font-medium">
                  <Icon icon="material-symbols:coffee" class="w-3 h-3" />
                  {{ post.coffeeName.length > 10 ? post.coffeeName.slice(0, 10) + '…' : post.coffeeName }}
                </span>
                <span v-if="post.location" class="inline-flex items-center gap-1 bg-blue-50/80 text-blue-600 text-[10.5px] px-2 py-1 rounded-xl font-medium">
                  <Icon icon="material-symbols:location-on" class="w-3 h-3" />
                  {{ post.location.length > 8 ? post.location.slice(0, 8) + '…' : post.location }}
                </span>
              </div>

              <!-- 底部：作者 + 点赞 -->
              <div class="flex items-center justify-between text-[11.5px] pt-1.5 border-t border-line/60">
                <router-link
                  v-if="getAuthorId(post)"
                  :to="`/user/${getAuthorId(post)}`"
                  class="flex items-center gap-1.5 min-w-0 tap-scale hover:opacity-80"
                  @click.stop
                >
                  <img
                    v-if="getAuthorAvatar(post)"
                    :src="getAuthorAvatar(post)"
                    class="w-5 h-5 rounded-full object-cover border border-line flex-shrink-0"
                  />
                  <div v-else class="w-5 h-5 rounded-full avatar-gradient-light flex items-center justify-center text-ink text-[9px] font-bold flex-shrink-0">
                    {{ getAuthorName(post).charAt(0).toUpperCase() }}
                  </div>
                  <span class="truncate max-w-[100px] text-ink-muted font-medium">
                    {{ getAuthorName(post) }}
                  </span>
                </router-link>
                <div v-else class="flex items-center gap-1.5 min-w-0">
                  <img
                    v-if="getAuthorAvatar(post)"
                    :src="getAuthorAvatar(post)"
                    class="w-5 h-5 rounded-full object-cover border border-line flex-shrink-0"
                  />
                  <div v-else class="w-5 h-5 rounded-full avatar-gradient-light flex items-center justify-center text-ink text-[9px] font-bold flex-shrink-0">
                    {{ getAuthorName(post).charAt(0).toUpperCase() }}
                  </div>
                  <span class="truncate max-w-[100px] text-ink-muted font-medium">
                    {{ getAuthorName(post) }}
                  </span>
                </div>

                <div class="flex items-center gap-1 flex-shrink-0">
                  <Icon
                    :icon="post.likedByMe ? 'material-symbols:favorite' : 'material-symbols:favorite-outline'"
                    :class="['w-4 h-4 transition-colors', post.likedByMe ? 'text-rose' : 'text-ink-muted']"
                  />
                  <span :class="post.likedByMe ? 'text-rose font-semibold' : 'text-ink-muted'">
                    {{ formatCount(post.likeCount || post.likes || 0) }}
                  </span>
                </div>
              </div>
            </div>
          </article>
        </div>

        <!-- 加载更多 -->
        <div v-if="hasMore && posts.length > 0" class="flex justify-center py-8">
          <button
            class="inline-flex items-center gap-2 px-6 py-3 rounded-2xl bg-surface-elevated shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.05)] text-ink text-sm font-semibold tap-scale transition-all hover:shadow-[0_2px_12px_rgba(62,39,35,0.08)] disabled:opacity-50 border border-line/40"
            :disabled="loading"
            @click="loadMore"
          >
            <Icon v-if="loading" icon="material-symbols:refresh" class="w-4 h-4 animate-spin" />
            <Icon v-else icon="material-symbols:expand-more" class="w-4 h-4" />
            {{ loading ? '加载中...' : '加载更多' }}
          </button>
        </div>
      </main>

      <!-- 桌面端右侧信息栏 -->
      <aside class="hidden xl:block w-[280px] shrink-0 pt-4 pr-4">
        <div class="sticky top-[88px] space-y-4">
          <!-- 每日精选卡片 -->
          <div class="p-5 rounded-[20px] brand-gradient-btn-alt shadow-[0_2px_12px_rgba(62,39,35,0.18)] overflow-hidden relative">
            <div class="absolute -right-4 -bottom-4 opacity-20">
              <WorldCoffeeLogoMini :size="112" :with-circle="false" />
            </div>
            <div class="text-[11px] font-medium uppercase tracking-wider text-white/70 mb-1">Today</div>
            <h3 class="text-[18px] font-bold mb-1 leading-tight">每日咖啡灵感</h3>
            <p class="text-[12.5px] text-white/80 leading-relaxed mb-4">发现新的咖啡馆，记录你的咖啡时光</p>
            <router-link v-if="isLoggedIn" to="/create" class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-surface text-brand text-[12.5px] font-bold hover:bg-surface-elevated transition-colors tap-scale">
              <Icon icon="material-symbols:edit" class="w-4 h-4" />
              开始记录
            </router-link>
            <router-link v-else to="/login" class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-surface text-brand text-[12.5px] font-bold hover:bg-surface-elevated transition-colors tap-scale">
              立即登录
            </router-link>
          </div>

          <!-- 统计卡片 -->
          <div v-if="isLoggedIn" class="p-5 rounded-[20px] bg-surface-elevated shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.05)] border border-line/40">
            <h4 class="text-[13px] font-bold text-ink mb-4">我的咖啡</h4>
            <div class="grid grid-cols-3 gap-2 text-center">
              <div class="p-2.5 rounded-xl bg-surface-soft">
                <div class="text-[17px] font-bold text-brand">{{ quickStats?.posts || 0 }}</div>
                <div class="text-[10.5px] text-ink-muted mt-0.5">帖子</div>
              </div>
              <div class="p-2.5 rounded-xl bg-surface-soft">
                <div class="text-[17px] font-bold text-rose">{{ quickStats?.likes || 0 }}</div>
                <div class="text-[10.5px] text-ink-muted mt-0.5">获赞</div>
              </div>
              <div class="p-2.5 rounded-xl bg-surface-soft">
                <div class="text-[17px] font-bold text-amber">{{ quickStats?.favs || 0 }}</div>
                <div class="text-[10.5px] text-ink-muted mt-0.5">收藏</div>
              </div>
            </div>
          </div>

          <!-- 热门标签 -->
          <div class="p-5 rounded-[20px] bg-surface-elevated shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_8px_rgba(62,39,35,0.05)] border border-line/40">
            <h4 class="text-[13px] font-bold text-ink mb-4 flex items-center gap-1.5">
              <Icon icon="material-symbols:local-fire-department" class="w-4 h-4 text-amber" />
              热门话题
            </h4>
            <div class="flex flex-wrap gap-1.5">
              <span class="inline-flex items-center bg-surface-soft text-ink text-[11.5px] px-2.5 py-1.5 rounded-xl font-medium cursor-pointer hover:bg-surface-soft hover:text-brand transition-colors tap-scale">#手冲咖啡</span>
              <span class="inline-flex items-center bg-surface-soft text-ink text-[11.5px] px-2.5 py-1.5 rounded-xl font-medium cursor-pointer hover:bg-surface-soft hover:text-brand transition-colors tap-scale">#意式浓缩</span>
              <span class="inline-flex items-center bg-surface-soft text-ink text-[11.5px] px-2.5 py-1.5 rounded-xl font-medium cursor-pointer hover:bg-surface-soft hover:text-brand transition-colors tap-scale">#拿铁拉花</span>
              <span class="inline-flex items-center bg-surface-soft text-ink text-[11.5px] px-2.5 py-1.5 rounded-xl font-medium cursor-pointer hover:bg-surface-soft hover:text-brand transition-colors tap-scale">#咖啡店探店</span>
              <span class="inline-flex items-center bg-surface-soft text-ink text-[11.5px] px-2.5 py-1.5 rounded-xl font-medium cursor-pointer hover:bg-surface-soft hover:text-brand transition-colors tap-scale">#咖啡豆</span>
              <span class="inline-flex items-center bg-surface-soft text-ink text-[11.5px] px-2.5 py-1.5 rounded-xl font-medium cursor-pointer hover:bg-surface-soft hover:text-brand transition-colors tap-scale">#冷萃</span>
            </div>
          </div>

          <!-- 底部版权 -->
          <div class="text-center pt-2 pb-4">
            <p class="text-[11px] text-ink-muted">
              © {{ new Date().getFullYear() }} WorldCoffee · 用心品味每一杯
            </p>
          </div>
        </div>
      </aside>
    </div>

    <!-- ================== 帖子详情 Modal ================== -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="selectedPost" class="fixed inset-0 z-50 flex items-end md:items-center justify-center" @click.self="closePost">
          <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="closePost" />

          <div class="modal-content relative w-full md:max-w-2xl max-h-[92vh] bg-surface-elevated md:rounded-[28px] rounded-t-[28px] overflow-hidden flex flex-col shadow-[0_20px_60px_rgba(0,0,0,0.3)]">
            <!-- 顶部：作者栏 -->
            <div class="flex items-center justify-between px-5 py-4 border-b border-line/60 flex-shrink-0">
              <router-link :to="`/user/${getAuthorId(selectedPost)}`" class="flex items-center gap-3 tap-scale" @click="closePost">
                <img
                  v-if="getAuthorAvatar(selectedPost)"
                  :src="getAuthorAvatar(selectedPost)"
                  class="w-10 h-10 rounded-2xl object-cover shadow-[0_2px_8px_rgba(62,39,35,0.1)] border border-line"
                />
                <div v-else class="w-10 h-10 rounded-2xl avatar-gradient-light flex items-center justify-center text-ink text-sm font-bold shadow-[0_2px_8px_rgba(62,39,35,0.1)]">
                  {{ getAuthorName(selectedPost).charAt(0).toUpperCase() }}
                </div>
                <div>
                  <div class="text-[14px] font-bold text-ink">{{ getAuthorName(selectedPost) }}</div>
                  <div class="text-[11px] text-ink-muted">{{ formatTime(selectedPost.createTime || selectedPost.createdAt || selectedPost.createDate) }}</div>
                </div>
              </router-link>
              <button class="w-9 h-9 rounded-full bg-surface-soft hover:bg-surface-soft flex items-center justify-center transition-colors tap-scale" @click="closePost">
                <Icon icon="material-symbols:close" class="w-5 h-5 text-ink" />
              </button>
            </div>

            <!-- 可滚动内容 -->
            <div class="flex-1 overflow-y-auto">
                            <!-- 图片画廊 -->
              <div v-if="selectedPost.images && selectedPost.images.length" class="w-full bg-surface-soft/80 select-none relative" style="aspect-ratio: 16/9">
                <div
                  class="flex h-full w-full gallery-track"
                  style="user-select: none"
                  :style="{ transform: 'translateX(' + (-detailImageIndex * 100) + '%)', transition: 'transform 350ms cubic-bezier(0.25, 0.46, 0.45, 0.94)' }"
                  @touchstart="galleryTouchStart"
                  @touchend="galleryTouchEnd"
                  @mousedown="galleryMouseDown"
                  @mouseup="galleryMouseUp"
                  @mouseleave="galleryMouseUp"
                >
                  <div v-for="(img, i) in selectedPost.images" :key="i" class="shrink-0 h-full w-full flex items-center justify-center">
                    <img
                      :src="normalizeUrl(img)"
                      :alt="selectedPost.title + ' ' + (i + 1)"
                      class="w-full h-full object-cover"
                      draggable="false"
                      @error="handleImgError($event, selectedPost)"
                    />
                  </div>
                </div>
                <div v-if="selectedPost.images.length > 1" class="absolute bottom-3 right-3 bg-black/40 text-white text-xs px-2.5 py-1 rounded-full backdrop-blur">
                  {{ detailImageIndex + 1 }} / {{ selectedPost.images.length }}
                </div>
              </div><div class="p-5 md:p-6 space-y-5">
                <!-- 标题 & 内容 -->
                <h2 class="text-[20px] font-bold text-ink leading-snug">{{ selectedPost.title || selectedPost.content }}</h2>
                <p v-if="selectedPost.content" class="text-[14px] text-ink-soft leading-relaxed whitespace-pre-wrap">
                  {{ selectedPost.content }}
                </p>

                <!-- 元信息 -->
                <div class="flex flex-wrap gap-2 pt-1">
                  <span v-if="selectedPost.coffeeName" class="inline-flex items-center gap-1.5 bg-surface-soft text-ink text-[12px] px-3 py-1.5 rounded-xl font-medium">
                    <Icon icon="material-symbols:local-cafe" class="w-4 h-4" />
                    {{ selectedPost.coffeeName }}
                    <span v-if="selectedPost.coffeeBrand" class="text-ink-muted">· {{ selectedPost.coffeeBrand }}</span>
                  </span>
                  <span v-if="selectedPost.location" class="inline-flex items-center gap-1.5 bg-surface-soft text-ink text-[12px] px-3 py-1.5 rounded-xl font-medium">
                    <Icon icon="material-symbols:location-on" class="w-4 h-4" />
                    {{ selectedPost.location }}
                  </span>
                </div>

                <!-- 交互按钮 -->
                <div class="flex items-center gap-2 py-4 border-y border-line/60">
                  <button
                    class="flex items-center gap-2 px-4 py-2.5 rounded-2xl text-sm font-semibold transition-all tap-scale"
                    :class="detailLiked ? 'bg-rose-soft text-rose' : 'bg-surface-soft text-ink hover:bg-surface-soft'"
                    @click="toggleDetailLike"
                  >
                    <Icon :icon="detailLiked ? 'material-symbols:favorite' : 'material-symbols:favorite-outline'" class="w-[18px] h-[18px]" />
                    {{ detailLikeCount }}
                  </button>
                  <button
                    class="flex items-center gap-2 px-4 py-2.5 rounded-2xl text-sm font-semibold transition-all tap-scale"
                    :class="detailFav ? 'bg-amber-soft text-brand' : 'bg-surface-soft text-ink hover:bg-surface-soft'"
                    @click="toggleDetailFav"
                  >
                    <Icon :icon="detailFav ? 'material-symbols:bookmark' : 'material-symbols:bookmark-outline'" class="w-[18px] h-[18px]" />
                    收藏
                  </button>
                  <button class="flex items-center gap-2 px-4 py-2.5 rounded-2xl text-sm font-semibold bg-surface-soft text-ink hover:bg-surface-soft transition-all tap-scale ml-auto">
                    <Icon icon="material-symbols:share-outline" class="w-[18px] h-[18px]" />
                    <span class="hidden sm:inline">分享</span>
                  </button>
                </div>

                <!-- 评论区 -->
                <div>
                  <h4 class="text-[14px] font-bold text-ink mb-3 flex items-center gap-1.5">
                    <Icon icon="material-symbols:chat-bubble-outline" class="w-4 h-4" />
                    评论 ({{ detailComments.length }})
                  </h4>

                  <div v-if="detailCommentsLoading" class="flex items-center justify-center py-6">
                    <Icon icon="material-symbols:refresh" class="w-5 h-5 text-ink-muted animate-spin" />
                  </div>

                  <div v-else-if="detailComments.length === 0" class="py-8 text-center">
                    <Icon icon="material-symbols:chat-bubble-outline" class="w-10 h-10 text-ink-muted/50 mx-auto mb-2" />
                    <p class="text-[13px] text-ink-muted">暂无评论，来抢沙发吧~</p>
                  </div>

                  <div v-else class="space-y-3">
                    <div v-for="c in detailComments" :key="c.id || c.commentId" class="flex gap-2.5 p-3 rounded-2xl bg-surface-soft">
                      <img
                        v-if="c.user?.avatar || c.avatar || c.fromUserAvatar"
                        :src="c.user?.avatar || c.avatar || c.fromUserAvatar"
                        class="w-8 h-8 rounded-2xl object-cover flex-shrink-0 border border-line shadow-sm"
                      />
                      <div v-else class="w-8 h-8 rounded-2xl avatar-gradient-light flex items-center justify-center text-ink text-[11px] font-bold flex-shrink-0 shadow-sm">
                        {{ (c.user?.username || c.fromUser || c.username || 'U').charAt(0).toUpperCase() }}
                      </div>
                      <div class="flex-1 min-w-0">
                        <div class="flex items-baseline gap-2 mb-0.5">
                          <span class="text-[12.5px] font-bold text-ink">{{ c.user?.username || c.fromUser || c.username || '用户' }}</span>
                          <span class="text-[10.5px] text-ink-muted">{{ formatTime(c.createTime || c.createdAt || c.createDate) }}</span>
                        </div>
                        <p class="text-[13px] text-ink-soft leading-relaxed break-words">{{ c.content || c.comment || c.text }}</p>
                      </div>
                    </div>
                  </div>

                  <!-- 评论输入 -->
                  <div v-if="isLoggedIn" class="mt-4 pt-4 border-t border-line/60 flex gap-2.5 items-end">
                    <div class="flex-1 relative">
                      <textarea
                        v-model="commentText"
                        rows="2"
                        placeholder="写下你的评论..."
                        class="w-full px-4 py-3 rounded-2xl bg-surface-soft border border-transparent text-[13.5px] text-ink outline-none transition-all focus:bg-surface-elevated focus:border-line/60 focus:shadow-[0_0_0_4px_rgba(109,76,65,0.08)] resize-none"
                      />
                    </div>
                    <button
                      class="inline-flex items-center justify-center w-11 h-11 rounded-2xl brand-gradient-btn shadow-[0_4px_12px_rgba(109,76,65,0.25)] flex-shrink-0 tap-scale transition-all disabled:opacity-50 hover:shadow-[0_6px_16px_rgba(109,76,65,0.3)]"
                      :disabled="!commentText.trim() || commenting"
                      @click="submitComment"
                    >
                      <Icon v-if="commenting" icon="material-symbols:refresh" class="w-5 h-5 animate-spin" />
                      <Icon v-else icon="material-symbols:send" class="w-5 h-5" />
                    </button>
                  </div>
                  <div v-else class="mt-4 pt-4 text-center">
                    <router-link to="/login" class="text-[13px] text-brand font-semibold underline">登录后发表评论</router-link>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, inject, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { coffeeApi, normalizeUrl, extractApiError } from '../api'
import { useAuth } from '../composables/useAuth'
import { useTheme } from '../composables/useTheme'
import WorldCoffeeAiLogo from '../components/WorldCoffeeAiLogo.vue'
import WorldCoffeeLogoMini from '../components/WorldCoffeeLogoMini.vue'

const router = useRouter()
const toast = inject('toast', { show: () => {}, notifCount: ref(0) })
const { isLoggedIn, user, avatar: authAvatar } = useAuth()
const { isDark, toggleTheme } = useTheme()

// Logo 连点彩蛋：连点三次跳转关于我们
let logoClickCount = 0
let logoClickTimer = null
function handleLogoClick() {
  logoClickCount++
  if (logoClickCount === 1) {
    logoClickTimer = setTimeout(() => { logoClickCount = 0 }, 600)
  }
  if (logoClickCount >= 3) {
    clearTimeout(logoClickTimer)
    logoClickCount = 0
    router.push('/settings/about')
  }
}

onBeforeUnmount(() => {
  if (logoClickTimer) {
    clearTimeout(logoClickTimer)
    logoClickTimer = null
  }
})

const searchQuery = ref('')
const isSearching = ref(false)
const searchModalOpen = ref(false)
const searchInput = ref(null)

const hotTags = ['手冲咖啡', '拿铁', '云南咖啡', '冷萃', '意式浓缩', '咖啡馆探店', '挂耳', '冰美式', '生椰', '蓝山', '耶加雪菲', '曼特宁']
const activeTab = ref('recommend')
const posts = ref([])
const loading = ref(false)
const hasMore = ref(true)
const page = ref(1)
const pageSize = 12
const loadError = ref('')
const notifCount = computed(() => {
  const v = toast?.notifCount?.value
  return typeof v === 'number' ? v : 0
})

const imgErrors = ref({})
const selectedPost = ref(null)
const detailLiked = ref(false)
const detailLikeCount = ref(0)
const detailFav = ref(false)
const detailComments = ref([])
const detailCommentsLoading = ref(false)
const commentText = ref('')
const commenting = ref(false)

const username = computed(() => user.value?.username || user.value?.nickname || '')
const usernameInitial = computed(() => (username.value || 'W').charAt(0).toUpperCase())
const userAvatar = computed(() => normalizeUrl(user.value?.avatar || authAvatar.value || ''))

const quickStats = computed(() => ({
  posts: user.value?.postCount || user.value?.posts || 0,
  likes: user.value?.likeCount || user.value?.likedCount || 0,
  favs: user.value?.favoriteCount || user.value?.favorites || 0
}))

const tabs = [
  { key: 'recommend', label: '推荐' },
  { key: 'latest', label: '最新' },
  ...(isLoggedIn.value ? [{ key: 'following', label: '关注' }] : [])
]

// --- 工具函数 ---
function extractList(res) {
  if (!res || !res.data) return []
  if (Array.isArray(res.data)) return res.data
  if (Array.isArray(res.data.data)) return res.data.data
  if (Array.isArray(res.data.records)) return res.data.records
  if (Array.isArray(res.data.list)) return res.data.list
  return []
}

function formatCount(n) {
  if (!n) return 0
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return n
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return String(t).slice(0, 10)
  const diff = Date.now() - d.getTime()
  const sec = Math.floor(diff / 1000)
  if (sec < 60) return '刚刚'
  if (sec < 3600) return Math.floor(sec / 60) + '分钟前'
  if (sec < 86400) return Math.floor(sec / 3600) + '小时前'
  if (sec < 604800) return Math.floor(sec / 86400) + '天前'
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 帖子图片 URL 归一化（API 层的 withImageUrlList 已处理 posts/images 归一化）
function getPostImage(post) {
  if (!post) return ''
  if (Array.isArray(post.images) && post.images.length > 0) {
    const first = post.images[0]
    const raw = typeof first === 'string' ? first : (first?.url || first?.imageUrl || '')
    return normalizeUrl(raw)
  }
  if (typeof post.images === 'string' && post.images) {
    const t = post.images.trim()
    if (t.startsWith('[') || t.startsWith('{')) {
      try {
        const parsed = JSON.parse(t)
        if (Array.isArray(parsed) && parsed.length > 0) {
          const raw = typeof parsed[0] === 'string' ? parsed[0] : (parsed[0]?.url || '')
          return normalizeUrl(raw)
        }
      } catch {}
    }
    const split = t.split(',').map(s => s.trim()).filter(Boolean)
    return normalizeUrl(split[0] || '')
  }
  if (post.imageUrl) return normalizeUrl(post.imageUrl)
  if (post.coverImage) return normalizeUrl(post.coverImage)
  if (post.cover) return normalizeUrl(post.cover)
  return ''
}

function getAuthorAvatar(post) {
  if (!post) return ''
  const raw = post.avatar || post.authorAvatar || post.userAvatar
    || (post.user && post.user.avatar) || (post.author && post.author.avatar) || ''
  return normalizeUrl(raw)
}

function getAuthorName(post) {
  if (!post) return '用户'
  return post.username || post.authorName || post.nickname
    || (post.user && post.user.username) || (post.author && post.author.username) || '用户'
}

function getAuthorId(post) {
  if (!post) return null
  return post.userId || post.authorId
    || (post.user && post.user.id) || (post.author && post.author.id)
    || post.fromUserId || null
}

function handleImgError(e, post) {
  if (post?.id) imgErrors.value[post.id] = true
}

/**
 * 小红书风格：图片加载后读取自然尺寸，动态限制超长图高度
 * - 普通图（高度/宽度 ≤ 1.333，即 ≤ 3:4）：按原比例显示
 * - 超长图（高度/宽度 > 1.333）：限制为 3:4 固定比例，超出部分居中裁剪
 */
function handleImgLoad(e, post) {
  const img = e.target
  if (!img || !img.naturalWidth || !img.naturalHeight) return

  const ratio = img.naturalHeight / img.naturalWidth  // 高/宽比例

  // 超过 3:4（高度 > 宽度的 1.333 倍）就限制为固定高度
  if (ratio > 1.333) {
    // 给容器设置固定宽高比，让图片被裁剪
    const wrap = img.parentElement
    if (wrap && wrap.classList.contains('masonry-img-wrap')) {
      wrap.style.aspectRatio = '3 / 4'   // 固定 3:4
    }
    // 图片填充满容器，超出部分裁剪
    img.style.width = '100%'
    img.style.height = '100%'
    img.style.objectFit = 'cover'
  } else {
    // 普通图：按原比例显示
    img.style.height = 'auto'
    img.style.width = '100%'
  }
}

// --- 数据加载 ---
async function fetchPosts(reset = false) {
  if (loading.value) return
  if (reset) { page.value = 1; posts.value = []; hasMore.value = true; loadError.value = '' }

  loading.value = true
  try {
    const params = { page: page.value, size: pageSize }
    let res
    if (isSearching.value) {
      params.keyword = searchQuery.value
      res = await coffeeApi.search(params)
    } else if (activeTab.value === 'following' && isLoggedIn.value) {
      res = await coffeeApi.getFollowingPosts(params)
    } else if (activeTab.value === 'recommend') {
      res = await coffeeApi.getPosts(params)
    } else {
      res = await coffeeApi.getPosts(params)
    }

    if (res && res.code !== 200) {
      throw new Error(res.msg || '加载失败')
    }

    const list = extractList(res)
    if (list.length > 0) {
      // 按 id 去重，防止后端分页返回重叠数据
      const existingIds = new Set(posts.value.map(p => p.id))
      const newPosts = list.filter(p => !existingIds.has(p.id))
      posts.value = [...posts.value, ...newPosts]
      hasMore.value = list.length >= pageSize

      // 推荐 tab 首页加载后客户端随机排列，保证分页一致性
      if (activeTab.value === 'recommend' && page.value === 1) {
        for (let i = posts.value.length - 1; i > 0; i--) {
          const j = Math.floor(Math.random() * (i + 1));
          [posts.value[i], posts.value[j]] = [posts.value[j], posts.value[i]]
        }
      }
    } else {
      hasMore.value = false
    }
  } catch (err) {
    hasMore.value = false
    loadError.value = extractApiError(err)
    if (isSearching.value || page.value === 1) {
      toast.show(loadError.value || '加载失败', 'error')
    }
  } finally {
    loading.value = false
  }
}

function loadMore() {
  if (!hasMore.value || loading.value) return
  page.value++
  fetchPosts()
}

function switchTab(key) {
  if (activeTab.value === key) return
  activeTab.value = key
  fetchPosts(true)
}

function doSearch() {
  if (!searchQuery.value.trim()) return
  isSearching.value = true
  fetchPosts(true)
}

function clearSearch() {
  searchQuery.value = ''
  isSearching.value = false
  fetchPosts(true)
}

function openSearch() {
  searchModalOpen.value = true
  // 打开后聚焦输入框
  setTimeout(() => {
    if (searchInput.value && searchInput.value) searchInput.value.focus()
  }, 50)
}
function closeSearchModal() {
  searchModalOpen.value = false
}
function doSearchFromModal() {
  if (!searchQuery.value.trim()) return
  isSearching.value = true
  searchModalOpen.value = false
  fetchPosts(true)
}
function stripTags(str) {
  if (!str) return ''
  return String(str).replace(/<[^>]*>/g, '').replace(/\s+/g, ' ').trim()
}

// --- 帖子详情 ---
async function openPost(post) {
  detailImageIndex.value = 0
  selectedPost.value = post
  detailLiked.value = !!post.likedByMe
  detailLikeCount.value = post.likeCount || post.likes || 0
  detailFav.value = !!post.favoritedByMe
  detailComments.value = []
  commentText.value = ''
  try {
    const detailRes = await coffeeApi.getPostDetail(post.id)
    if (detailRes && detailRes.code === 200 && detailRes.data) {
      const d = detailRes.data
      detailLiked.value = !!d.likedByMe
      detailLikeCount.value = d.likeCount || d.likes || detailLikeCount.value
      detailFav.value = !!d.favoritedByMe
      if (Array.isArray(d.comments) && d.comments.length > 0) {
        detailComments.value = d.comments
      } else {
        loadComments()
      }
    } else {
      loadComments()
    }
  } catch {
    loadComments()
  }
}


// ===== Gallery (详情弹窗左右滑动) =====
const detailImageIndex = ref(0)

let _gx = 0
function galleryTouchStart(e) {
  const t = e.changedTouches && e.changedTouches[0]
  if (t) _gx = t.clientX
}
function galleryTouchEnd(e) {
  const t = e.changedTouches && e.changedTouches[0]
  if (!t) return
  const dx = t.clientX - _gx
  if (Math.abs(dx) >= 30) gallerySwipe(dx)
}

function galleryMouseDown(e) { _gx = e.clientX }
function galleryMouseUp(e) {
  const dx = e.clientX - _gx
  if (Math.abs(dx) > 5 && Math.abs(dx) >= 30) gallerySwipe(dx)
}

function gallerySwipe(dx) {
  const total = selectedPost.value?.images?.length || 0
  if (dx < 0 && detailImageIndex.value < total - 1) detailImageIndex.value++
  else if (dx > 0 && detailImageIndex.value > 0) detailImageIndex.value--
}
function closePost() {
  selectedPost.value = null
}

async function loadComments() {
  if (!selectedPost.value) return
  detailCommentsLoading.value = true
  try {
    const res = await coffeeApi.getComments(selectedPost.value.id, { page: 1, size: 50 })
    detailComments.value = extractList(res)
  } catch {
    // 静默
  } finally {
    detailCommentsLoading.value = false
  }
}

async function toggleDetailLike() {
  if (!isLoggedIn.value) { router.push('/login'); return }
  if (!selectedPost.value) return
  try {
    const res = await coffeeApi.toggleLike(selectedPost.value.id)
    if (res && res.code === 200) {
      const d = res?.data
      const nowLiked = typeof d === 'object'
        ? !!(d?.likedByMe ?? d?.liked ?? d?.result ?? d?.isLiked)
        : !detailLiked.value
      detailLiked.value = nowLiked
      detailLikeCount.value = Math.max(0, detailLikeCount.value + (nowLiked ? 1 : -1))
    }
  } catch (e) {
    toast.show(extractApiError(e) || '操作失败', 'error')
  }
}

async function toggleDetailFav() {
  if (!isLoggedIn.value) { router.push('/login'); return }
  if (!selectedPost.value) return
  try {
    const res = await coffeeApi.toggleFavorite(selectedPost.value.id)
    if (res && res.code === 200) {
      const d = res?.data
      detailFav.value = typeof d === 'object'
        ? !!(d?.favoritedByMe ?? d?.favorited ?? d?.result)
        : !detailFav.value
      toast.show(detailFav.value ? '已收藏' : '已取消收藏', 'success')
    }
  } catch (e) {
    toast.show(extractApiError(e) || '操作失败', 'error')
  }
}

async function submitComment() {
  if (!isLoggedIn.value || !selectedPost.value) return
  const content = commentText.value.trim()
  if (!content) return
  commenting.value = true
  try {
    const res = await coffeeApi.addComment(selectedPost.value.id, { content })
    if (res && res.code === 200) {
      commentText.value = ''
      loadComments()
    } else {
      toast.show(res?.msg || '评论失败', 'error')
    }
  } catch (e) {
    toast.show(extractApiError(e) || '评论失败', 'error')
  } finally {
    commenting.value = false
  }
}

// --- 生命周期 ---
onMounted(async () => {
  fetchPosts(true)
})
</script>

<style scoped>
.scrollbar-hide {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.scrollbar-hide::-webkit-scrollbar {
  display: none;
}

.gallery-track { touch-action: none; -ms-touch-action: none; }
.gallery-track img { touch-action: none; }
</style>
