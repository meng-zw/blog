<template>
  <div class="share-tool">
    <h1>分享工具</h1>
    <el-card shadow="hover">
      <el-form :model="toolForm" label-width="80px">
        <el-form-item label="工具名称">
          <el-input v-model="toolForm.name" placeholder="请输入工具名称" maxlength="100" show-word-limit></el-input>
        </el-form-item>
        
        <el-form-item label="工具描述">
          <el-input
            type="textarea"
            v-model="toolForm.description"
            :rows="4"
            placeholder="请输入工具描述"
            maxlength="500" show-word-limit
          ></el-input>
        </el-form-item>
        
        <el-form-item label="工具链接">
          <el-input v-model="toolForm.url" placeholder="请输入工具链接" maxlength="200" show-word-limit></el-input>
        </el-form-item>
        
        <el-form-item label="工具分类">
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
import axios from '../utils/axios'

const router = useRouter()
const toolForm = ref({
  name: '',
  description: '',
  url: '',
  category_id: ''
})

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
  if (!toolForm.value.name.trim()) {
    alert('请输入工具名称')
    return
  }
  
  if (!toolForm.value.description.trim()) {
    alert('请输入工具描述')
    return
  }
  
  if (!toolForm.value.url.trim()) {
    alert('请输入工具链接')
    return
  }
  
  if (!toolForm.value.category_id) {
    alert('请选择工具分类')
    return
  }
  
  try {
    // 真实API调用分享工具
    await axios.post('/tools', toolForm.value)
    alert('工具分享成功')
    router.push('/tool')
  } catch (error: any) {
    console.error('分享工具失败:', error)
    alert(error.response?.data?.message || '工具分享失败，请稍后重试')
  }
}

const cancel = () => {
  router.push('/tool')
}

onMounted(async () => {
  await loadCategories()
})
</script>

<style scoped>
.share-tool {
  text-align: left;
}
</style>
