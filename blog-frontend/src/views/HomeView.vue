<template>
  <div class="home-view">
    <section class="hero-section">
      <div class="hero__content">
        <h1 class="hero__title">探索 · 分享 · 成长</h1>
        <p class="hero__subtitle">在这里，记录技术足迹，分享实用工具，与志同道合的开发者一起进步</p>
        <div class="hero__actions">
          <el-button type="primary" size="large" @click="goToWrite">
            <Icon name="write" size="sm" />
            写文章
          </el-button>
          <el-button size="large" @click="goToTools">
            <Icon name="tool" size="sm" />
            发现工具
          </el-button>
        </div>
      </div>
    </section>

    <div class="content-wrapper">
      <section class="article-section">
        <div class="section-header">
          <div class="section-header__left">
            <Icon name="article" size="lg" color="primary" />
            <h2 class="section-title">最新文章</h2>
          </div>
          <router-link to="/article" class="section-link">
            查看更多
            <Icon name="arrow-right" size="sm" />
          </router-link>
        </div>
        
        <div class="article-grid">
          <el-card 
            v-for="(article, index) in articles" 
            :key="article.id" 
            class="article-card"
            :style="{ animationDelay: `${index * 100}ms` }"
            shadow="hover"
          >
            <div class="article-card__body">
              <div class="article-card__meta">
                <span class="article-card__tag">
                  <Icon name="tag" size="xs" />
                  技术
                </span>
                <span class="article-card__date">
                  <Icon name="calendar" size="xs" />
                  {{ formatDate(article.created_at) }}
                </span>
              </div>
              <h3 class="article-card__title" @click="goToArticle(article.id)">
                {{ article.title }}
              </h3>
              <p class="article-card__excerpt">{{ article.content.substring(0, 120) }}...</p>
              <div class="article-card__footer">
                <div class="article-card__stats">
                  <span>
                    <Icon name="eye" size="xs" />
                    {{ article.view_count || Math.floor(Math.random() * 1000) }}
                  </span>
                  <span>
                    <Icon name="comment" size="xs" />
                    {{ article.comment_count || Math.floor(Math.random() * 50) }}
                  </span>
                </div>
                <el-button type="primary" size="small" @click="goToArticle(article.id)">
                  阅读全文
                  <Icon name="arrow-right" size="xs" />
                </el-button>
              </div>
            </div>
          </el-card>
        </div>
        
        <div v-if="articles.length === 0" class="empty-state">
          <div class="empty-state__icon">
            <Icon name="article" size="xl" />
          </div>
          <h3 class="empty-state__title">暂无文章</h3>
          <p class="empty-state__description">成为第一个分享知识的人吧！</p>
          <el-button type="primary" @click="goToWrite">
            <Icon name="write" size="sm" />
            写第一篇文章
          </el-button>
        </div>
      </section>
      
      <aside class="sidebar">
        <section class="sidebar__section">
          <div class="section-header">
            <div class="section-header__left">
              <Icon name="tool" size="md" color="success" />
              <h3 class="section-title">热门工具</h3>
            </div>
          </div>
          
          <div class="tool-list">
            <div 
              v-for="(tool, index) in tools" 
              :key="tool.id" 
              class="tool-item"
              :style="{ animationDelay: `${index * 50}ms` }"
            >
              <div class="tool-item__icon">
                <Icon name="tool" size="md" />
              </div>
              <div class="tool-item__content">
                <h4 class="tool-item__name" @click="goToTool(tool.id)">{{ tool.name }}</h4>
                <p class="tool-item__desc">{{ tool.description.substring(0, 40) }}...</p>
              </div>
              <el-button type="success" size="small" text @click="goToTool(tool.id)">
                <Icon name="arrow-right" size="sm" />
              </el-button>
            </div>
          </div>
          
          <router-link to="/tool" class="sidebar__link">
            查看更多工具
            <Icon name="arrow-right" size="sm" />
          </router-link>
        </section>
        
        <section class="sidebar__section sidebar__section--highlight">
          <div class="sidebar__card">
            <Icon name="write" size="xl" color="primary" />
            <h3>分享你的工具</h3>
            <p>发现有趣的实用工具？分享给更多开发者吧！</p>
            <el-button type="primary" size="small" @click="goToShareTool">
              分享工具
            </el-button>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from '../utils/axios'
