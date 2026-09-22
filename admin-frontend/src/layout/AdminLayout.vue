<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import logoMark from '../assets/logo-mark.png'

const router = useRouter()
const route = useRoute()

const menuItems = [
  { path: '/dashboard', title: '仪表盘', icon: 'Odometer' },
  { path: '/users', title: '用户管理', icon: 'User' },
  { path: '/products', title: '商品管理', icon: 'Goods' },
  { path: '/orders', title: '订单管理', icon: 'List' },
  { path: '/marketing', title: '营销管理', icon: 'Present' },
  { path: '/moderation', title: '举报审核', icon: 'Warning' },
  { path: '/governance', title: '内容治理', icon: 'Filter' }
]

function handleLogout() {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    type: 'warning'
  }).then(() => {
    sessionStorage.removeItem('admin_token')
    router.push('/login')
  }).catch(() => {})
}
</script>

<template>
  <el-container class="admin-shell">
    <el-aside width="220px" class="admin-aside">
      <div class="admin-brand">
        <img :src="logoMark" alt="loean worldcoffee" class="admin-brand-logo" />
        <span>World Coffee 后台</span>
      </div>
      <el-menu
        :default-active="route.path"
        background-color="#3E2723"
        text-color="#D7CCC8"
        active-text-color="#EEC27B"
        router
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <span class="admin-user">管理员</span>
        <el-button type="danger" text @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          退出
        </el-button>
      </el-header>

      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-shell {
  height: 100vh;
}

.admin-aside {
  background: #3E2723;
}

.admin-brand {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #FFF8E1;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.01em;
  border-bottom: 1px solid rgba(215, 204, 200, 0.14);
}

.admin-brand-logo {
  width: 34px;
  height: 34px;
  background: #FFF8E1;
  border-radius: 8px;
  padding: 3px;
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  border-bottom: 1px solid var(--border);
  background: var(--bg);
}

.admin-user {
  margin-right: 16px;
  color: var(--text);
  font-size: 13px;
}

.admin-main {
  background: #F5F0EB;
  padding: 20px;
}

/* Element Plus 菜单在咖啡棕侧栏下的悬停/激活态微调 */
.admin-aside :deep(.el-menu) {
  border-right: none;
}
.admin-aside :deep(.el-menu-item:hover) {
  background-color: #4E342E !important;
  color: #FFF8E1 !important;
}
.admin-aside :deep(.el-menu-item.is-active) {
  background-color: rgba(238, 194, 123, 0.12) !important;
  border-left: 3px solid #EEC27B;
  padding-left: 17px !important;
}
</style>
