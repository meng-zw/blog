<template>
  <div class="register-view">
    <h1>用户注册</h1>
    <el-card shadow="hover" class="register-card">
      <el-form :model="registerForm" label-width="80px" class="register-form">
        <el-form-item label="用户名">
          <el-input v-model="registerForm.username" placeholder="请输入用户名" maxlength="50" show-word-limit autocomplete="off"></el-input>
        </el-form-item>
        
        <el-form-item label="密码">
          <el-input v-model="registerForm.password" type="password" placeholder="请输入密码" show-password maxlength="50" show-word-limit autocomplete="off"></el-input>
        </el-form-item>
        
        <el-form-item label="确认密码">
          <el-input v-model="registerForm.confirmPassword" type="password" placeholder="请确认密码" show-password maxlength="50" show-word-limit autocomplete="off"></el-input>
        </el-form-item>
        
        <el-form-item label="邮箱">
          <el-input v-model="registerForm.email" placeholder="请输入邮箱" maxlength="100" show-word-limit autocomplete="off"></el-input>
        </el-form-item>
        
        <el-form-item label="手机号（可选）">
          <el-input v-model="registerForm.phone" placeholder="请输入手机号" maxlength="20" show-word-limit autocomplete="off"></el-input>
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="register" class="register-button">注册</el-button>
          <el-button @click="goToLogin">登录</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from '../utils/axios'

const router = useRouter()
const registerForm = ref({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  phone: ''
})

const register = async () => {
  if (!registerForm.value.username.trim()) {
    alert('请输入用户名')
    return
  }
  
  if (!registerForm.value.password.trim()) {
    alert('请输入密码')
    return
  }
  
  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    alert('两次输入的密码不一致')
    return
  }
  
  if (!registerForm.value.email.trim()) {
    alert('请输入邮箱')
    return
  }
  
  // 简单的邮箱格式验证
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(registerForm.value.email)) {
    alert('请输入有效的邮箱地址')
    return
  }
  
  try {
    // 真实API调用
    await axios.post('/auth/register', {
      username: registerForm.value.username,
      password: registerForm.value.password,
      email: registerForm.value.email,
      phone: registerForm.value.phone
    })
    alert('注册成功，请登录')
    router.push('/login')
  } catch (error: any) {
    console.error('注册失败:', error)
    // 输出更详细的错误信息到控制台
    console.error('错误响应:', error.response)
    console.error('错误状态:', error.response?.status)
    console.error('错误数据:', error.response?.data)
    // 显示更详细的错误信息给用户
    alert(error.response?.data?.message || error.response?.statusText || '注册失败，请稍后重试')
  }
}

const goToLogin = () => {
  router.push('/login')
}
</script>

<style scoped>
.register-view {
  text-align: center;
  max-width: 400px;
  margin: 0 auto;
  padding: 20px;
}

.register-card {
  margin-top: 20px;
}

.register-form {
  width: 100%;
}

.register-button {
  width: 100%;
}
</style>
