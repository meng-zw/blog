<template>
  <div class="login-view">
    <div class="login-container">
      <div class="login-card">
        <div class="login__header">
          <Logo variant="icon" />
          <h1 class="login__title">欢迎回来</h1>
          <p class="login__subtitle">登录您的账号，继续探索之旅</p>
        </div>
        
        <el-form 
          :model="loginForm" 
          label-position="top"
          class="login-form"
          @submit.prevent="login"
        >
          <el-form-item label="用户名" required>
            <el-input 
              v-model="loginForm.username" 
              placeholder="请输入用户名"
              size="large"
              :prefix-icon="Icon"
            >
              <template #prefix>
                <Icon name="user" size="md" />
              </template>
            </el-input>
          </el-form-item>
          
          <el-form-item label="密码" required>
            <el-input 
              v-model="loginForm.password" 
              type="password" 
              placeholder="请输入密码"
              size="large"
              show-password
            >
              <template #prefix>
                <Icon name="tool" size="md" />
              </template>
            </el-input>
          </el-form-item>
          
          <div class="login__options">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            <a href="#" class="login__forgot">忘记密码？</a>
          </div>
          
          <el-form-item>
            <el-button type="primary" size="large" class="login-button" :loading="loading" @click="login">
              <Icon v-if="!loading" name="login" size="sm" />
              {{ loading ? '登录中...' : '登录' }}
            </el-button>
          </el-form-item>
        </el-form>
        
        <div class="login__footer">
          <p>还没有账号？<router-link to="/register">立即注册</router-link></p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from '../utils/axios'
import { ElMessage } from 'element-plus'
import Logo from '../components/Logo.vue'
import Icon from '../components/Icon.vue'

const router = useRouter()
const loading = ref(false)
const rememberMe = ref(false)

const loginForm = ref({
  username: '',
  password: ''
})

const login = async () => {
  if (!loginForm.value.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  
  if (!loginForm.value.password.trim()) {
    ElMessage.warning('请输入密码')
    return
  }
  
  loading.value = true
  
  try {
    const response = await axios.post('/auth/login', loginForm.value)
    if (response && response.token) {
      localStorage.setItem('token', response.token)
      ElMessage.success('登录成功')
      router.push('/')
    } else {
      console.error('登录响应数据格式错误:', response)
      ElMessage.error('登录失败，响应数据格式错误')
    }
  } catch (error: any) {
    console.error('登录失败:', error)
    ElMessage.error(error.response?.data?.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-view {
  min-height: calc(100vh - 200px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-6);
}

.login-container {
  width: 100%;
  max-width: 420px;
  animation: slideUp var(--transition-normal);
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.login-card {
  background-color: var(--color-bg-primary);
  border-radius: var(--border-radius-2xl);
  padding: var(--space-8);
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--color-border-light);
}

.login__header {
  text-align: center;
  margin-bottom: var(--space-8);
}

.login__header .logo {
  justify-content: center;
  margin-bottom: var(--space-4);
}

.login__title {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2);
}

.login__subtitle {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin: 0;
}

.login-form {
  margin-bottom: var(--space-6);
}

.login__options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-6);
}

.login__forgot {
  font-size: var(--font-size-sm);
  color: var(--color-primary-600);
  text-decoration: none;
}

.login__forgot:hover {
  text-decoration: underline;
}

.login-button {
  width: 100%;
  height: 44px;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}

.login__footer {
  text-align: center;
  padding-top: var(--space-6);
  border-top: 1px solid var(--color-border-light);
}

.login__footer p {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin: 0;
}

.login__footer a {
  color: var(--color-primary-600);
  font-weight: var(--font-weight-medium);
  text-decoration: none;
}

.login__footer a:hover {
  text-decoration: underline;
}

@media (max-width: 480px) {
  .login-card {
    padding: var(--space-6) var(--space-4);
  }
  
  .login__title {
    font-size: var(--font-size-xl);
  }
}
</style>
