<script setup lang="ts">
/**
 * WorldCoffee Admin · 仪表盘
 *
 * 数据来源：
 *   - GET /api/admin/dashboard                 顶部核心指标（现有接口）
 *   - GET /api/admin/dashboard/trend?days=14   销售 & 订单趋势（TODO 后端补齐）
 *   - GET /api/admin/dashboard/order-status    订单状态分布（TODO 后端补齐）
 *   - GET /api/admin/dashboard/top-categories  热门品类 Top5（TODO 后端补齐）
 *   - GET /api/admin/dashboard/recent-orders   最近订单（TODO 后端补齐）
 *
 * 后端接口未就绪时，前端使用 mock 兜底数据，并在卡片右上角显示"演示数据"标签，
 * 避免"数字墙"看起来像真实统计。
 */
import { ref, computed, onMounted } from 'vue'
import request from '../utils/request'

// ─── 顶部核心指标 ───────────────────────────────
const stats = ref<Record<string, number>>({})
const statsDelta = ref<Record<string, number>>({}) // 环比百分比，正数=上升，负数=下降
const loading = ref(true)

const cards = [
  { key: 'userCount',          title: '用户总数',       icon: 'User',     color: '#6D4C41', softBg: 'rgba(109, 76, 65, 0.10)' },
  { key: 'productCount',       title: '商品总数',       icon: 'Goods',    color: '#7A9B84', softBg: 'rgba(122, 155, 132, 0.12)' },
  { key: 'orderCount',         title: '订单总数',       icon: 'List',     color: '#D48A5D', softBg: 'rgba(212, 138, 93, 0.12)' },
  { key: 'todayOrderCount',    title: '今日新增订单',   icon: 'Calendar', color: '#EEC27B', softBg: 'rgba(238, 194, 123, 0.16)' },
  { key: 'pendingShipCount',   title: '待发货',         icon: 'Van',      color: '#8D6E63', softBg: 'rgba(141, 110, 99, 0.12)' },
  { key: 'totalRevenue',       title: '总销售额',       icon: 'Money',    color: '#A66A43', softBg: 'rgba(166, 106, 67, 0.12)', isMoney: true }
]

// ─── 销售趋势（14 天折线图） ─────────────────────
interface TrendPoint { date: string; revenue: number; orders: number }
const trend = ref<TrendPoint[]>([])
const trendLoading = ref(true)
const isDemoTrend = ref(false)

const TREND_W = 640
const TREND_H = 200
const TREND_PAD = { top: 20, right: 16, bottom: 30, left: 44 }

/** 将 trend 数据映射为 SVG 坐标 */
function toXY(values: number[], idx: number) {
  const n = values.length
  if (n < 2) return { x: TREND_PAD.left, y: TREND_H / 2 }
  const max = Math.max(...values, 1)
  const min = 0
  const innerW = TREND_W - TREND_PAD.left - TREND_PAD.right
  const innerH = TREND_H - TREND_PAD.top - TREND_PAD.bottom
  const x = TREND_PAD.left + (idx * innerW) / (n - 1)
  const y = TREND_PAD.top + innerH - ((values[idx] - min) / (max - min || 1)) * innerH
  return { x, y }
}

const revenuePath = computed(() => {
  const values = trend.value.map(p => p.revenue)
  if (values.length < 2) return ''
  return values.map((_, i) => {
    const { x, y } = toXY(values, i)
    return `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`
  }).join(' ')
})

const revenueAreaPath = computed(() => {
  const values = trend.value.map(p => p.revenue)
  if (values.length < 2) return ''
  const first = toXY(values, 0)
  const last = toXY(values, values.length - 1)
  return `${revenuePath.value} L ${last.x.toFixed(1)} ${TREND_H - TREND_PAD.bottom} L ${first.x.toFixed(1)} ${TREND_H - TREND_PAD.bottom} Z`
})

const ordersPath = computed(() => {
  const values = trend.value.map(p => p.orders)
  if (values.length < 2) return ''
  return values.map((_, i) => {
    const { x, y } = toXY(values, i)
    return `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`
  }).join(' ')
})

