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
          <div class="tool-card__actions">
            <el-button type="success" size="small" @click="openToolUrl(tool)">
              访问工具
              <Icon name="arrow-right" size="xs" />
            </el-button>
            <el-button size="small" text @click="goToTool(tool.id)">
              详情
            </el-button>
          </div>
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
import { ElMessage } from 'element-plus'
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

const openToolUrl = (tool: any) => {
  if (!tool?.url) {
    ElMessage.info('该工具暂无链接')
    return
  }
  window.open(tool.url, '_blank', 'noopener noreferrer')
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
    const res = await axios.get('/tools', {
      params: { page: 0, size: 100 }
    })
    // 后端返回 {tools, total, page, size} 包装对象，取其中的 tools 数组
    tools.value = res.tools || []
  } catch (error) {
    console.error('获取工具列表失败:', error)
    tools.value = []
  }
})
</script>

<style scoped>
.tool-list {
  animation: none;
}

.page-header {
  background-color: transparent;
  border: none;
  border-top: 1px solid var(--color-border-default);
  border-bottom: 1px solid var(--color-border-default);
  border-radius: 0;
  padding: var(--space-8) var(--space-5);
  margin-bottom: var(--space-8);
  text-align: center;
}

.page-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-5);
  font-family: var(--font-family-display);
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2);
  letter-spacing: var(--letter-spacing-wide);
}

.page-title::before,
.page-title::after {
  content: "";
  width: 48px;
  height: 1px;
  background: var(--color-border-strong);
  flex-shrink: 0;
}

.page-subtitle {
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
  margin: 0;
}

.tool-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: var(--space-5);
}

.tool-card {
  border-radius: var(--border-radius-lg);
  animation: none;
  border: 1px solid var(--color-border-light);
  transition: border-color var(--transition-normal);
}

.tool-card:hover {
  border-color: var(--color-border-default);
}

.tool-card__header {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.tool-card__icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-md);
  color: var(--color-text-tertiary);
  flex-shrink: 0;
  /* 覆盖模板内联渐变背景，统一为朴素色 */
  background: var(--color-bg-tertiary) !important;
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
  color: var(--color-primary-600);
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

.tool-card__actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex-shrink: 0;
}

.tool-card__url {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.tool-card__url a {
  color: var(--color-primary-600);
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
  border-radius: var(--border-radius-lg);
  border: 1px dashed var(--color-border-default);
}

.empty-state__icon {
  width: 72px;
  height: 72px;
  margin: 0 auto var(--space-4);
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--color-bg-tertiary);
  border-radius: var(--border-radius-full);
  color: var(--color-text-tertiary);
}

.empty-state__title {
  font-size: var(--font-size-lg);
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
    font-size: var(--font-size-lg);
  }
  
  .tool-grid {
    grid-template-columns: 1fr;
  }
}
</style>
