<template>
  <div class="tool-list">
    <div class="page-header">
      <div class="page-header__content">
        <h1 class="page-title">实用工具箱</h1>
        <p class="page-subtitle">精选各类开发者工具，提升工作效率</p>
      </div>
    </div>

    <div class="tool-grid">
      <el-card 
        v-for="(tool, index) in tools" 
        :key="tool.id" 
        class="tool-card"
        :style="{ animationDelay: `${index * 80}ms` }"
        shadow="hover"
      >
        <div class="tool-card__header">
          <div class="tool-card__icon" :style="{ backgroundColor: getToolIconBg(index) }">
            <Icon name="tool" size="lg" />
          </div>
          <div class="tool-card__info">
            <h3 class="tool-card__name" @click="goToTool(tool.id)">{{ tool.name }}</h3>
            <span class="tool-card__date">
              <Icon name="calendar" size="xs" />
              {{ formatDate(tool.created_at) }}
            </span>
          </div>
        </div>
        
        <p class="tool-card__description">{{ tool.description }}</p>
        
        <div class="tool-card__footer">
          <div class="tool-card__url">
            <Icon name="link" size="xs" />
            <a :href="tool.url" target="_blank" rel="noopener noreferrer">
              {{ formatUrl(tool.url) }}
            </a>
          </div>
          <el-button type="success" size="small" @click="goToTool(tool.id)">
            查看详情
            <Icon name="arrow-right" size="xs" />
          </el-button>
        </div>
      </el-card>
    </div>
    
    <div v-if="tools.length === 0" class="empty-state">
      <div class="empty-state__icon">
        <Icon name="tool" size="xl" />
      </div>
      <h3 class="empty-state__title">暂无工具</h3>
      <p class="empty-state__description">分享第一个实用工具给大家吧！</p>
      <el-button type="primary" @click="goToShareTool">
        <Icon name="share" size="sm" />
        分享工具
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from '../utils/axios'
import Icon from '../components/Icon.vue'

const router = useRouter()
const tools = ref<any[]>([])

const toolIconBgs = [
  'linear-gradient(135deg, #6366f1 0%, #4f46e5 100%)',
  'linear-gradient(135deg, #22c55e 0%, #16a34a 100%)',
  'linear-gradient(135deg, #f97316 0%, #ea580c 100%)',
  'linear-gradient(135deg, #0ea5e9 0%, #0284c7 100%)',
  'linear-gradient(135deg, #ec4899 0%, #db2777 100%)',
  'linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%)'
]

const getToolIconBg = (index: number) => {
  return toolIconBgs[index % toolIconBgs.length]
}

const goToTool = (id: number) => {
  router.push(`/tool/${id}`)
}

const goToShareTool = () => {
  router.push('/share-tool')
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

const formatUrl = (url: string) => {
  if (!url) return ''
  try {
    const urlObj = new URL(url)
    return urlObj.hostname
  } catch {
    return url
  }
}

onMounted(async () => {
  try {
    tools.value = await axios.get('/tools')
  } catch (error) {
    console.error('获取工具列表失败:', error)
    tools.value = [
      {
        id: 1,
        name: 'Markdown 编辑器',
        description: '一个功能强大的在线 Markdown 编辑器，支持实时预览和多种主题切换，让写作更加轻松愉快。',
        url: 'https://example.com/markdown-editor',
        created_at: '2026-01-25'
      },
      {
        id: 2,
        name: '在线图片压缩工具',
        description: '免费的在线图片压缩工具，支持 JPG、PNG、WebP 等多种格式，快速减小图片体积。',
        url: 'https://example.com/image-compressor',
        created_at: '2026-01-24'
      },
      {
        id: 3,
        name: 'JSON 格式化器',
        description: '快速格式化、验证和压缩 JSON 数据，支持语法高亮和错误检测。',
        url: 'https://example.com/json-formatter',
        created_at: '2026-01-23'
      },
      {
        id: 4,
        name: 'CSS 三角形生成器',
        description: '可视化生成 CSS 三角形代码，支持自定义颜色、尺寸和方向。',
        url: 'https://example.com/css-triangle',
        created_at: '2026-01-22'
      },
      {
        id: 5,
        name: '正则表达式测试器',
        description: '在线测试和调试正则表达式，实时显示匹配结果。',
        url: 'https://example.com/regex-tester',
        created_at: '2026-01-21'
      },
      {
        id: 6,
        name: 'Base64 编解码器',
        description: '在线 Base64 编码和解码工具，支持文件和文本。',
        url: 'https://example.com/base64',
        created_at: '2026-01-20'
      }
    ]
  }
})
</script>

<style scoped>
.tool-list {
  animation: fadeIn var(--transition-normal);
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.page-header {
  background: linear-gradient(135deg, var(--color-success-50) 0%, var(--color-bg-primary) 100%);
  border-radius: var(--border-radius-2xl);
  padding: var(--space-8);
  margin-bottom: var(--space-8);
  text-align: center;
}

.page-title {
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2);
}

.page-subtitle {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
  margin: 0;
}

.tool-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: var(--space-5);
}

.tool-card {
  border-radius: var(--border-radius-xl);
  animation: slideUp var(--transition-normal) ease-out both;
  border: 1px solid var(--color-border-light);
  transition: transform var(--transition-normal), box-shadow var(--transition-normal);
}

.tool-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
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

.tool-card__header {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.tool-card__icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--border-radius-xl);
  color: white;
  flex-shrink: 0;
}

.tool-card__info {
  flex: 1;
  min-width: 0;
}

.tool-card__name {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-1);
  cursor: pointer;
  transition: color var(--transition-fast);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tool-card__name:hover {
  color: var(--color-success-600);
}

.tool-card__date {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.tool-card__description {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: var(--line-height-relaxed);
  margin: 0 0 var(--space-4);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.tool-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-border-light);
}

.tool-card__url {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.tool-card__url a {
  color: var(--color-success-600);
  text-decoration: none;
  max-width: 150px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tool-card__url a:hover {
  text-decoration: underline;
}

.empty-state {
  text-align: center;
  padding: var(--space-12);
  background-color: var(--color-bg-primary);
  border-radius: var(--border-radius-xl);
  border: 2px dashed var(--color-border-light);
  animation: fadeIn var(--transition-normal);
}

.empty-state__icon {
  width: 80px;
  height: 80px;
  margin: 0 auto var(--space-4);
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--color-success-50);
  border-radius: var(--border-radius-full);
  color: var(--color-success-500);
}

.empty-state__title {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2);
}

.empty-state__description {
  color: var(--color-text-secondary);
  margin: 0 0 var(--space-6);
}

@media (max-width: 768px) {
  .page-header {
    padding: var(--space-6) var(--space-4);
  }
  
  .page-title {
    font-size: var(--font-size-2xl);
  }
  
  .tool-grid {
    grid-template-columns: 1fr;
  }
}
</style>
