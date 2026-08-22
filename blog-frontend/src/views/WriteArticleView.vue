<template>
  <div class="write-article">
    <h1>写文章</h1>
    <el-card shadow="hover">
      <el-form ref="formRef" :model="articleForm" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="articleForm.title" placeholder="请输入文章标题（2-100 个字符）" maxlength="100" show-word-limit></el-input>
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

        <el-form-item label="封面图">
          <el-upload
            class="cover-uploader"
            action="/api/files/upload"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="handleCoverSuccess"
            :on-error="handleCoverError"
            :before-upload="beforeCoverUpload"
          >
            <img v-if="articleForm.cover_image" :src="articleForm.cover_image" class="cover-preview" alt="封面图" />
            <div v-else class="cover-placeholder">
              <Icon name="image" size="lg" />
              <span>上传封面图</span>
            </div>
          </el-upload>
          <el-button v-if="articleForm.cover_image" type="danger" text size="small" @click="articleForm.cover_image = ''">
            移除封面
          </el-button>
        </el-form-item>

        <el-form-item v-if="isAdmin" label="置顶">
          <el-switch v-model="articleForm.is_top" active-text="置顶文章" />
        </el-form-item>

        <el-form-item label="发布方式">
          <el-radio-group v-model="publishMode">
            <el-radio value="publish">立即发布</el-radio>
            <el-radio value="schedule">定时发布</el-radio>
            <el-radio value="draft">存为草稿</el-radio>
          </el-radio-group>
          <el-date-picker
            v-if="publishMode === 'schedule'"
            v-model="articleForm.publish_time"
            type="datetime"
            placeholder="选择发布时间"
            :disabled-date="disablePastDate"
            style="margin-left: 12px"
          />
        </el-form-item>
        
        <el-form-item label="内容">
          <div class="editor-container">
            <div ref="editorRef"></div>
          </div>
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="submitArticle" :loading="submitting">
            {{ publishMode === 'schedule' ? '定时发布' : '发布文章' }}
          </el-button>
          <el-button @click="saveDraft" :loading="submitting">保存草稿</el-button>
          <el-button @click="cancel">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import Vditor from 'vditor'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import 'vditor/dist/index.css'
import axios from '../utils/axios'
import Icon from '../components/Icon.vue'

const router = useRouter()
const editorRef = ref<HTMLElement | null>(null)
const formRef = ref<FormInstance>()
let vditor: Vditor | null = null

const submitting = ref(false)
const publishMode = ref<'publish' | 'schedule' | 'draft'>('publish')

// 上传请求头（携带JWT）
const uploadHeaders = computed(() => {
  const token = localStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
})

// 当前用户是否为管理员（置顶仅管理员可用）
const isAdmin = computed(() => localStorage.getItem('role') === 'admin')

// 检查登录状态
const checkLogin = () => {
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录后再写文章')
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
  html_content: '',
  cover_image: '',
  publish_time: null as string | null,
  is_top: false
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
      upload: {
        url: '/api/files/upload',
        fieldName: 'file',
        max: 10 * 1024 * 1024,
        // 将后端 { url } 响应转换为 Vditor 要求的格式
        format: (files: File[], responseText: string) => {
          const fileName = files[0]?.name || 'image'
          const res: any = { code: 0, msg: '', data: { errFiles: [], succMap: {} } }
          try {
            const parsed = JSON.parse(responseText)
            if (parsed.url) {
              res.data.succMap[fileName] = parsed.url
            } else {
              res.code = 1
              res.msg = parsed.message || '图片上传失败'
              res.data.errFiles = [fileName]
            }
          } catch (e) {
            res.code = 1
            res.msg = '上传响应解析失败'
            res.data.errFiles = [fileName]
          }
          return res
        }
      },
      after: () => {
        vditor?.setValue('')
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
  }
}

// 封面图上传校验与回调
const beforeCoverUpload = (file: File) => {
  const allowedTypes = ['image/png', 'image/jpeg', 'image/gif', 'image/webp', 'image/bmp']
  if (!allowedTypes.includes(file.type)) {
    ElMessage.error('仅支持 PNG/JPG/GIF/WebP/BMP 格式的图片')
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 10MB')
    return false
  }
  return true
}

const handleCoverSuccess = (response: any) => {
  if (response?.url) {
    articleForm.value.cover_image = response.url
    ElMessage.success('封面图上传成功')
  } else {
    ElMessage.error(response?.message || '封面图上传失败')
  }
}

const handleCoverError = (error: any) => {
  ElMessage.error(error?.message || '封面图上传失败，请稍后重试')
}

// 定时发布时间不能早于当前时间
const disablePastDate = (date: Date) => {
  return date.getTime() < Date.now() - 60 * 1000
}

// 收集编辑器内容
const collectContent = () => {
  articleForm.value.content = vditor?.getValue() || ''
  articleForm.value.html_content = vditor?.getHTML() || ''
}

const submitArticle = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const content = vditor?.getValue() || ''
  if (!content.trim()) {
    ElMessage.warning('请输入文章内容')
    return
  }

  // 定时发布校验
  if (publishMode.value === 'schedule' && !articleForm.value.publish_time) {
    ElMessage.warning('请选择定时发布时间')
    return
  }
  if (publishMode.value === 'schedule' && new Date(articleForm.value.publish_time!).getTime() <= Date.now()) {
    ElMessage.warning('定时发布时间必须晚于当前时间')
    return
  }

  submitting.value = true
  collectContent()

  try {
    await axios.post('/articles', {
      ...articleForm.value,
      status: publishMode.value === 'schedule' ? 'scheduled' : 'published'
    })
    ElMessage.success(publishMode.value === 'schedule' ? '文章已安排定时发布' : '文章发布成功')
    router.push('/')
  } catch (error: any) {
    console.error('发布文章失败:', error)
    ElMessage.error(error.message || '文章发布失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

const saveDraft = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  collectContent()

  try {
    await axios.post('/articles', {
      ...articleForm.value,
      status: 'draft',
      publish_time: null
    })
    ElMessage.success('草稿已保存')
    router.push('/profile')
  } catch (error: any) {
    console.error('保存草稿失败:', error)
    ElMessage.error(error.message || '草稿保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

const cancel = () => {
  router.push('/')
}

onMounted(async () => {
  if (!checkLogin()) return
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

.cover-uploader :deep(.el-upload) {
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  cursor: pointer;
  overflow: hidden;
  transition: border-color var(--transition-fast);
}

.cover-uploader :deep(.el-upload:hover) {
  border-color: var(--color-primary-500);
}

.cover-preview {
  width: 240px;
  height: 135px;
  object-fit: cover;
  display: block;
}

.cover-placeholder {
  width: 240px;
  height: 135px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