import Icon from '../components/Icon.vue'

const router = useRouter()
const articles = ref<any[]>([])
const tools = ref<any[]>([])

const goToArticle = (id: number) => {
  router.push(`/article/${id}`)
}

const goToTool = (id: number) => {
  router.push(`/tool/${id}`)
}

const goToWrite = () => {
  router.push('/write')
}

const goToTools = () => {
  router.push('/tool')
}

const goToShareTool = () => {
  router.push('/share-tool')
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

onMounted(async () => {
  try {
    const articleResponse = await axios.get('/articles/latest')
    articles.value = articleResponse
    
    const toolResponse = await axios.get('/tools/popular')
    tools.value = toolResponse
  } catch (error) {
    console.error('获取数据失败:', error)
    articles.value = [
      {
        id: 1,
        title: 'Vue 3 入门教程：从零开始的完整指南',
        content: 'Vue 3 是一套用于构建用户界面的渐进式 JavaScript 框架。与其他大型框架不同的是，Vue 被设计为可以自底向上逐层应用。Vue 的核心库只关注视图层，不仅易于上手，还便于与第三方库或既有项目整合。',
        created_at: '2026-01-25',
        view_count: 1234,
        comment_count: 56
      },
      {
        id: 2,
        title: 'Spring Boot 4 新特性详解',
        content: 'Spring Boot 4 带来了许多新特性，包括对 JDK 25 的支持、改进的性能和安全性等。本文将详细介绍这些新特性及其在实际开发中的应用。',
        created_at: '2026-01-24',
        view_count: 892,
        comment_count: 34
      },
      {
        id: 3,
        title: 'TypeScript 最佳实践指南',
        content: 'TypeScript 作为 JavaScript 的超集，提供了静态类型检查和面向对象特性。本文分享一些在实际项目中使用 TypeScript 的最佳实践。',
        created_at: '2026-01-23',
        view_count: 756,
        comment_count: 28
      }
    ]
    
    tools.value = [
      {
        id: 1,
        name: 'Markdown 编辑器',
        description: '一个功能强大的在线 Markdown 编辑器，支持实时预览和多种主题切换。',
        created_at: '2026-01-25'
      },
      {
        id: 2,
        name: '在线图片压缩工具',
        description: '免费的在线图片压缩工具，支持多种格式。',
        created_at: '2026-01-24'
      },
      {
        id: 3,
        name: 'JSON 格式化器',
        description: '快速格式化、验证和压缩 JSON 数据。',
        created_at: '2026-01-23'
      }
    ]
  }
})
</script>

<style scoped>
.home-view {
  animation: fadeIn var(--transition-normal);
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.hero-section {
  background: linear-gradient(135deg, var(--color-primary-50) 0%, var(--color-bg-primary) 100%);
  border-radius: var(--border-radius-2xl);
  padding: var(--space-10) var(--space-8);
  margin-bottom: var(--space-8);
  text-align: center;
}

.hero__title {
  font-size: var(--font-size-4xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-3);
  letter-spacing: var(--letter-spacing-tight);
}

.hero__subtitle {
  font-size: var(--font-size-lg);
  color: var(--color-text-secondary);
  max-width: 600px;
  margin: 0 auto var(--space-6);
  line-height: var(--line-height-relaxed);
}

.hero__actions {
  display: flex;
  justify-content: center;
  gap: var(--space-4);
}

.content-wrapper {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: var(--space-6);
  align-items: start;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-5);
}

.section-header__left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.section-title {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0;
}

.section-link {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-primary-600);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  text-decoration: none;
  transition: gap var(--transition-fast);
}

