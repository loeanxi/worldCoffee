<template>
  <div class="wc-home-shell min-h-screen bg-surface">
    <!-- ================== 顶部栏（WorldCoffee：左 LOGO / 中搜索 / 右按钮） ================== -->
    <header class="wc-home-header fixed top-0 left-0 right-0 z-40">
      <div class="flex items-center px-4 lg:px-6 py-3 max-w-[1700px] mx-auto">
        <!-- LOGO（左）：咖啡杯标识 + 品牌文字 -->
        <router-link to="/" class="wc-home-brand shrink-0 select-none flex items-center tap-scale">
          <WorldCoffeeLogoMini class="wc-home-brand-icon hidden sm:block" :size="32" :with-circle="true" />
          <span class="flex flex-col leading-none">
            <span class="wc-home-brand-title">WorldCoffee</span>
            <span class="wc-home-brand-subtitle hidden lg:inline">coffee notes & city cafés</span>
          </span>
        </router-link>

        <!-- 搜索框（居中，咖啡社区搜索） -->
        <div class="flex items-center justify-center flex-1 ml-6">
          <div class="wc-home-search relative w-full max-w-[560px]">
            <Icon icon="material-symbols:search" class="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-ink-muted pointer-events-none"/>
            <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索咖啡、地点、话题..."
              class="w-full h-10 pl-10 pr-4 rounded-full text-[13px] text-ink outline-none"
              @keyup.enter="doSearch"
            />
            <button
              v-if="isSearching && !activeTopic"
              class="absolute right-3 top-1/2 -translate-y-1/2 text-[11px] text-ink-muted font-medium bg-surface rounded-full px-2.5 py-0.5 shadow-sm border border-line-soft"
              @click="clearSearch"
            >清除</button>
          </div>
        </div>

        <div class="hidden flex-1" />

        <!-- 右侧按钮 -->
        <div class="flex items-center gap-2 shrink-0 ml-2">
          <!-- 主题切换（月亮）-->
          <button class="wc-home-icon-btn tap-scale" @click="toggleTheme" :aria-label="isDark ? '切换到浅色主题' : '切换到深色主题'">
            <Icon v-if="isDark" icon="material-symbols:light-mode-outline" class="w-4 h-4 text-ink-soft" />
            <Icon v-else icon="material-symbols:dark-mode-outline" class="w-4 h-4 text-ink-soft" />
          </button>

          <!-- 桌面端：通知 -->
          <router-link v-if="isLoggedIn" to="/notifications" class="wc-home-icon-btn inline-flex relative tap-scale">
            <Icon icon="material-symbols:notifications-outline" class="w-4 h-4 text-ink-soft" />
            <span v-if="notifCount > 0" class="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 bg-rose text-white text-[10px] font-bold rounded-full flex items-center justify-center border-2 border-white">
              {{ notifCount > 99 ? '99+' : notifCount }}
            </span>
          </router-link>

          <!-- 桌面端：发布 -->
          <button v-if="isLoggedIn" type="button" class="wc-home-primary-action inline-flex items-center gap-1.5 tap-scale" @click="openComposer">
            <Icon icon="material-symbols:add" class="w-4 h-4" />
            发布
          </button>

          <!-- 桌面端：未登录 → 登录按钮 -->
          <router-link v-else to="/login" class="wc-home-primary-action inline-flex items-center gap-1.5 tap-scale">
            登录
          </router-link>

          <!-- 桌面端：头像（小圆形） -->
          <router-link v-if="isLoggedIn" to="/me" class="inline-flex w-9 h-9 rounded-full overflow-hidden tap-scale ring-1 ring-gray-200">
            <img v-if="userAvatar" :src="userAvatar" class="w-full h-full object-cover" alt="avatar" />
            <div v-else class="w-full h-full bg-surface-soft flex items-center justify-center text-ink-soft text-[11px] font-bold">{{ usernameInitial }}</div>
          </router-link>

          <!-- 移动端：搜索 -->
          <button class="wc-home-mobile-btn hidden tap-scale" @click="openSearch" aria-label="搜索">
            <Icon icon="material-symbols:search" class="w-5 h-5 text-ink" />
          </button>
        </div>
      </div>

    </header>

    <!-- ================== 主体内容（咖啡社区信息流：三栏布局 — 左导航 / 中间瀑布流 / 右发现栏） ================== -->
    <div class="wc-home-main max-w-[1480px] mx-auto px-4 xl:px-6 pt-[120px] lg:pt-[88px] pb-28 lg:pb-10">

      <!-- ========== 移动端：帖子列表 ========== -->
      <div class="hidden">
        <!-- 瀑布流帖子 -->
        <section v-if="!loading || posts.length" class="masonry">
          <article
            v-for="post in posts"
            :key="post.id"
            class="feed-post-card wc-feed-card group relative overflow-hidden transition-all cursor-pointer animate-fade-in"
            :data-post-id="post.id"
            @click="openPost(post)"
          >
            <button
              v-if="activeTab === 'recommend' && !isSearching"
              class="wc-not-interested absolute top-2 right-2 z-10 tap-scale"
              title="Not interested"
              aria-label="Not interested"
              @click.stop="markNotInterested(post)"
            >
              <Icon icon="material-symbols:close-small" class="w-5 h-5" />
            </button>
            <div v-if="getPostImage(post)" class="wc-feed-image-wrap relative overflow-hidden">
              <img :src="getPostImage(post)" loading="lazy" class="wc-feed-cover w-full h-auto block" :alt="post.title || post.content" />
              <span
                v-if="(post.like_count || post.likeCount || 0) >= 10"
                class="wc-hot-badge absolute top-2 left-2 flex items-center gap-1"
              >
                <Icon icon="material-symbols:local-fire-department" class="w-3 h-3" /> 热帖
              </span>
            </div>
            <div v-else class="brand-placeholder">
              <WorldCoffeeLogoMini :size="48" :with-circle="false" />
            </div>
            <h3 class="wc-feed-title text-[12.5px] text-ink leading-snug line-clamp-2 mt-2 mb-1.5 font-medium px-0.5">{{ post.title || post.content }}</h3>
            <div v-if="getTopics(post).length" class="flex flex-wrap gap-1 px-0.5 mb-1.5">
              <button
                v-for="topic in getTopics(post)"
                :key="topic"
                class="wc-topic-chip tap-scale"
                @click.stop="searchTopic(topic)"
              >
                #{{ topic }}
              </button>
            </div>
            <div class="wc-feed-meta flex items-center justify-between px-0.5">
              <div class="flex items-center gap-1.5 min-w-0">
                <img v-if="post.author?.avatar" :src="post.author.avatar" class="w-4 h-4 rounded-full object-cover shrink-0" :alt="post.author.nickname" />
                <span class="text-[11px] text-ink-muted truncate">{{ post.author?.nickname || '咖啡爱好者' }}</span>
              </div>
              <div class="flex items-center gap-1 shrink-0 text-ink-muted">
                <Icon icon="material-symbols:favorite-border" class="w-3.5 h-3.5" />
                <span class="text-[11px]">{{ formatCount(post.like_count || post.likeCount || 0) }}</span>
              </div>
            </div>
          </article>
        </section>

        <!-- 加载中 -->
        <section v-else-if="loading" class="py-16 flex flex-col items-center text-ink-muted text-sm">
          <Icon icon="material-symbols:progress-activity" class="animate-spin w-8 h-8 mb-3" />
          <span>正在加载...</span>
        </section>

        <!-- 无内容 -->
        <section v-else class="py-16 flex flex-col items-center text-center">
          <div class="w-16 h-16 mb-4 rounded-full bg-gray-50 flex items-center justify-center">
            <Icon icon="material-symbols:inbox-outline" class="w-8 h-8 text-gray-300" />
          </div>
          <p class="text-ink-muted text-sm mb-4">还没有内容，成为第一个分享者吧～</p>
          <button v-if="isLoggedIn" type="button" class="wc-home-primary-action inline-flex items-center" @click="openComposer">发布笔记</button>
          <router-link v-else to="/login" class="wc-home-primary-action inline-flex items-center">登录查看更多</router-link>
        </section>

        <!-- 加载更多 -->
        <div v-if="posts.length && hasMore" class="flex justify-center pt-6">
          <button
            class="px-6 h-9 rounded-full bg-surface-soft text-[12.5px] text-ink-soft font-medium tap-scale hover:bg-surface-soft transition-colors"
            @click="loadMore"
            :disabled="loading"
          >
            <span v-if="!loading">加载更多</span>
            <span v-else class="flex items-center gap-1.5"><Icon icon="material-symbols:progress-activity" class="animate-spin w-3.5 h-3.5" />加载中...</span>
          </button>
        </div>
      </div>

      <!-- ========== 桌面端：左导航 + 中间瀑布流 ========== -->
      <div class="wc-home-desktop-layout grid items-start">
        <!-- 左栏：导航（固定宽度 + 粘性定位跟随滚动） -->
        <nav class="wc-side-nav shrink-0 sticky top-[84px]">
          <!-- 品牌标识（对齐原型：侧栏顶部 Logo） -->
          <router-link to="/" class="wc-side-brand tap-scale">
            <WorldCoffeeLogoMini :size="30" variant="icon" />
            <span class="wc-side-brand-text">WorldCoffee</span>
          </router-link>
          <div class="my-3 h-px bg-line-soft" />

          <div class="wc-side-section flex flex-col gap-1">
            <button
              v-for="item in leftNavItems"
              :key="item.label"
              :class="[
                'wc-side-nav-item group flex items-center gap-2.5 tap-scale text-left',
                activeTab === item.key
                  ? 'is-active text-ink font-semibold'
                  : 'text-ink-soft font-medium'
              ]"
              @click="item.action ? item.action() : (item.key && switchTab(item.key))"
            >
              <Icon :icon="item.icon" :class="['w-4 h-4 shrink-0', activeTab === item.key ? 'text-brand' : 'text-ink-muted group-hover:text-ink']" />
              <span class="text-[12.5px] truncate flex-1">{{ item.label }}</span>
              <span v-if="item.badge" class="wc-side-badge">{{ item.badge > 99 ? '99+' : item.badge }}</span>
            </button>
          </div>

          <!-- 分隔线 -->
          <div class="my-3 h-px bg-line-soft" />

          <!-- 登录提示 / 用户卡 -->
          <div class="wc-side-section flex flex-col gap-2">
            <router-link v-if="!isLoggedIn" to="/login" class="wc-side-login flex items-center justify-center tap-scale">
              登录
            </router-link>
            <router-link v-else to="/me" class="wc-side-user flex items-center gap-2 tap-scale">
              <div class="shrink-0">
                <img v-if="userAvatar" :src="userAvatar" class="w-7 h-7 rounded-full object-cover" alt="avatar" />
                <div v-else class="w-7 h-7 rounded-full bg-surface-soft flex items-center justify-center text-ink-soft text-[9px] font-bold">{{ usernameInitial }}</div>
              </div>
              <div class="min-w-0 flex-1">
                <div class="text-[11.5px] font-semibold text-ink truncate">{{ username }}</div>
                <div class="text-[10px] text-ink-muted">我的咖啡主页</div>
              </div>
            </router-link>
          </div>

          <!-- 优化：咖啡足迹 + 每日签到（填充整屏侧栏垂直空间） -->
          <div v-if="isLoggedIn" class="wc-side-stats">
            <div class="t">
              <Icon icon="material-symbols:local-cafe" class="w-3.5 h-3.5" />
              我的咖啡足迹
            </div>
            <div class="row" v-if="!footprintEmpty">
              <div class="cell"><div class="num">{{ footprint.notes }}</div><div class="lab">笔记</div></div>
              <div class="cell"><div class="num">{{ footprint.likes }}</div><div class="lab">点赞</div></div>
              <div class="cell"><div class="num">{{ footprint.favs }}</div><div class="lab">收藏</div></div>
            </div>
            <div v-else class="text-[10.5px] text-ink-muted text-center py-1">发布 / 点赞 / 收藏，点亮你的第一杯足迹</div>
          </div>
          <div v-if="isLoggedIn" class="wc-checkin-card">
            <span class="cup">☕</span>
            <span class="txt">
              <b>每日签到</b>
              <span>{{ checkinText }}</span>
            </span>
            <button type="button" class="wc-checkin-btn" :class="{ 'is-done': checkedIn }" @click="doCheckin">
              {{ checkedIn ? '已签' : '签到' }}
            </button>
          </div>

          <!-- 底部：更多 / 关于我们 -->
          <div class="wc-side-footer mt-auto pt-6 flex flex-col text-[10.5px] text-ink-muted">
            <button class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md transition-colors text-left">
              <Icon icon="material-symbols:more-horiz" class="w-3.5 h-3.5" />
              <span>更多</span>
            </button>
            <button class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md transition-colors text-left">
              <Icon icon="material-symbols:info-outline" class="w-3.5 h-3.5" />
              <span>关于我们</span>
            </button>
          </div>
        </nav>

        <!-- 中间：主体内容 -->
        <div class="wc-feed-main min-w-0">
          <!-- 桌面端：顶部 Tab 栏（横排文字标签 + 下划线选中态） -->
          <div class="wc-channel-bar flex items-center justify-start gap-1 mb-5 overflow-x-auto scrollbar-hide">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              :class="[
                'wc-channel-btn relative shrink-0 tap-scale transition-colors whitespace-nowrap',
                activeTab === tab.key && !isSearching && !activeTopic
                  ? 'is-active text-ink font-semibold'
                  : 'text-ink-muted hover:text-ink font-medium'
              ]"
              @click="switchTab(tab.key)"
            >
              {{ tab.label }}
              <span
                v-if="activeTab === tab.key && !isSearching && !activeTopic"
                class="wc-channel-active-line absolute left-1/2 -translate-x-1/2 bottom-0"
              />
            </button>
            <button
              v-for="topic in topicTabs"
              :key="topic"
              :class="[
                'wc-channel-btn relative shrink-0 tap-scale transition-colors whitespace-nowrap',
                activeTopic === topic
                  ? 'is-active text-ink font-semibold'
                  : 'text-ink-muted hover:text-ink font-medium'
              ]"
              @click="searchTopic(topic)"
            >
              {{ topic }}
              <span
                v-if="activeTopic === topic"
                class="wc-channel-active-line absolute left-1/2 -translate-x-1/2 bottom-0"
              />
            </button>
          </div>

          <!-- 帖子列表：瀑布流 -->
          <section v-if="!loading || posts.length" class="masonry">
            <PostCard
              v-for="post in posts"
              :key="post.id"
              :post="post"
              @click="openPost(post)"
              @like="quickLike(post)"
              @favorite="quickFav(post)"
              @share="sharePost(post)"
            />
          </section>

          <!-- 加载中 -->
          <section v-else-if="loading" class="py-16 flex flex-col items-center text-ink-muted text-sm">
            <Icon icon="material-symbols:progress-activity" class="animate-spin w-8 h-8 mb-3" />
            <span>正在为你加载好内容...</span>
          </section>

          <!-- 无内容 -->
          <section v-else class="py-16 flex flex-col items-center text-center">
            <div class="w-16 h-16 mb-4 rounded-full bg-gray-50 flex items-center justify-center">
              <Icon icon="material-symbols:inbox-outline" class="w-8 h-8 text-gray-300" />
            </div>
            <p class="text-ink-muted text-sm mb-4">还没有内容，成为第一个分享者吧～</p>
            <button
              v-if="isLoggedIn"
              type="button"
                class="wc-home-primary-action inline-flex items-center gap-1.5 tap-scale"
              @click="openComposer"
            >
              <Icon icon="material-symbols:add" class="w-4 h-4" />
              发布笔记
            </button>
            <router-link v-else to="/login" class="wc-home-primary-action inline-flex items-center gap-1.5 tap-scale">
              登录查看更多
            </router-link>
          </section>

          <!-- 加载更多 -->
          <div
            v-if="posts.length && hasMore"
            ref="loadMoreSentinel"
            class="wc-load-more-sentinel flex justify-center pt-6 text-[12.5px] text-ink-muted"
            aria-live="polite"
          >
            <span v-if="loading" class="flex items-center gap-1.5">
              <Icon icon="material-symbols:progress-activity" class="animate-spin w-3.5 h-3.5" />
              加载中...
            </span>
            <span v-else>继续下滑加载更多</span>
          </div>

          <!-- 优化：完读底帽，给信息流一个收尾 -->
          <div v-if="posts.length && !hasMore && !loading" class="wc-feed-end">
            <span class="cup">☕</span>
            <span>你已追上所有咖啡朋友的脚步</span>
            <button type="button" class="wc-feed-end-btn" @click="scrollToTop">
              回到顶部
              <Icon icon="material-symbols:arrow-upward" class="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        <!-- ========== 右栏：发现（热门话题 / 推荐创作者 / 品牌卡） ========== -->
        <aside class="wc-right-rail flex">
          <!-- 热门话题 -->
          <section class="wc-rail-card">
            <div class="wc-rail-title mb-2">
              <Icon icon="material-symbols:local-fire-department" class="w-4 h-4 text-amber" />
              热门话题
            </div>
            <div class="flex flex-col gap-0.5">
              <button
                v-for="(topic, i) in trendingTopics"
                :key="topic"
                class="wc-rail-topic tap-scale"
                @click="searchTopic(topic)"
              >
                <span :class="['wc-rail-rank', i < 3 && 'is-top']">{{ i + 1 }}</span>
                <span class="text-[12.5px] font-medium text-ink-soft truncate">#{{ topic }}</span>
              </button>
            </div>
          </section>

          <!-- 推荐创作者 -->
          <section v-if="recommendedCreators.length" class="wc-rail-card">
            <div class="wc-rail-title mb-2">
              <Icon icon="material-symbols:group-add-outline" class="w-4 h-4 text-brand-green" />
              推荐创作者
            </div>
            <div class="flex flex-col gap-2">
              <div v-for="c in recommendedCreators" :key="c.id" class="flex items-center gap-2">
                <router-link :to="`/user/${c.id}`" class="flex items-center gap-2 min-w-0 flex-1 tap-scale">
                  <img v-if="c.avatar" :src="c.avatar" class="w-8 h-8 rounded-full object-cover shrink-0" :alt="c.name" />
                  <div v-else class="w-8 h-8 rounded-full avatar-gradient-honey flex items-center justify-center text-white text-[11px] font-bold shrink-0">{{ c.name.charAt(0).toUpperCase() }}</div>
                  <span class="text-[12px] font-medium text-ink truncate">{{ c.name }}</span>
                </router-link>
                <button class="wc-rail-follow-btn tap-scale">关注</button>
              </div>
            </div>
          </section>

          <!-- 近期活动 -->
          <section class="wc-rail-card">
            <div class="wc-rail-title mb-2">
              <Icon icon="material-symbols:event-outline" class="w-4 h-4 text-brand-green" />
              近期活动
            </div>
            <div class="flex flex-col gap-2">
              <button v-for="ev in upcomingEvents" :key="ev.title" class="wc-rail-highlight tap-scale">
                <span class="wc-rail-dot" />
                <div class="min-w-0 flex-1 text-left">
                  <div class="text-[12px] font-semibold text-ink truncate">{{ ev.title }}</div>
                  <div class="text-[10.5px] text-ink-muted">{{ ev.date }}</div>
                </div>
              </button>
            </div>
          </section>

          <!-- 品牌卡 -->
          <section class="wc-rail-card">
            <div class="flex items-center gap-2.5">
              <WorldCoffeeLogoMini :size="34" variant="icon" />
              <div class="min-w-0">
                <div class="text-[12px] font-bold text-ink leading-tight">loean worldcoffee</div>
                <div class="text-[10px] text-ink-muted">记录每一杯好咖啡</div>
              </div>
            </div>
          </section>

          <!-- 优化：每日冷知识渐变卡 -->
          <section class="wc-tip-card">
            <div class="t">COFFEE FACT · 每日冷知识</div>
            <div class="q" :key="tipIndex">{{ coffeeTips[tipIndex] }}</div>
            <button type="button" class="more" @click="nextTip">换一条 →</button>
          </section>

          <!-- 优化：品牌微链接收尾 -->
          <div class="wc-rail-links">
            <router-link to="/settings/about">关于我们</router-link>
            <a href="#" @click.prevent>条款</a>
            <a href="#" @click.prevent>隐私</a>
            <span>© 2026 WorldCoffee</span>
          </div>
        </aside>

      </div>
    </div>

    <!-- 优化：回到顶部悬浮按钮 -->
    <button type="button" class="wc-fab-top" :class="{ show: showFab }" aria-label="回到顶部" @click="scrollToTop">
      <Icon icon="material-symbols:arrow-upward" class="w-5 h-5" />
    </button>

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
          <div class="text-[13px] text-ink-muted mb-3">搜索「<strong class="text-ink">{{ searchQuery }}</strong>」的结果</div>
          <!-- 这里复用一套简化的瀑布流结果 -->
          <div v-if="loading && posts.length === 0" class="text-center text-ink-muted text-sm py-8">加载中...</div>
          <div v-else-if="posts.length === 0" class="text-center text-ink-muted text-sm py-12">没有找到相关内容</div>
          <div v-else class="masonry">
            <PostCard
              v-for="post in posts"
              :key="post.id"
              :post="post"
              @click="openPost(post)"
            />
          </div>
        </div>

        <!-- 搜索热词 / 推荐 -->
        <div v-else class="max-w-2xl mx-auto px-4 pt-4">
          <h4 class="text-[13px] font-bold text-ink mb-3">热门搜索</h4>
          <div class="flex flex-wrap gap-2 mb-6">
            <button
              v-for="tag in hotTags"
              :key="tag"
              class="px-3 py-1.5 rounded-full bg-surface-soft text-[12.5px] text-ink-soft font-medium tap-scale hover:bg-surface-soft transition-colors"
              @click="searchQuery = tag; doSearchFromModal()"
            >
              {{ tag }}
            </button>
          </div>
        </div>
      </div>
    </Transition>

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
                      :alt="String(selectedPost.title || '') + ' ' + (i + 1)"
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
                  <button
                    v-for="topic in getTopics(selectedPost)"
                    :key="topic"
                    class="wc-topic-chip inline-flex items-center gap-1 text-[12px] px-3 py-1.5 rounded-xl font-medium tap-scale"
                    @click="searchTopic(topic); closePost()"
                  >
                    #{{ topic }}
                  </button>
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

    <!-- 同页发布弹窗：模仿小红书 Web，不让用户感知大跳转 -->
    <Teleport to="body">
      <Transition name="modal">
        <div
          v-if="composerOpen"
          class="wc-composer-overlay fixed inset-0 z-[70] flex items-start justify-center px-3 py-5 sm:px-5 sm:py-8 lg:py-10"
          @click.self="closeComposer"
        >
          <div class="absolute inset-0 bg-black/45 backdrop-blur-[3px]" />
          <section class="wc-composer-modal relative w-full max-w-[760px] max-h-[90vh] overflow-hidden rounded-[26px] bg-surface-elevated border border-line/70 shadow-[0_28px_90px_rgba(33,28,24,.30)]">
            <div class="sticky top-0 z-10 flex items-center justify-between gap-4 px-5 py-4 bg-surface-elevated/92 border-b border-line/60 backdrop-blur-xl">
              <div class="min-w-0">
                <p class="text-[10px] font-black tracking-[0.18em] text-brand uppercase">WorldCoffee Note</p>
                <h2 class="text-[17px] font-black text-ink leading-tight">发布咖啡笔记</h2>
              </div>
              <button
                type="button"
                class="wc-composer-close tap-scale"
                aria-label="关闭发布面板"
                @click="closeComposer"
              >
                <Icon icon="material-symbols:close" class="w-5 h-5" />
              </button>
            </div>
            <div class="wc-composer-body max-h-[calc(90vh-74px)] overflow-y-auto px-5 py-5">
              <CreatePost embedded @success="handleComposerSuccess" />
            </div>
          </section>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, onUnmounted, inject, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { coffeeApi, normalizeUrl, extractApiError } from '@wc/shared'
