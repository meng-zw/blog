<template>
  <div class="register-view">
    <h1>用户注册</h1>
    <el-card shadow="hover" class="register-card">
      <el-form ref="formRef" :model="registerForm" :rules="rules" label-width="80px" class="register-form">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="registerForm.username" placeholder="请输入用户名（3-50 个字符）" maxlength="50" show-word-limit autocomplete="off"></el-input>
        </el-form-item>
        
        <el-form-item label="密码" prop="password">
          <el-input v-model="registerForm.password" type="password" placeholder="请输入密码（至少 6 位）" show-password maxlength="50" show-word-limit autocomplete="off"></el-input>
        </el-form-item>
        
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="registerForm.confirmPassword" type="password" placeholder="请确认密码" show-password maxlength="50" show-word-limit autocomplete="off"></el-input>
        </el-form-item>
        
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="registerForm.email" placeholder="请输入邮箱" maxlength="100" show-word-limit autocomplete="off"></el-input>
        </el-form-item>
        
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="registerForm.phone" placeholder="请输入手机号（选填）" maxlength="20" show-word-limit autocomplete="off"></el-input>
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
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import axios from '../utils/axios'

const router = useRouter()
const formRef = ref<FormInstance>()

const registerForm = ref({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  phone: ''
})

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const phoneRegex = /^1[3-9]\d{9}$/

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度需在 3 到 50 个字符之间', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码长度需在 6 到 50 个字符之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value !== registerForm.value.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { pattern: emailRegex, message: '请输入有效的邮箱地址', trigger: 'blur' }
  ],
  phone: [
    {
      validator: (_rule: any, value: string, callback: any) => {
        // 选填字段：留空时直接通过
        if (!value || !value.trim()) {
          callback()
        } else if (!phoneRegex.test(value.trim())) {
          callback(new Error('请输入有效的手机号'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const register = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  
  try {
    // 真实API调用
    await axios.post('/auth/register', {
      username: registerForm.value.username,
      password: registerForm.value.password,
      email: registerForm.value.email,
      phone: registerForm.value.phone
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (error: any) {
    console.error('注册失败:', error)
    // 展示后端返回的具体原因（拦截器已统一解析到 error.message）
    ElMessage.error(error.message || '注册失败，请稍后重试')
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