.section-link:hover {
  gap: var(--space-2);
}

.article-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-5);
}

.article-card {
  border-radius: var(--border-radius-xl);
  overflow: hidden;
  animation: slideUp var(--transition-normal) ease-out both;
  border: 1px solid var(--color-border-light);
  transition: transform var(--transition-normal), box-shadow var(--transition-normal);
}

.article-card:hover {
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

.article-card__body {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.article-card__meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.article-card__tag {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-2);
  background-color: var(--color-primary-50);
  color: var(--color-primary-700);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
}

.article-card__date {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.article-card__title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2);
  line-height: var(--line-height-tight);
  cursor: pointer;
  transition: color var(--transition-fast);
}

.article-card__title:hover {
  color: var(--color-primary-600);
}

.article-card__excerpt {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: var(--line-height-relaxed);
  margin: 0 0 var(--space-4);
  flex: 1;
}

.article-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-border-light);
}

.article-card__stats {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.article-card__stats span {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.empty-state {
  text-align: center;
  padding: var(--space-12);
  background-color: var(--color-bg-primary);
  border-radius: var(--border-radius-xl);
  border: 2px dashed var(--color-border-light);
}

.empty-state__icon {
  width: 80px;
  height: 80px;
  margin: 0 auto var(--space-4);
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--color-gray-100);
  border-radius: var(--border-radius-full);
  color: var(--color-text-tertiary);
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

.sidebar {
  position: sticky;
  top: 80px;
}

.sidebar__section {
  background-color: var(--color-bg-primary);
  border-radius: var(--border-radius-xl);
  padding: var(--space-5);
  margin-bottom: var(--space-5);
  border: 1px solid var(--color-border-light);
}

.sidebar__section--highlight {
  background: linear-gradient(135deg, var(--color-primary-50) 0%, var(--color-bg-primary) 100%);
  border-color: var(--color-primary-200);
}

.sidebar__card {
  text-align: center;
  padding: var(--space-4);
}

.sidebar__card h3 {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: var(--space-3) 0 var(--space-2);
}

.sidebar__card p {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin: 0 0 var(--space-4);
}

.tool-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.tool-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  background-color: var(--color-gray-50);
  border-radius: var(--border-radius-lg);
  animation: fadeIn var(--transition-normal) ease-out both;
  transition: background-color var(--transition-fast);
}

.tool-item:hover {
  background-color: var(--color-primary-50);
}

.tool-item__icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--color-success-50);
  color: var(--color-success-500);
  border-radius: var(--border-radius-lg);
  flex-shrink: 0;
}

.tool-item__content {
  flex: 1;
  min-width: 0;
}

.tool-item__name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-1);
  cursor: pointer;
  transition: color var(--transition-fast);
}

.tool-item__name:hover {
  color: var(--color-primary-600);
}

.tool-item__desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar__link {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  padding: var(--space-3);
  margin-top: var(--space-3);
  color: var(--color-primary-600);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  text-decoration: none;
  border-radius: var(--border-radius-lg);
  transition: all var(--transition-fast);
}

.sidebar__link:hover {
  background-color: var(--color-primary-50);
}

@media (max-width: 1024px) {
  .content-wrapper {
    grid-template-columns: 1fr;
  }
  
  .sidebar {
    position: static;
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: var(--space-5);
  }
  
  .sidebar__section {
    margin-bottom: 0;
  }
}

@media (max-width: 768px) {
  .hero-section {
    padding: var(--space-6) var(--space-4);
  }
  
  .hero__title {
    font-size: var(--font-size-2xl);
  }
  
  .hero__subtitle {
    font-size: var(--font-size-base);
  }
  
  .hero__actions {
    flex-direction: column;
    gap: var(--space-3);
  }
  
  .article-grid {
    grid-template-columns: 1fr;
  }
  
  .sidebar {
    grid-template-columns: 1fr;
  }
}
</style>