const revenueMax = computed(() => Math.max(...trend.value.map(p => p.revenue), 1))
const trendDots = computed(() => trend.value.map((p, i) => {
  const { x, y } = toXY(trend.value.map(v => v.revenue), i)
  return { x, y, ...p }
}))

// Y 轴刻度（4 段）
const yTicks = computed(() => {
  const max = revenueMax.value
  return [0, 0.25, 0.5, 0.75, 1].map(r => ({
    value: Math.round(max * r),
    y: TREND_PAD.top + (TREND_H - TREND_PAD.top - TREND_PAD.bottom) * (1 - r)
  }))
})

// ─── 订单状态分布 ───────────────────────────────
interface OrderStatus { key: string; label: string; value: number; color: string }
const orderStatus = ref<OrderStatus[]>([])
const isDemoStatus = ref(false)
const orderStatusTotal = computed(() => orderStatus.value.reduce((s, x) => s + x.value, 0))

// ─── 热门品类 Top 5 ─────────────────────────────
interface CategoryRank { name: string; count: number; revenue: number }
const topCategories = ref<CategoryRank[]>([])
const isDemoCategories = ref(false)
const topCategoriesMax = computed(() => Math.max(...topCategories.value.map(c => c.count), 1))

// ─── 最近订单 ───────────────────────────────────
interface RecentOrder { orderNo: string; user: string; amount: number; status: string; createdAt: string }
const recentOrders = ref<RecentOrder[]>([])
const isDemoOrders = ref(false)

const statusTagType = (s: string) => {
  if (s === '已完成' || s === '已支付') return 'success'
  if (s === '待发货') return 'warning'
  if (s === '已取消' || s === '退款') return 'danger'
  return 'info'
}

// ─── Mock 数据（后端接口未就绪时兜底） ─────────────
function mockTrend(): TrendPoint[] {
  const out: TrendPoint[] = []
  const now = new Date()
  for (let i = 13; i >= 0; i--) {
    const d = new Date(now.getTime() - i * 86400000)
    const base = 3200 + Math.sin(i * 0.9) * 700 + Math.random() * 900
    out.push({
      date: `${d.getMonth() + 1}/${d.getDate()}`,
      revenue: Math.round(base),
      orders: Math.round(base / 68)
    })
  }
  return out
}

function mockStatus(): OrderStatus[] {
  return [
    { key: 'pending',   label: '待付款',   value: 42,  color: '#D48A5D' },
    { key: 'paid',      label: '待发货',   value: 68,  color: '#EEC27B' },
    { key: 'shipped',   label: '已发货',   value: 91,  color: '#8D6E63' },
    { key: 'done',      label: '已完成',   value: 324, color: '#7A9B84' },
    { key: 'cancelled', label: '已取消',   value: 17,  color: '#A1887F' }
  ]
}

function mockCategories(): CategoryRank[] {
  return [
    { name: '意式浓缩',   count: 182, revenue: 21840 },
    { name: '手冲单品',   count: 156, revenue: 24960 },
    { name: '冷萃',       count: 124, revenue: 14880 },
    { name: '挂耳',       count: 98,  revenue: 7840 },
    { name: '器具周边',   count: 61,  revenue: 18300 }
  ]
}

function mockRecent(): RecentOrder[] {
  return [
    { orderNo: 'WC20260922001', user: 'loean',       amount: 168, status: '待发货', createdAt: '2026-09-22 09:14' },
    { orderNo: 'WC20260922002', user: '咖啡爱好者',  amount: 92,  status: '已支付', createdAt: '2026-09-22 08:47' },
    { orderNo: 'WC20260921015', user: 'barista_zhang', amount: 328, status: '已完成', createdAt: '2026-09-21 21:03' },
    { orderNo: 'WC20260921012', user: '小满',        amount: 58,  status: '已完成', createdAt: '2026-09-21 18:22' },
    { orderNo: 'WC20260921009', user: 'kenji',       amount: 240, status: '已取消', createdAt: '2026-09-21 15:11' }
  ]
}

// ─── 加载 ───────────────────────────────────────
async function loadStats() {
  loading.value = true
  try {
    const data: any = await request.get('/api/admin/dashboard')
    stats.value = data || {}
    statsDelta.value = data?.delta || {}
  } catch {
    stats.value = {}
  } finally {
    loading.value = false
  }
}

