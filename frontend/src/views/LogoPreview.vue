<!-- ============================================================
     WorldCoffee Logo 预览页
     路径：/logo-preview （或直接放到首页最上方）
============================================================ -->
<template>
  <div class="min-h-screen bg-surface">
    <div class="max-w-5xl mx-auto px-6 py-12">

      <!-- 头部 -->
      <div class="text-center mb-12">
        <p class="text-[13px] tracking-[0.3em] text-ink-muted uppercase mb-3">Brand Identity</p>
        <h1 class="text-[32px] md:text-[44px] font-bold text-ink leading-tight">
          WorldCoffee Logo
        </h1>
        <p class="mt-4 text-ink-muted text-[15px]">简约 · 大气 · 咖啡色调</p>
      </div>

      <!-- === 主展示：大号 Logo === -->
      <div class="bg-surface-elevated rounded-[28px] shadow-[0_8px_32px_rgba(62,39,35,0.08)] p-10 md:p-16 text-center">
        <WorldCoffeeLogo :size="200" variant="full" />
        <p class="mt-8 text-ink text-[14px] leading-relaxed max-w-xl mx-auto">
          线条简约的咖啡杯造型，上中下三层奶泡高光，搭配三条优雅弧线表示热气升腾。
          <br />
          深咖啡色渐变体现精品咖啡的醇厚与温度感。
        </p>
      </div>

      <!-- === 不同尺寸 & 背景展示 === -->
      <div class="mt-10">
        <h2 class="text-[18px] font-bold text-ink mb-5">不同尺寸</h2>
        <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div class="bg-surface-elevated rounded-2xl p-6 text-center shadow-sm">
            <WorldCoffeeLogo :size="160" />
            <p class="mt-3 text-[12px] text-ink-muted">160px</p>
          </div>
          <div class="bg-surface-elevated rounded-2xl p-6 text-center shadow-sm">
            <WorldCoffeeLogo :size="120" />
            <p class="mt-3 text-[12px] text-ink-muted">120px</p>
          </div>
          <div class="bg-surface-elevated rounded-2xl p-6 text-center shadow-sm">
            <WorldCoffeeLogoMini :size="96" :with-circle="true" />
            <p class="mt-3 text-[12px] text-ink-muted">96px · 极简</p>
          </div>
          <div class="bg-surface-elevated rounded-2xl p-6 text-center shadow-sm">
            <WorldCoffeeLogoMini :size="64" :with-circle="false" />
            <p class="mt-3 text-[12px] text-ink-muted">64px · 纯图标</p>
          </div>
        </div>
      </div>

      <!-- === 不同背景色 === -->
      <div class="mt-10">
        <h2 class="text-[18px] font-bold text-ink mb-5">不同背景</h2>
        <div class="grid grid-cols-2 md:grid-cols-3 gap-4">
          <div class="rounded-2xl p-8 text-center shadow-sm" style="background: #2C1810;">
            <div class="inline-block" style="filter: invert(1);">
              <WorldCoffeeLogoMini :size="100" variant="icon" :with-circle="false" />
            </div>
            <p class="mt-3 text-[12px]" style="color:#F5F0EB;">深咖啡底</p>
          </div>
          <div class="rounded-2xl p-8 text-center shadow-sm" style="background: #3E2723;">
            <div style="filter: invert(1);">
              <WorldCoffeeLogoMini :size="100" variant="icon" :with-circle="false" />
            </div>
            <p class="mt-3 text-[12px]" style="color:#F5F0EB;">深棕底</p>
          </div>
          <div class="rounded-2xl p-8 text-center shadow-sm border border-line/40" style="background: #FAF7F5;">
            <WorldCoffeeLogoMini :size="100" />
            <p class="mt-3 text-[12px] text-ink-muted">米色底 · Logo色</p>
          </div>
        </div>
      </div>

      <!-- === 不同 variant 展示 === -->
      <div class="mt-10">
        <h2 class="text-[18px] font-bold text-ink mb-5">三种排版方式</h2>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div class="bg-surface-elevated rounded-2xl p-8 text-center shadow-sm">
            <p class="text-[11px] text-ink-muted mb-4">icon 纯图标</p>
            <WorldCoffeeLogo :size="110" />
          </div>
          <div class="bg-surface-elevated rounded-2xl p-8 text-center shadow-sm">
            <p class="text-[11px] text-ink-muted mb-4">full 图标+双行文字</p>
            <WorldCoffeeLogo :size="110" variant="full" />
          </div>
          <div class="bg-surface-elevated rounded-2xl p-8 text-center shadow-sm">
            <p class="text-[11px] text-ink-muted mb-4">mark 图标+小标签</p>
            <WorldCoffeeLogo :size="110" variant="mark" />
          </div>
        </div>
      </div>

      <!-- === 使用说明 === -->
      <div class="mt-12 bg-surface-elevated/70 rounded-2xl p-8 shadow-sm">
        <h2 class="text-[18px] font-bold text-ink mb-4">使用方式</h2>
        <div class="space-y-3 text-[13px] text-ink-muted leading-relaxed">
          <p><span class="font-bold text-ink">组件引入：</span></p>
          <pre v-pre class="bg-surface/60 rounded-xl p-4 text-[12px] text-ink overflow-x-auto"><code>&lt;script setup&gt;
import WorldCoffeeLogo from '@/components/WorldCoffeeLogo.vue'
import WorldCoffeeLogoMini from '@/components/WorldCoffeeLogoMini.vue'
&lt;/script&gt;

&lt;WorldCoffeeLogo :size="120" /&gt;                 &lt;!-- 标准版，默认 120px --&gt;
&lt;WorldCoffeeLogo :size="200" variant="full" /&gt;     &lt;!-- 大号+文字 --&gt;
&lt;WorldCoffeeLogoMini :size="80" :with-circle="false" /&gt; &lt;!-- 极简，仅图标 --&gt;</code></pre>
          <p class="mt-6"><span class="font-bold text-ink">Props：</span></p>
          <ul class="list-disc pl-6 space-y-1">
            <li><code class="text-brand">size</code>：数字，图标大小（px）</li>
            <li><code class="text-brand">variant</code>：<code>icon</code> / <code>full</code> / <code>mark</code>，文字排版方式</li>
            <li><code class="text-brand">withCircle</code>：是否加圆形描边（仅 Mini 版有）</li>
          </ul>
        </div>
      </div>

      <!-- 底部返回 -->
      <div class="mt-12 text-center">
        <button
        @click="$router.push('/')"
        class="px-8 py-3 rounded-full brand-gradient-btn text-[14px] font-semibold hover:brightness-95 transition-colors tap-scale"
        >
          返回首页
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import WorldCoffeeLogo from '../components/WorldCoffeeLogo.vue'
import WorldCoffeeLogoMini from '../components/WorldCoffeeLogoMini.vue'
</script>
