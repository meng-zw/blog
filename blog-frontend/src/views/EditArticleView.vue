<template>
  <div class="edit-article">
    <div class="page-header">
      <div class="page-header__content">
        <h1 class="page-title">编辑文章</h1>
        <p class="page-subtitle">修改您的文章内容</p>
      </div>
    </div>

    <el-card shadow="hover" class="editor-card">
      <el-form ref="formRef" :model="articleForm" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input 
            v-model="articleForm.title" 
            placeholder="请输入文章标题（2-100 个字符）" 
            maxlength="100" 
            show-word-limit
          ></el-input>
        </el-form-item>
        
        <el-form-item label="分类" prop="category_id">
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
            v-model="articleForm.tag_ids"
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
          <el-button type="primary" @click="submitArticle" :loading="submitting">
            保存修改
          </el-button>
          <el-button @click="cancel">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Vditor from 'vditor'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import 'vditor/dist/index.css'
import axios from '../utils/axios'

const route = useRoute()
const router = useRouter()
const editorRef = ref<HTMLElement | null>(null)
const formRef = ref<FormInstance>()
let vditor: Vditor | null = null

const submitting = ref(false)
const articleId = computed(() => route.params.id as string)

// 检查登录状态
const checkLogin = () => {
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录后再编辑文章')
    router.push('/login')
    return false
  }
  return true
}

const articleForm = ref({
  title: '',
  category_id: '' as string | number,
  tag_ids: [] as number[],
  content: '',
  html_content: ''
})

const rules: FormRules = {
  title: [
    { required: true, message: '请输入文章标题', trigger: 'blur' },
    { min: 2, max: 100, message: '标题长度需在 2 到 100 个字符之间', trigger: 'blur' }
  ],
  category_id: [
    { required: true, message: '请选择文章分类', trigger: 'change' }
  ]
}

const categories = ref<any[]>([])
const tags = ref<any[]>([])

// 加载文章数据
const loadArticle = async () => {
  try {
    const response = await axios.get(`/articles/${articleId.value}`)
    articleForm.value = {
      title: response.title || '',
      category_id: response.category?.id || '',
      tag_ids: (response.tags || []).map((t: any) => t.id),
      content: response.content || '',
      html_content: response.html_content || ''
    }
  } catch (error: any) {
    console.error('加载文章失败:', error)
    if (error.response?.status === 404) {
      ElMessage.error('文章不存在')
      router.push('/article')
    } else {
      ElMessage.error(error.message || '加载文章失败')
    }
  }
}

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
        // 加载已有内容
        vditor?.setValue(articleForm.value.content || '')
      }
    })
  }
}

const loadCategoriesAndTags = async () => {
  try {
    const [categoriesResponse, tagsResponse] = await Promise.all([
      axios.get('/categories'),
      axios.get('/tags')
    ])
    categories.value = categoriesResponse
    tags.value = tagsResponse
  } catch (error) {
    console.error('加载分类和标签失败:', error)
    // 使用模拟数据
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
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  
  const content = vditor?.getValue() || ''
  if (!content.trim()) {
    ElMessage.warning('请输入文章内容')
    return
  }
  
  submitting.value = true
  
  articleForm.value.content = content
  articleForm.value.html_content = vditor?.getHTML() || ''

  try {
    await axios.put(`/articles/${articleId.value}`, articleForm.value)
    ElMessage.success('文章更新成功')
    router.push(`/article/${articleId.value}`)
  } catch (error: any) {
    console.error('更新文章失败:', error)
    if (error.response?.status === 403) {
      ElMessage.error('您没有权限编辑这篇文章')
    } else {
      ElMessage.error(error.message || '文章更新失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}

const cancel = () => {
  router.push(`/article/${articleId.value}`)
}

onMounted(async () => {
  if (!checkLogin()) return
  
  await loadArticle()
  await loadCategoriesAndTags()
  initEditor()
})

onBeforeUnmount(() => {
  vditor?.destroy()
})
</script>

<style scoped>
.edit-article {
  text-align: left;
}

.page-header {
  background-color: transparent;
  border: none;
  border-top: 1px solid var(--color-border-default);
  border-bottom: 1px solid var(--color-border-default);
  padding: var(--space-8) var(--space-5);
  margin-bottom: var(--space-8);
  text-align: center;
}

.page-title {
  font-family: var(--font-family-display);
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2);
  letter-spacing: var(--letter-spacing-wide);
}

.page-subtitle {
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
  margin: 0;
}

.editor-card {
  max-width: 900px;
  margin: 0 auto;
}

.editor-container {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}

.editor-container :deep(.vditor) {
  border: none;
}

@media (max-width: 768px) {
  .page-header {
    padding: var(--space-6) var(--space-4);
  }
  
  .page-title {
    font-size: var(--font-size-xl);
  }
  
  .editor-card {
    margin: 0 var(--space-4);
  }
}
</style>