import { useAuth } from '@wc/shared'
import { useTheme } from '@wc/shared'
import { WorldCoffeeLogoMini } from '@wc/shared'
import { PostCard } from '@wc/shared'
import CreatePost from './CreatePost.vue'

const router = useRouter()
const toast = inject<any>('toast', { show: () => {}, notifCount: ref(0) })
const { isLoggedIn, user, avatar: authAvatar } = useAuth()
const { isDark, toggleTheme } = useTheme()

const searchQuery = ref('')
const isSearching = ref(false)
const searchModalOpen = ref(false)
const composerOpen = ref(false)
const searchInput = ref(null)

const hotTags = ['手冲咖啡', '拿铁', '云南咖啡', '冷萃', '意式浓缩', '咖啡馆探店', '挂耳', '冰美式', '生椰', '蓝山', '耶加雪菲', '曼特宁']
const activeTab = ref('recommend')
const activeTopic = ref('')
const posts = ref([])
const loading = ref(false)
const hasMore = ref(true)
const page = ref(1)
const pageSize = 12
const loadError = ref('')
const FEED_SESSION_KEY = 'worldcoffee:feed-session-id'
const feedSessionId = getOrCreateFeedSessionId()
const exposedPostIds = new Set()
const detailOpenedAt = ref(0)
let feedObserver = null
const loadMoreSentinel = ref(null)
let loadMoreObserver = null
const notifCount = computed(() => {
  const v = toast?.notifCount?.value
  return typeof v === 'number' ? v : 0
})
const messageCount = computed(() => {
  const v = toast?.messageCount?.value
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


const tabs = [
  { key: 'recommend', label: '推荐' },
  { key: 'latest', label: '最新' },
  ...(isLoggedIn.value ? [{ key: 'following', label: '关注' }] : [])
]
const topicTabs = ['咖啡馆', '手冲', '拉花', '甜品', '冷萃']

// 右侧发现栏：热门话题（话题+热词去重取前六）
const trendingTopics = computed(() => [...new Set([...topicTabs, ...hotTags])].slice(0, 6))
// 右侧发现栏：推荐创作者（从信息流作者去重，排除自己）
const recommendedCreators = computed(() => {
  const seen = new Set()
  const list = []
  for (const p of posts.value) {
    const author = p.author || {}
    const id = author.id ?? p.userId
    const name = author.nickname || p.username
    if (!id || seen.has(id) || !name) continue
    if (user.value && String(id) === String(user.value.id)) continue
    seen.add(id)
    list.push({ id, name, avatar: normalizeUrl(author.avatar || p._avatar || '') })
    if (list.length >= 4) break
  }
  return list
})

// 桌面端左侧导航（咖啡社区：发现/关注 + 功能入口 + 消息/通知徽章）
const leftNavItems = computed(() => [
  { key: 'recommend', label: '发现', icon: 'material-symbols:explore-outline' },
  { key: 'latest', label: '最新', icon: 'material-symbols:bolt-outline' },
  ...(isLoggedIn.value ? [{ key: 'following', label: '关注', icon: 'material-symbols:person-add-outline' }] : []),
  { key: null, label: '商城', icon: 'material-symbols:storefront-outline', action: () => router.push('/shop') },
  { key: null, label: '消息', icon: 'material-symbols:chat-bubble-outline', badge: messageCount.value, action: () => router.push(isLoggedIn.value ? '/messages' : '/login') },
  { key: null, label: '通知', icon: 'material-symbols:notifications-outline', badge: notifCount.value, action: () => router.push(isLoggedIn.value ? '/notifications' : '/login') },
  { key: null, label: '发布笔记', icon: 'material-symbols:edit-note-outline', action: () => openComposer() },
  { key: null, label: '我的收藏', icon: 'material-symbols:bookmark-outline', action: () => router.push(isLoggedIn.value ? '/me' : '/login') },
  { key: null, label: 'AI 助手', icon: 'material-symbols:smart-toy-outline', action: () => router.push('/ai-chat') }
])

// 右侧发现栏：近期活动
const upcomingEvents = [
  { title: '城市咖啡节 · 上海站', date: '10.01 - 10.03' },
  { title: '手冲工作坊 · 第 12 期', date: '10.12 周六' }
]

// --- PC 优化 v2：回到顶部 / 冷知识 / 签到 / 足迹 ---
const showFab = ref(false)
function _onScroll() {
  showFab.value = window.scrollY > 420
}
function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const coffeeTips = [
  '咖啡豆其实是种子——我们喝的「咖啡豆」是咖啡果实里的核，烘焙后才变成熟悉的棕色。',
  '拿铁（Latte）在意大利语里就是「牛奶」的意思，所以在意大利点 latte 只会得到一杯牛奶。',
  'espresso 的「crema」油脂层，是高压下二氧化碳与油脂乳化形成的，新鲜豆才丰富。',
  '全球每天约消耗 22.5 亿杯咖啡，其中北欧人均消费量常年第一。'
]
const tipIndex = ref(0)
function nextTip() {
  tipIndex.value = (tipIndex.value + 1) % coffeeTips.length
}

const FOOTPRINT_KEY = 'worldcoffee:footprint'
const CHECKIN_KEY = 'worldcoffee:checkin'
const footprint = reactive({ notes: 0, likes: 0, favs: 0 })
const footprintEmpty = computed(() => !footprint.notes && !footprint.likes && !footprint.favs)
function bumpFootprint(kind: 'notes' | 'likes' | 'favs') {
  footprint[kind] += 1
  try {
    window.localStorage.setItem(FOOTPRINT_KEY, JSON.stringify({ ...footprint }))
  } catch {}
}
function initFootprint() {
  try {
    const raw = JSON.parse(window.localStorage.getItem(FOOTPRINT_KEY) || 'null')
    if (raw) {
      footprint.notes = Number(raw.notes) || 0
      footprint.likes = Number(raw.likes) || 0
      footprint.favs = Number(raw.favs) || 0
    }
  } catch {}
}

const checkedIn = ref(false)
const checkinStreak = ref(0)
const checkinText = computed(() => {
  if (checkedIn.value) return `已连续 ${checkinStreak.value} 天`
  return checkinStreak.value > 0 ? `已连续 ${checkinStreak.value} 天` : '签到领豆币'
})
function initCheckin() {
  try {
    const raw = JSON.parse(window.localStorage.getItem(CHECKIN_KEY) || 'null')
    const today = new Date().toISOString().slice(0, 10)
    checkinStreak.value = Number(raw?.streak) || 0
    checkedIn.value = raw?.date === today
  } catch {}
}
function doCheckin() {
  const today = new Date().toISOString().slice(0, 10)
  const yesterday = new Date(Date.now() - 86400000).toISOString().slice(0, 10)
  let streak = 1
  try {
    const raw = JSON.parse(window.localStorage.getItem(CHECKIN_KEY) || 'null')
    if (raw?.date === yesterday) streak = (Number(raw.streak) || 0) + 1
  } catch {}
  try {
    window.localStorage.setItem(CHECKIN_KEY, JSON.stringify({ date: today, streak }))
  } catch {}
  checkedIn.value = true
  checkinStreak.value = streak
  toast.show(`签到成功！已连续 ${streak} 天`, 'success')
}

// 瀑布流卡片悬停快捷操作：赞 / 藏 / 分享
async function quickLike(post) {
  if (!post?.id) return
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  try {
    const res = await coffeeApi.toggleLike(post.id)
    if (res && res.code === 200) {
      const d = res?.data
      const nowLiked = typeof d === 'object'
        ? !!(d?.likedByMe ?? d?.liked ?? d?.result ?? d?.isLiked)
        : !post.likedByMe
      const prev = !!post.likedByMe
      post.likedByMe = nowLiked
      if (nowLiked !== prev) {
        const base = Number(post.likeCount ?? post.like_count ?? 0)
        post.likeCount = Math.max(0, base + (nowLiked ? 1 : -1))
        post.like_count = post.likeCount
        if (nowLiked) bumpFootprint('likes')
      }
    }
  } catch (e) {
    toast.show(extractApiError(e) || '操作失败', 'error')
  }
}

async function quickFav(post) {
  if (!post?.id) return
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  try {
    const res = await coffeeApi.toggleFavorite(post.id)
    if (res && res.code === 200) {
      const d = res?.data
      const nowFav = typeof d === 'object'
        ? !!(d?.favoritedByMe ?? d?.favorited ?? d?.result)
        : !post.favoritedByMe
      post.favoritedByMe = nowFav
      toast.show(nowFav ? '已收藏' : '已取消收藏', 'success')
      if (nowFav) bumpFootprint('favs')
    }
  } catch (e) {
    toast.show(extractApiError(e) || '操作失败', 'error')
  }
}

function sharePost(post) {
  const url = `${window.location.origin}/posts/${post?.id || ''}`
  if (navigator.clipboard?.writeText) {
    navigator.clipboard.writeText(url)
      .then(() => toast.show('链接已复制，分享给咖啡朋友吧', 'success'))
      .catch(() => toast.show('复制失败，请手动复制地址栏链接', 'warn'))
  } else {
    toast.show('复制失败，请手动复制地址栏链接', 'warn')
  }
}

// --- 工具函数 ---
function extractList(res) {
  if (!res || !res.data) return []
  if (Array.isArray(res.data)) return res.data
  if (Array.isArray(res.data.data)) return res.data.data
  if (Array.isArray(res.data.records)) return res.data.records
  if (Array.isArray(res.data.list)) return res.data.list
  return []
}

function getOrCreateFeedSessionId() {
  if (typeof window === 'undefined') return 'server-session'
  const existing = window.localStorage.getItem(FEED_SESSION_KEY)
  if (existing) return existing
  const id = window.crypto?.randomUUID
    ? window.crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
  window.localStorage.setItem(FEED_SESSION_KEY, id)
  return id
}

function getFeedSource() {
  if (isSearching.value) return 'search'
  if (activeTopic.value) return `topic:${activeTopic.value}`
  return activeTab.value || 'feed'
}

function trackFeedEvent(post, eventType, extra = {}) {
  const postId = post?.id || post?.postId
  if (!postId) return
  coffeeApi.recordFeedEvent({
    postId,
    eventType,
    source: getFeedSource(),
    sessionId: feedSessionId,
    ...extra
  }).catch(() => {})
}

function observeFeedCards() {
  if (typeof window === 'undefined' || !window.IntersectionObserver) return
  if (!feedObserver) {
    feedObserver = new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (!entry.isIntersecting || entry.intersectionRatio < 0.5) return
        const postId = Number((entry.target as HTMLElement).dataset.postId)
        if (!postId || exposedPostIds.has(postId)) return
        exposedPostIds.add(postId)
        const post = posts.value.find(item => Number(item.id) === postId)
        trackFeedEvent(post || { id: postId }, 'IMPRESSION')
        feedObserver.unobserve(entry.target)
      })
    }, { threshold: [0.5] })
  }
  document.querySelectorAll('.feed-post-card[data-post-id]').forEach((el: HTMLElement) => {
    if (el.dataset.feedObserved === '1') return
    el.dataset.feedObserved = '1'
    feedObserver.observe(el)
  })
}

