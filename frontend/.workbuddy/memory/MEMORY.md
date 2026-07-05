# World Coffee 前端项目笔记

## 项目概览
- **名称**: worldcoffee-frontend（World Coffee / 世界咖啡）
- **定位**: 咖啡主题社区 + 电商综合应用（移动端优先，桌面端有背景装饰）
- **后端**: Java（/api 代理到 localhost:8080，统一响应 {code, msg, data}，JWT + Redis + ES）

## 技术栈
- Vue 3 (Composition API, `<script setup>`) + Vite 5 + Vue Router 4
- TailwindCSS 3.4（自定义咖啡色系 design tokens，CSS 变量驱动语义化主题色）
- @iconify/vue（图标）、@vueuse/core + @vueuse/motion（工具 + 动画）
- axios（HTTP）、clsx + tailwind-merge（类名合并）
- Google Fonts: DM Serif Display（标题）+ Inter（正文）+ Noto Serif SC（中文衬线）

## 设计系统
- 咖啡色系：coffee-bean #3E2723（主色）→ coffee-cream #FFF8E1（底色）
- 语义化色板：surface / ink / brand / line / glass / shadow（全用 CSS 变量，支持 light/dark）
- 双主题切换：useTheme.js，class 模式，跟随系统偏好
- 丰富动画：fade-up、float、steam、shimmer、toast-in 等（tailwind.config.js keyframes）
- 无障碍：focus-visible 焦点环

## 六大功能模块
1. **用户系统** (`/api/users`): 登录/注册/资料/关注/粉丝/头像/密码/注销/短信验证码
2. **咖啡社区** (`/api/coffee`): 发帖/列表/详情/点赞/收藏/评论/搜索/热门/关注流
3. **商城系统** (`/api/shop`): 商品/详情/购物车/订单/地址/分类/ES搜索
4. **优惠券/秒杀** (`/api/shop/coupons`, `/api/shop/seckill`): 领券/我的券/秒杀活动/秒杀下单
5. **通知/私信** (`/api/notifications`, `/api/messages`): 通知列表/SSE实时推送/私信会话/聊天历史
6. **AI 对话** (`/api/ai`): 流式聊天(打字机)/会话列表/历史消息/知识库上传

## 关键架构
- **API 层** (`src/api/index.js`): 大量数据归一化（normalizeUrl/parseProductImages），统一响应解析 ok()，兼容数组和分页包装
- **认证** (`src/composables/useAuth.js`): JWT 存 localStorage (wc_token/wc_user)，请求拦截器自动附带 Bearer，401 自动清 token 跳登录
- **SSE** (App.vue + api/createSSESubscriber): fetch+ReadableStream 手动解析（绕过 EventSource 无法设 header），指数退避重连(最多5次)，失败降级为30s轮询
- **AI 流式**: fetch + ReadableStream，text/html 逐字返回
- **全局 Toast**: App.vue provide('toast')，各页面 inject 使用
- **路由守卫**: requiresAuth meta，已登录访问 login/register 自动跳首页

## 目录结构
```
src/
├── main.js              入口
├── App.vue              根组件（SSE连接/全局Toast/底部导航/页面过渡）
├── style.css            全局样式
├── router/index.js      路由（20+ 页面，懒加载）
├── api/index.js         API 封装（6 大模块 + SSE + AI）
├── composables/
│   ├── useAuth.js       认证状态管理
│   └── useTheme.js      主题切换
├── components/          通用组件（AppButton/AppInput/AppCard/EmptyState/BottomNav/Logo）
├── views/               20+ 页面
└── utils/time.js        时间工具
```

## 启动
- `npm run dev` → http://localhost:3000
- 代理：/api 和 /uploads → http://localhost:8080
