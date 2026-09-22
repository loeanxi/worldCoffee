<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import logoFull from '../assets/logo-full.png'

const router = useRouter()
const form = ref({ username: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const data = await request.post('/api/admin/login', form.value)
    sessionStorage.setItem('admin_token', data.token)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    // 错误已在 request 拦截器中处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="admin-login-page">
    <el-card class="admin-login-card" shadow="always">
      <img :src="logoFull" alt="loean worldcoffee" class="admin-login-logo" />
      <h2 class="admin-login-title">World Coffee 管理后台</h2>
      <el-form @submit.prevent="handleLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="管理员账号" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" placeholder="密码" prefix-icon="Lock" size="large" type="password" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="admin-login-submit" :loading="loading" @click="handleLogin">登 录</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.admin-login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 与 C 端登录页一致的深焙咖啡渐变 */
  background:
    radial-gradient(circle at 20% 12%, rgba(238, 194, 123, 0.20), transparent 42%),
    radial-gradient(circle at 85% 80%, rgba(122, 155, 132, 0.16), transparent 40%),
    linear-gradient(150deg, #4E342E 0%, #3E2723 45%, #2C1810 100%);
}

.admin-login-card {
  width: 400px;
  border-radius: 16px;
  border: none;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28);
}

.admin-login-logo {
  width: 150px;
  display: block;
  margin: 0 auto 10px;
}

.admin-login-title {
  text-align: center;
  margin-bottom: 30px;
  color: #2C1810;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.admin-login-submit {
  width: 100%;
  background: linear-gradient(135deg, #6D4C41, #3E2723);
  border: none;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.admin-login-submit:hover {
  background: linear-gradient(135deg, #8D5A3B, #4E342E);
}
</style>
