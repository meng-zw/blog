<template>
  <div class="profile-view">
    <div class="profile-container">
      <!-- 用户信息卡片 -->
      <el-card class="profile-card">
        <template #header>
          <div class="card-header">
            <h2>个人资料</h2>
          </div>
        </template>
        
        <div class="profile-info">
          <div class="avatar-section">
            <div class="avatar" :style="{ backgroundImage: user.avatar ? `url(${user.avatar})` : 'none' }">
              <Icon name="user" size="xl" v-if="!user.avatar" />
            </div>
            <div class="user-meta">
              <h3 class="username">{{ user.username }}</h3>
              <p class="email">{{ user.email }}</p>
              <el-tag :type="user.role === 'admin' ? 'danger' : 'primary'" size="small">
                {{ user.role === 'admin' ? '管理员' : '普通用户' }}
              </el-tag>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 统计信息 -->
      <div class="stats-grid">
        <el-card class="stat-card">
          <div class="stat-item">
            <span class="stat-number">{{ stats.articleCount }}</span>
            <span class="stat-label">文章</span>
          </div>
        </el-card>
        <el-card class="stat-card">
          <div class="stat-item">
            <span class="stat-number">{{ stats.toolCount }}</span>
            <span class="stat-label">工具</span>
          </div>
        </el-card>
        <el-card class="stat-card">
          <div class="stat-item">
            <span class="stat-number">{{ stats.totalViews }}</span>
            <span class="stat-label">总浏览</span>
          </div>
        </el-card>
      </div>

      <!-- 我的文章 -->
      <el-card class="my-articles">
        <template #header>
          <div class="card-header">
            <h2>我的文章</h2>
            <router-link to="/write" class="write-btn">
              <Icon name="write" size="sm" />
              写新文章
            </router-link>
          </div>
        </template>
        
        <div class="article-list" v-if="articles.length > 0">
          <div 
            v-for="article in articles" 
            :key="article.id" 
            class="article-item"
          >
            <div class="article-info">
              <h4 class="article-title" @click="goToArticle(article.id)">
                {{ article.title }}
              </h4>
              <div class="article-meta">
                <span class="article-category">{{ article.category?.name || '技术' }}</span>
                <span class="article-date">{{ formatDate(article.created_at) }}</span>
              </div>
            </div>
            <div class="article-stats">
              <span><Icon name="eye" size="xs" /> {{ article.view_count || 0 }}</span>
              <span><Icon name="comment" size="xs" /> {{ article.comment_count || 0 }}</span>
            </div>
            <div class="article-actions">
              <el-button type="primary" text size="small" @click="goToEdit(article.id)">
                <Icon name="edit" size="sm" /> 编辑
              </el-button>
              <el-button type="danger" text size="small" @click="deleteArticle(article.id)">
                <Icon name="delete" size="sm" /> 删除
              </el-button>
            </div>
          </div>
        </div>
        
        <div v-else class="empty-state">
          <Icon name="article" size="xl" />
          <p>还没有发布过文章</p>
          <el-button type="primary" @click="goToWrite">
            <Icon name="write" size="sm" />
            写第一篇文章
          </el-button>
        </div>
      </el-card>

      <!-- 我的工具 -->
      <el-card class="my-tools">
        <template #header>
          <div class="card-header">
            <h2>我的工具</h2>
            <router-link to="/share-tool" class="share-btn">
              <Icon name="share" size="sm" />
              分享工具
            </router-link>
          </div>
        </template>
        
        <div class="tool-list" v-if="tools.length > 0">
          <div 
            v-for="tool in tools" 
            :key="tool.id" 
            class="tool-item"
          >
            <div class="tool-info">
              <h4 class="tool-name" @click="goToTool(tool.id)">{{ tool.name }}</h4>
              <p class="tool-desc">{{ tool.description?.substring(0, 60) }}...</p>
              <div class="tool-meta">
                <span class="tool-category">{{ tool.category?.name || '其他' }}</span>
                <span class="tool-date">{{ formatDate(tool.created_at) }}</span>
              </div>
            </div>
            <div class="tool-stats">
              <span><Icon name="eye" size="xs" /> {{ tool.view_count || 0 }}</span>
              <span><Icon name="comment" size="xs" /> {{ tool.comment_count || 0 }}</span>
            </div>
            <div class="tool-actions">
              <el-button type="primary" text size="small" @click="goToEditTool(tool.id)">
                <Icon name="edit" size="sm" /> 编辑
              </el-button>
              <el-button type="danger" text size="small" @click="deleteTool(tool.id)">
                <Icon name="delete" size="sm" /> 删除
              </el-button>
            </div>
          </div>
        </div>
        
        <div v-else class="empty-state">
          <Icon name="tool" size="xl" />
          <p>还没有分享过工具</p>
          <el-button type="primary" @click="goToShareTool">
            <Icon name="share" size="sm" />
            分享第一个工具
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from '../utils/axios'
import Icon from '../components/Icon.vue'

const router = useRouter()
const user = ref<any>({})
const articles = ref<any[]>([])
const tools = ref<any[]>([])

