<template>
  <div class="write-article">
    <h1>写文章</h1>
    <el-card shadow="hover">
      <el-form :model="articleForm" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="articleForm.title" placeholder="请输入文章标题" maxlength="100" show-word-limit></el-input>
        </el-form-item>
        
        <el-form-item label="分类">
          <el-select v-model="articleForm.category_id" placeholder="请选择分类">
            <el-option
              v-for="category in categories"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            ></el-option>
          </el-select>
        </el-form-item>
        
        <el-form-item label="标签">
          <el-select
            v-model="articleForm.tags"
            multiple
            placeholder="请选择标签"
            style="width: 100%"
          >
            <el-option
              v-for="tag in tags"
              :key="tag.id"
              :label="tag.name"
              :value="tag.id"
            ></el-option>
          </el-select>
        </el-form-item>
        
        <el-form-item label="内容">
          <div class="editor-container">
            <div ref="editorRef"></div>
          </div>
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="submitArticle">发布文章</el-button>
          <el-button @click="saveDraft">保存草稿</el-button>
          <el-button @click="cancel">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import Vditor from 'vditor'
import 'vditor/dist/index.css'
import axios from '../utils/axios'

const router = useRouter()
const editorRef = ref<HTMLElement | null>(null)
let vditor: Vditor | null = null

const articleForm = ref({
  title: '',
  category_id: '',
  tags: [] as string[],
  content: ''
})

const categories = ref<any[]>([])
const tags = ref<any[]>([])

const initEditor = () => {
  if (editorRef.value) {
    vditor = new Vditor(editorRef.value, {
      height: 600,
      mode: 'sv',
      preview: {
        delay: 100
      },
      cache: {
        enable: false
      },
      after: () => {
        vditor?.setValue('')
      }
    })
  }
}

const loadCategoriesAndTags = async () => {
  try {
    // 真实API调用获取分类和标签
    const [categoriesResponse, tagsResponse] = await Promise.all([
      axios.get('/categories'),
      axios.get('/tags')
    ])
    categories.value = categoriesResponse
    tags.value = tagsResponse
  } catch (error) {
    console.error('加载分类和标签失败:', error)
    // 如果API调用失败，使用模拟数据
    categories.value = [
      { id: '1', name: '技术分享' },
      { id: '2', name: '生活随笔' },
      { id: '3', name: '学习笔记' },
      { id: '4', name: '其他' }
    ]
    tags.value = [
      { id: '1', name: 'Vue' },
      { id: '2', name: 'React' },
      { id: '3', name: 'Spring Boot' },
      { id: '4', name: 'JavaScript' },
      { id: '5', name: 'TypeScript' }
    ]
  }
}

const submitArticle = async () => {
  if (!articleForm.value.title.trim()) {
    alert('请输入文章标题')
    return
  }
  
  if (!articleForm.value.category_id) {
    alert('请选择文章分类')
    return
  }
  
  const content = vditor?.getValue() || ''
  if (!content.trim()) {
    alert('请输入文章内容')
    return
  }
  
  articleForm.value.content = content
  
  try {
    // 真实API调用发布文章
    await axios.post('/articles', articleForm.value)
    alert('文章发布成功')
    router.push('/')
  } catch (error: any) {
    console.error('发布文章失败:', error)
    alert(error.response?.data?.message || '文章发布失败，请稍后重试')
  }
}

const saveDraft = async () => {
  const content = vditor?.getValue() || ''
  articleForm.value.content = content
  
  try {
    // 真实API调用保存草稿
    await axios.post('/articles/draft', articleForm.value)
    alert('草稿保存成功')
  } catch (error: any) {
    console.error('保存草稿失败:', error)
    alert(error.response?.data?.message || '草稿保存失败，请稍后重试')
  }
}

const cancel = () => {
  router.push('/')
}

onMounted(async () => {
  await loadCategoriesAndTags()
  initEditor()
})

onBeforeUnmount(() => {
  vditor?.destroy()
})
</script>

<style scoped>
.write-article {
  text-align: left;
}

.editor-container {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}

.editor-container :deep(.vditor) {
  border: none;
}
</style>
