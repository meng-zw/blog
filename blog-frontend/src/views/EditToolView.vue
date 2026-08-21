<template>
  <div class="edit-tool">
    <h1>编辑工具</h1>
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
          <el-button type="primary" :loading="submitting" @click="submitEdit">保存修改</el-button>
          <el-button @click="cancel">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import axios from '../utils/axios'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const toolId = route.params.id as string

const toolForm = ref({
  name: '',
  description: '',
  url: '',
  category_id: '' as string | number
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
    ElMessage.warning('请先登录后再编辑工具')
    router.push('/login')
    return false
  }
  return true
}

const categories = ref<any[]>([])

const loadCategories = async () => {
  try {
    // 真实API调用获取工具分类
    categories.value = await axios.get('/categories/tool')
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

// 加载工具详情并预填表单
const loadToolDetail = async () => {
  try {
    const tool = await axios.get(`/tools/${toolId}`)
    if (!tool) {
      ElMessage.error('工具不存在')
      router.push('/tool')
      return
    }
    toolForm.value.name = tool.name || ''
    toolForm.value.description = tool.description || ''
    toolForm.value.url = tool.url || ''
    // 后端返回的 category 是嵌套对象，取其 id 回填表单
    toolForm.value.category_id = tool.category?.id || ''
  } catch (error: any) {
    console.error('加载工具详情失败:', error)
    ElMessage.error(error.message || '加载工具详情失败')
    router.push('/tool')
  }
}

const submitEdit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    // 真实API调用更新工具
    await axios.put(`/tools/${toolId}`, toolForm.value)
    ElMessage.success('工具修改成功')
    router.push(`/tool/${toolId}`)
  } catch (error: any) {
    console.error('更新工具失败:', error)
    if (error.response?.status === 403 || error.response?.status === 401) {
      ElMessage.warning('没有权限编辑该工具')
      router.push('/login')
    } else {
      ElMessage.error(error.message || '工具修改失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}

const cancel = () => {
  router.push(`/tool/${toolId}`)
}

onMounted(async () => {
  // 检查登录状态，未登录则跳转
  if (!checkLogin()) return
  await loadCategories()
  await loadToolDetail()
})
</script>

<style scoped>
.edit-tool {
  text-align: left;
}
</style>