// 统计信息
const stats = computed(() => {
  const articleCount = articles.value.length
  const toolCount = tools.value.length
  const totalViews = articles.value.reduce((sum, a) => sum + (a.view_count || 0), 0) +
                     tools.value.reduce((sum, t) => sum + (t.view_count || 0), 0)
  return { articleCount, toolCount, totalViews }
})

const checkLogin = () => {
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录后再访问')
    router.push('/login')
    return false
  }
  return true
}

const loadUserInfo = async () => {
  try {
    const response = await axios.get('/auth/me')
    user.value = response
  } catch (error) {
    console.error('获取用户信息失败:', error)
  }
}

const loadMyArticles = async () => {
  try {
    const response = await axios.get('/articles', { params: { page: 0, size: 100 } })
    // 过滤出当前用户的文章
    articles.value = (response.articles || []).filter((a: any) => a.user?.username === user.value.username)
  } catch (error) {
    console.error('获取我的文章失败:', error)
    articles.value = []
  }
}

const loadMyTools = async () => {
  try {
    const response = await axios.get('/tools', { params: { page: 0, size: 100 } })
    // 过滤出当前用户的工具
    tools.value = (response.tools || []).filter((t: any) => t.user?.username === user.value.username)
  } catch (error) {
    console.error('获取我的工具失败:', error)
    tools.value = []
  }
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' })
}

const goToArticle = (id: number) => {
  router.push(`/article/${id}`)
}

const goToEdit = (id: number) => {
  router.push(`/article/${id}/edit`)
}

const deleteArticle = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这篇文章吗？删除后无法恢复。', '提示', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await axios.delete(`/articles/${id}`)
    ElMessage.success('文章已删除')
    articles.value = articles.value.filter(a => a.id !== id)
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除文章失败')
    }
  }
}

const goToTool = (id: number) => {
  router.push(`/tool/${id}`)
}

const goToEditTool = (id: number) => {
  router.push(`/tool/${id}/edit`)
}

const deleteTool = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这个工具吗？删除后无法恢复。', '提示', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await axios.delete(`/tools/${id}`)
    ElMessage.success('工具已删除')
    tools.value = tools.value.filter(t => t.id !== id)
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除工具失败')
    }
  }
}

const goToWrite = () => {
  router.push('/write')
}

const goToShareTool = () => {
  router.push('/share-tool')
}

onMounted(async () => {
  if (!checkLogin()) return
  await Promise.all([
    loadUserInfo(),
    loadMyArticles(),
    loadMyTools()
  ])
})
</script>

<style scoped>
.profile-view {
  max-width: 1000px;
  margin: 0 auto;
  padding: var(--space-6) var(--space-4);
}

.profile-container {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.profile-card {
  border-radius: var(--border-radius-lg);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h2 {
  margin: 0;
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
}

.write-btn, .share-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  background-color: var(--color-primary-500);
  color: white;
  text-decoration: none;
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-sm);
  transition: background-color var(--transition-fast);
}

.write-btn:hover, .share-btn:hover {
  background-color: var(--color-primary-600);
}

.profile-info {
  padding: var(--space-4) 0;
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: var(--space-6);
}

.avatar {
  width: 80px;
  height: 80px;
  border-radius: var(--border-radius-full);
  background-color: var(--color-bg-tertiary);
  border: 2px solid var(--color-border-default);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-tertiary);
  flex-shrink: 0;
  overflow: hidden;
  background-size: cover;
  background-position: center;
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.username {
  margin: 0;
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.email {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
}

.stat-card {
  text-align: center;
  border-radius: var(--border-radius-lg);
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.stat-number {
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-primary-600);
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.my-articles, .my-tools {
  border-radius: var(--border-radius-lg);
}

.article-list, .tool-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.article-item, .tool-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4);
  background-color: var(--color-bg-tertiary);
  border-radius: var(--border-radius-md);
  transition: background-color var(--transition-fast);
}

.article-item:hover, .tool-item:hover {
  background-color: var(--color-gray-200);
}

.article-info, .tool-info {
  flex: 1;
  min-width: 0;
}

.article-title, .tool-name {
  margin: 0 0 var(--space-2);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  cursor: pointer;
  transition: color var(--transition-fast);
}

.article-title:hover, .tool-name:hover {
  color: var(--color-primary-600);
}

.article-desc, .tool-desc {
  margin: 0 0 var(--space-2);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-meta, .tool-meta {
  display: flex;
  gap: var(--space-4);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.article-stats, .tool-stats {
  display: flex;
  gap: var(--space-4);
  margin: 0 var(--space-4);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.article-actions, .tool-actions {
  display: flex;
  gap: var(--space-2);
  flex-shrink: 0;
}

.empty-state {
  text-align: center;
  padding: var(--space-12);
  color: var(--color-text-tertiary);
}

.empty-state p {
  margin: var(--space-4) 0;
}

@media (max-width: 768px) {
  .profile-view {
    padding: var(--space-4) var(--space-3);
  }
  
  .stats-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: var(--space-2);
  }
  
  .stat-number {
    font-size: var(--font-size-xl);
  }
  
  .article-item, .tool-item {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-3);
  }
  
  .article-stats, .tool-stats {
    margin: 0;
  }
  
  .article-actions, .tool-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
