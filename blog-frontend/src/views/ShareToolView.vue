<template>
  <div class="share-tool">
    <h1>分享工具</h1>
    <el-card shadow="hover">
      <el-form ref="formRef" :model="toolForm" :rules="rules" label-width="80px">
        <el-form-item label="工具名称" prop="name">
          <el-input v-model="toolForm.name" placeholder="请输入工具名称（2-100 个字符）" maxlength="100" show-word-limit></el-input>
        </el-form-item>
        
        <el-form-item label="工具描述" prop="description">
          <el-input
            type="textarea"
            v-model="toolForm.description"
            :rows="4"
            placeholder="请输入工具描述"
            maxlength="500" show-word-limit
          ></el-input>
        </el-form-item>
        
        <el-form-item label="工具链接" prop="url">
          <el-input v-model="toolForm.url" placeholder="请输入工具链接（以 http:// 或 https:// 开头）" maxlength="200" show-word-limit></el-input>
        </el-form-item>
        
        <el-form-item label="工具分类" prop="category_id">
          <el-select v-model="toolForm.category_id" placeholder="请选择工具分类">
            <el-option
              v-for="category in categories"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            ></el-option>
          </el-select>
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="submitTool">分享工具</el-button>
          <el-button @click="cancel">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import axios from '../utils/axios'

const router = useRouter()
const formRef = ref<FormInstance>()

const toolForm = ref({
  name: '',
  description: '',
  url: '',
  category_id: ''
})

const urlRegex = /^https?:\/\/\S+\.\S+/

const rules: FormRules = {
  name: [
    { required: true, message: '请输入工具名称', trigger: 'blur' },
    { min: 2, max: 100, message: '工具名称长度需在 2 到 100 个字符之间', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入工具描述', trigger: 'blur' },
    { max: 500, message: '描述不能超过 500 个字符', trigger: 'blur' }
  ],
  url: [
    { required: true, message: '请输入工具链接', trigger: 'blur' },
    { pattern: urlRegex, message: '链接需以 http:// 或 https:// 开头，且包含有效域名', trigger: 'blur' }
  ],
  category_id: [
    { required: true, message: '请选择工具分类', trigger: 'change' }
  ]
}

// 检查登录状态
const checkLogin = () => {
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录后再分享工具')
    router.push('/login')
    return false
  }
  return true
}

const categories = ref<any[]>([])

const loadCategories = async () => {
  try {
    // 真实API调用获取工具分类
    categories.value = await axios.get('/tool-categories')
  } catch (error) {
    console.error('加载工具分类失败:', error)
    // 如果API调用失败，使用模拟数据
    categories.value = [
      { id: '1', name: '开发工具' },
      { id: '2', name: '设计工具' },
      { id: '3', name: '在线服务' },
      { id: '4', name: '其他工具' }
    ]
  }
}

const submitTool = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  
  try {
    // 真实API调用分享工具
    await axios.post('/tools', toolForm.value)
    ElMessage.success('工具分享成功')
    router.push('/tool')
  } catch (error: any) {
    console.error('分享工具失败:', error)
    if (error.response?.status === 403 || error.response?.status === 401) {
      ElMessage.warning('请先登录后再分享工具')
      router.push('/login')
    } else {
      ElMessage.error(error.message || '工具分享失败，请稍后重试')
    }
  }
}

const cancel = () => {
  router.push('/tool')
}

onMounted(async () => {
  // 检查登录状态，未登录则跳转
  if (!checkLogin()) return
  await loadCategories()
})
</script>

<style scoped>
.share-tool {
  text-align: left;
}
</style>