function setupLoadMoreObserver() {
  if (typeof window === 'undefined' || !window.IntersectionObserver) return
  if (!loadMoreObserver) {
    loadMoreObserver = new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) return
        if (!hasMore.value || loading.value || posts.value.length === 0) return
        loadMore()
      })
    }, { rootMargin: '360px 0px 520px 0px', threshold: 0.01 })
  }
  loadMoreObserver.disconnect()
  if (loadMoreSentinel.value) {
    loadMoreObserver.observe(loadMoreSentinel.value)
  }
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

function getTopics(post) {
  return Array.isArray(post?.topics) ? post.topics.filter(Boolean).slice(0, 3) : []
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


// --- 数据加载 ---
async function fetchPosts(reset = false) {
  if (loading.value) return
  if (reset) { page.value = 1; posts.value = []; hasMore.value = true; loadError.value = '' }

  loading.value = true
  try {
    const params: any = { page: page.value, size: pageSize, sessionId: feedSessionId }
    let res
    if (activeTopic.value) {
      res = await coffeeApi.getTopicPosts({ page: page.value, size: pageSize, topic: activeTopic.value })
    } else if (isSearching.value) {
      params.keyword = searchQuery.value
      res = await coffeeApi.search(params)
    } else if (activeTab.value === 'following' && isLoggedIn.value) {
      res = await coffeeApi.getFollowingPosts(params)
    } else if (activeTab.value === 'recommend') {
      res = await coffeeApi.getRecommendedPosts(params)
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
    nextTick(() => {
      observeFeedCards()
      setupLoadMoreObserver()
    })
  }
}

function loadMore() {
  if (!hasMore.value || loading.value) return
  page.value++
  fetchPosts()
}

function markNotInterested(post) {
  if (!post?.id) return
  trackFeedEvent(post, 'DISLIKE')
  posts.value = posts.value.filter(item => item.id !== post.id)
  toast.show('已减少类似内容推荐', 'success')
  nextTick(() => {
    observeFeedCards()
    setupLoadMoreObserver()
  })
}

function switchTab(key) {
  if (activeTab.value === key) return
  activeTab.value = key
  activeTopic.value = ''
  isSearching.value = false
  searchQuery.value = ''
  fetchPosts(true)
}

function searchTopic(topic) {
  activeTopic.value = topic
  searchQuery.value = topic
  isSearching.value = true
  fetchPosts(true)
}

function doSearch() {
  if (!searchQuery.value.trim()) return
  activeTopic.value = ''
  isSearching.value = true
  fetchPosts(true)
}

function clearSearch() {
  searchQuery.value = ''
  activeTopic.value = ''
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

function openComposer() {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  composerOpen.value = true
}

function closeComposer() {
  composerOpen.value = false
}

function handleComposerSuccess() {
  composerOpen.value = false
  bumpFootprint('notes')
  fetchPosts(true)
}

function doSearchFromModal() {
  if (!searchQuery.value.trim()) return
  activeTopic.value = ''
  isSearching.value = true
  searchModalOpen.value = false
  fetchPosts(true)
}

// --- 帖子详情 ---
async function openPost(post) {
  detailImageIndex.value = 0
  selectedPost.value = post
  detailOpenedAt.value = Date.now()
  trackFeedEvent(post, 'CLICK')
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
  if (selectedPost.value && detailOpenedAt.value) {
    const dwellMs = Date.now() - detailOpenedAt.value
    if (dwellMs >= 1000) {
      trackFeedEvent(selectedPost.value, 'DWELL', { dwellMs })
    }
  }
  selectedPost.value = null
  detailOpenedAt.value = 0
}

async function loadComments() {
  if (!selectedPost.value) return
  detailCommentsLoading.value = true
  try {
    // 后端没有单独的评论列表接口，帖子详情响应内嵌 comments
    const res = await coffeeApi.getPostDetail(selectedPost.value.id)
    detailComments.value = (res && res.code === 200 && Array.isArray(res.data?.comments))
      ? res.data.comments
      : []
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
      if (nowLiked) bumpFootprint('likes')
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
      if (detailFav.value) bumpFootprint('favs')
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
function _onKeydown(e) {
  if (e.key === 'Escape' && composerOpen.value) {
    composerOpen.value = false
    return
  }
  if (e.key === 'Escape' && selectedPost.value) {
    closePost()
    return
  }
}
onMounted(async () => {
  fetchPosts(true)
  window.addEventListener('keydown', _onKeydown)
  window.addEventListener('scroll', _onScroll, { passive: true })
  initFootprint()
  initCheckin()
})
onUnmounted(() => {
  if (selectedPost.value) closePost()
  if (feedObserver) {
    feedObserver.disconnect()
    feedObserver = null
  }
  if (loadMoreObserver) {
    loadMoreObserver.disconnect()
    loadMoreObserver = null
  }
  window.removeEventListener('keydown', _onKeydown)
  window.removeEventListener('scroll', _onScroll)
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

/* 长列表性能优化：跳过屏幕外卡片的渲染，减少回流和重绘 */
.feed-post-card {
  content-visibility: auto;
  contain-intrinsic-size: auto 300px;
}

.wc-composer-overlay {
  isolation: isolate;
}
.wc-composer-modal {
  animation: composer-pop .18s ease-out both;
}
.wc-composer-body {
  overscroll-behavior: contain;
}
.wc-composer-close {
  width: 38px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 999px;
  color: var(--text-secondary);
  background: color-mix(in srgb, var(--bg-secondary) 70%, transparent);
  border: 1px solid color-mix(in srgb, var(--border) 70%, transparent);
  transition: background .2s var(--ease-smooth), color .2s var(--ease-smooth);
}
.wc-composer-close:hover {
  color: var(--text-primary);
  background: var(--bg-secondary);
}
@keyframes composer-pop {
  from {
    opacity: 0;
    transform: translateY(18px) scale(.985);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