async function loadTrend() {
  trendLoading.value = true
  try {
    const data: any = await request.get('/api/admin/dashboard/trend', { params: { days: 14 }, silent: true })
    if (Array.isArray(data) && data.length >= 2) {
      trend.value = data
    } else {
      trend.value = mockTrend()
      isDemoTrend.value = true
    }
  } catch {
    trend.value = mockTrend()
    isDemoTrend.value = true
  } finally {
    trendLoading.value = false
  }
}

async function loadOrderStatus() {
  try {
    const data: any = await request.get('/api/admin/dashboard/order-status', { silent: true })
    if (Array.isArray(data) && data.length) orderStatus.value = data
    else { orderStatus.value = mockStatus(); isDemoStatus.value = true }
  } catch {
    orderStatus.value = mockStatus()
    isDemoStatus.value = true
  }
}

async function loadTopCategories() {
  try {
    const data: any = await request.get('/api/admin/dashboard/top-categories', { silent: true })
    if (Array.isArray(data) && data.length) topCategories.value = data
    else { topCategories.value = mockCategories(); isDemoCategories.value = true }
  } catch {
    topCategories.value = mockCategories()
    isDemoCategories.value = true
  }
}

async function loadRecentOrders() {
  try {
    const data: any = await request.get('/api/admin/dashboard/recent-orders', { silent: true })
    if (Array.isArray(data) && data.length) recentOrders.value = data
    else { recentOrders.value = mockRecent(); isDemoOrders.value = true }
  } catch {
    recentOrders.value = mockRecent()
    isDemoOrders.value = true
  }
}

onMounted(() => {
  loadStats()
  loadTrend()
  loadOrderStatus()
  loadTopCategories()
  loadRecentOrders()
})

function formatMoney(n: number | undefined) {
  if (n == null) return '-'
  if (n >= 10000) return `¥${(n / 10000).toFixed(2)}w`
  return `¥${n}`
}
function formatNumber(n: number | undefined) {
  if (n == null) return '-'
  return n.toLocaleString()
}
</script>

<template>
  <div class="dashboard">
    <!-- 页头 -->
    <div class="dash-header">
      <div>
        <h2>仪表盘</h2>
        <p class="dash-sub">WorldCoffee 运营概览 · 实时数据每 5 分钟刷新</p>
      </div>
      <el-button :icon="'Refresh'" circle @click="loadStats(); loadTrend(); loadOrderStatus(); loadTopCategories(); loadRecentOrders()" />
    </div>

    <!-- 顶部：6 个核心指标 -->
    <div v-loading="loading" class="stat-grid">
      <div v-for="card in cards" :key="card.key" class="stat-card">
        <div class="stat-icon" :style="{ background: card.softBg, color: card.color }">
          <el-icon :size="22"><component :is="card.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-title">{{ card.title }}</div>
          <div class="stat-value" :style="{ color: card.color }">
            {{ card.isMoney ? formatMoney(stats[card.key]) : formatNumber(stats[card.key]) }}
          </div>
          <div v-if="statsDelta[card.key] != null" class="stat-delta" :class="statsDelta[card.key] >= 0 ? 'up' : 'down'">
            <el-icon :size="12">
              <component :is="statsDelta[card.key] >= 0 ? 'Top' : 'Bottom'" />
            </el-icon>
            {{ Math.abs(statsDelta[card.key]) }}% 较昨日
          </div>
        </div>
      </div>
    </div>

    <!-- 中部：销售趋势 + 订单状态分布 -->
    <div class="row-grid">
      <!-- 销售趋势折线图 -->
      <section class="panel panel-trend">
        <div class="panel-head">
          <div>
            <h3>销售趋势</h3>
            <span class="panel-sub">最近 14 天营收与订单量</span>
          </div>
          <div class="legend">
            <span class="legend-item"><i class="dot" style="background: #6D4C41" />营收</span>
            <span class="legend-item"><i class="dot" style="background: #D48A5D" />订单量</span>
            <el-tag v-if="isDemoTrend" size="small" type="warning" effect="plain">演示数据</el-tag>
          </div>
        </div>
        <div v-loading="trendLoading" class="chart-wrap">
          <svg :viewBox="`0 0 ${TREND_W} ${TREND_H}`" preserveAspectRatio="none" class="trend-svg">
            <defs>
              <linearGradient id="revenueGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%"   stop-color="#6D4C41" stop-opacity="0.28" />
                <stop offset="100%" stop-color="#6D4C41" stop-opacity="0" />
              </linearGradient>
            </defs>

            <!-- Y 轴网格 + 刻度 -->
            <g v-for="(t, i) in yTicks" :key="i">
              <line
                :x1="TREND_PAD.left" :x2="TREND_W - TREND_PAD.right"
                :y1="t.y" :y2="t.y"
                stroke="#E7DED6" stroke-dasharray="3 3" stroke-width="1"
              />
              <text :x="TREND_PAD.left - 8" :y="t.y + 4" text-anchor="end" class="axis-label">
                {{ t.value >= 1000 ? (t.value / 1000).toFixed(1) + 'k' : t.value }}
              </text>
            </g>

            <!-- 营收面积 + 折线 -->
            <path v-if="revenueAreaPath" :d="revenueAreaPath" fill="url(#revenueGrad)" />
            <path v-if="revenuePath" :d="revenuePath" fill="none" stroke="#6D4C41" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />

            <!-- 订单量折线（虚线） -->
            <path v-if="ordersPath" :d="ordersPath" fill="none" stroke="#D48A5D" stroke-width="1.8" stroke-dasharray="4 3" stroke-linecap="round" />

            <!-- 数据点 -->
            <g v-for="(p, i) in trendDots" :key="i">
              <circle :cx="p.x" :cy="p.y" r="3.5" fill="#fff" stroke="#6D4C41" stroke-width="2" />
              <text :x="p.x" :y="TREND_H - TREND_PAD.bottom + 16" text-anchor="middle" class="axis-label">
                {{ p.date }}
              </text>
            </g>
          </svg>
        </div>
      </section>

      <!-- 订单状态分布 -->
      <section class="panel panel-status">
        <div class="panel-head">
          <div>
            <h3>订单状态分布</h3>
            <span class="panel-sub">共 {{ orderStatusTotal }} 单</span>
          </div>
          <el-tag v-if="isDemoStatus" size="small" type="warning" effect="plain">演示数据</el-tag>
        </div>

        <!-- 堆叠条 -->
        <div class="stacked-bar">
          <span
            v-for="s in orderStatus"
            :key="s.key"
            :style="{ width: (s.value / (orderStatusTotal || 1) * 100) + '%', background: s.color }"
            :title="`${s.label}: ${s.value}`"
          />
        </div>

        <!-- 图例 -->
        <ul class="status-list">
          <li v-for="s in orderStatus" :key="s.key">
            <i class="dot" :style="{ background: s.color }" />
            <span class="lbl">{{ s.label }}</span>
            <span class="val">{{ s.value }}</span>
            <span class="pct">{{ (s.value / (orderStatusTotal || 1) * 100).toFixed(1) }}%</span>
          </li>
        </ul>
      </section>
    </div>

    <!-- 底部：热门品类 Top 5 + 最近订单 -->
    <div class="row-grid">
      <section class="panel">
        <div class="panel-head">
          <div>
            <h3>热门品类 Top 5</h3>
            <span class="panel-sub">按销量排序</span>
          </div>
          <el-tag v-if="isDemoCategories" size="small" type="warning" effect="plain">演示数据</el-tag>
        </div>
        <ul class="rank-list">
          <li v-for="(c, i) in topCategories" :key="c.name">
            <span class="rank" :class="'r' + (i + 1)">{{ i + 1 }}</span>
            <div class="rank-body">
              <div class="rank-head">
                <span class="name">{{ c.name }}</span>
                <span class="count">{{ c.count }} 单 · ¥{{ c.revenue.toLocaleString() }}</span>
              </div>
              <div class="rank-bar">
                <span :style="{ width: (c.count / topCategoriesMax * 100) + '%' }" />
              </div>
            </div>
          </li>
        </ul>
      </section>

      <section class="panel">
        <div class="panel-head">
          <div>
            <h3>最近订单</h3>
            <span class="panel-sub">最近 5 笔交易</span>
          </div>
          <el-tag v-if="isDemoOrders" size="small" type="warning" effect="plain">演示数据</el-tag>
        </div>
        <el-table :data="recentOrders" size="small" style="width: 100%">
          <el-table-column prop="orderNo" label="订单号" min-width="150" />
          <el-table-column prop="user" label="用户" min-width="100" />
          <el-table-column label="金额" width="90" align="right">
            <template #default="{ row }">¥{{ row.amount }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small" effect="light">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="下单时间" min-width="140" />
        </el-table>
      </section>
    </div>
  </div>
</template>

<style scoped>
.dashboard { display: flex; flex-direction: column; gap: 20px; }

/* ─── 页头 ─── */
.dash-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.dash-header h2 { margin: 0; font-size: 22px; letter-spacing: -0.01em; }
.dash-sub { font-size: 12px; color: var(--text); margin-top: 4px; }

/* ─── 顶部核心指标 ─── */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
@media (max-width: 1200px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 700px)  { .stat-grid { grid-template-columns: 1fr; } }

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: 0 1px 3px rgba(62, 39, 35, 0.04);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(62, 39, 35, 0.08);
}
.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-body { flex: 1; min-width: 0; }
.stat-title { font-size: 12px; color: var(--text); margin-bottom: 4px; }
.stat-value { font-size: 26px; font-weight: 700; line-height: 1.1; letter-spacing: -0.02em; }
.stat-delta {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-top: 6px;
  font-size: 11px;
  font-weight: 500;
}
.stat-delta.up   { color: #7A9B84; }
.stat-delta.down { color: #C0705F; }

/* ─── 面板通用 ─── */
.row-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
}
.row-grid:last-of-type { grid-template-columns: 1fr 1.4fr; }
@media (max-width: 1100px) {
  .row-grid, .row-grid:last-of-type { grid-template-columns: 1fr; }
}

.panel {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 18px 20px;
  box-shadow: 0 1px 3px rgba(62, 39, 35, 0.04);
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}
.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.panel-head h3 { margin: 0; font-size: 15px; font-weight: 600; color: var(--text-h); }
.panel-sub { font-size: 11.5px; color: var(--text); margin-top: 2px; display: block; }

.legend { display: flex; align-items: center; gap: 10px; font-size: 11.5px; color: var(--text); }
.legend-item { display: inline-flex; align-items: center; gap: 4px; }
.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }

/* ─── 折线图 ─── */
.chart-wrap { width: 100%; }
.trend-svg { width: 100%; height: 220px; display: block; }
.axis-label { font-size: 10px; fill: #A79E97; font-family: inherit; }

/* ─── 订单状态堆叠条 ─── */
.stacked-bar {
  display: flex;
  width: 100%;
  height: 12px;
  border-radius: 999px;
  overflow: hidden;
  background: #F5F0EB;
}
.stacked-bar > span {
  height: 100%;
  transition: width 0.4s ease;
}
.status-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 8px; }
.status-list li {
  display: grid;
  grid-template-columns: 12px 1fr auto 48px;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
}
.status-list .lbl { color: var(--text-h); }
.status-list .val { font-weight: 600; color: var(--text-h); }
.status-list .pct { color: var(--text); text-align: right; font-variant-numeric: tabular-nums; }

/* ─── 品类排行 ─── */
.rank-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 14px; }
.rank-list li { display: flex; align-items: center; gap: 12px; }
.rank {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: #A1887F;
  flex-shrink: 0;
}
.rank.r1 { background: linear-gradient(135deg, #EEC27B, #D48A5D); }
.rank.r2 { background: linear-gradient(135deg, #A1887F, #6D4C41); }
.rank.r3 { background: linear-gradient(135deg, #7A9B84, #5A7D64); }
.rank-body { flex: 1; min-width: 0; }
.rank-head {
  display: flex;
  justify-content: space-between;
  font-size: 12.5px;
  margin-bottom: 6px;
}
.rank-head .name { color: var(--text-h); font-weight: 500; }
.rank-head .count { color: var(--text); font-variant-numeric: tabular-nums; }
.rank-bar {
  height: 6px;
  background: #F5F0EB;
  border-radius: 999px;
  overflow: hidden;
}
.rank-bar > span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #6D4C41, #A66A43);
  border-radius: 999px;
  transition: width 0.5s ease;
}
</style>
