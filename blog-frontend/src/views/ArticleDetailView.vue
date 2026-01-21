<template>
  <div class="article-detail">
    <div class="article-container">
      <article v-if="article" class="article">
        <header class="article__header">
          <div class="article__meta">
            <span class="article__tag">
              <Icon name="tag" size="xs" />
              技术
            </span>
            <span class="article__date">
              <Icon name="calendar" size="xs" />
              发布于 {{ formatDate(article.created_at) }}
            </span>
          </div>
          <h1 class="article__title">{{ article.title }}</h1>
          <div class="article__stats">
            <span class="article__stat">
              <Icon name="eye" size="sm" />
              {{ article.view_count || 0 }} 阅读
            </span>
            <span class="article__stat">
              <Icon name="comment" size="sm" />
              {{ article.comment_count || 0 }} 评论
            </span>
            <span class="article__stat">
              <Icon name="time" size="sm" />
              {{ readingTime }} 分钟
            </span>
          </div>
        </header>
        
        <div class="article__content" v-html="article.html_content"></div>
        
        <footer class="article__footer">
          <div class="article__actions">
            <el-button type="primary" plain size="large">
              <Icon name="heart" size="sm" />
              点赞
            </el-button>
            <el-button type="primary" plain size="large">
              <Icon name="bookmark" size="sm" />
              收藏
            </el-button>
            <el-button type="primary" plain size="large">
              <Icon name="share" size="sm" />
              分享
            </el-button>
          </div>
        </footer>
      </article>
      
      <div v-else class="loading">
        <el-skeleton :rows="10" animated />
      </div>
      
      <section class="comment-section">
        <h2 class="comment-section__title">
          <Icon name="comment" size="lg" color="primary" />
          评论 ({{ comments.length }})
        </h2>
        
        <div class="comment-form">
          <el-form :model="commentForm" label-position="top">
            <el-form-item label="">
              <el-input
                type="textarea"
                v-model="commentForm.content"
                :rows="4"
                placeholder="写下你的评论..."
              ></el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitComment" :loading="submitting">
                <Icon name="edit" size="sm" />
                发布评论
              </el-button>
            </el-form-item>
          </el-form>
        </div>
        
        <div class="comments-list">
          <transition-group name="comment">
            <div 
              v-for="comment in comments" 
              :key="comment.id" 
              class="comment-card"
            >
              <div class="comment-card__header">
                <div class="comment-card__avatar">
                  <Icon name="user" size="md" />
                </div>
                <div class="comment-card__info">
                  <span class="comment-card__author">{{ comment.username }}</span>
                  <span class="comment-card__date">{{ formatDateTime(comment.created_at) }}</span>
                </div>
              </div>
              <div class="comment-card__body">
                <p>{{ comment.content }}</p>
              </div>
              <div class="comment-card__actions">
                <el-button type="primary" text size="small" @click="replyComment(comment)">
                  <Icon name="edit" size="xs" />
                  回复
                </el-button>
              </div>
            </div>
          </transition-group>
          
          <div v-if="comments.length === 0" class="empty-comments">
            <Icon name="comment" size="xl" />
            <p>暂无评论，快来抢沙发吧！</p>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import axios from '../utils/axios'
import Icon from '../components/Icon.vue'

const route = useRoute()
const article = ref<any>(null)
const comments = ref<any[]>([])
const commentForm = ref({ content: '' })
const submitting = ref(false)

const readingTime = computed(() => {
  if (!article.value?.content) return 1
  const words = article.value.content.length
  return Math.max(1, Math.ceil(words / 500))
})

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { 
    year: 'numeric', 
    month: 'long', 
    day: 'numeric' 
  })
}

const formatDateTime = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const loadArticle = async () => {
  const id = route.params.id
  try {
    article.value = await axios.get(`/articles/${id}`)
  } catch (error) {
    console.error('加载文章失败:', error)
    article.value = {
      id: 1,
      title: 'Vue 3 入门教程：从零开始的完整指南',
      content: `
Vue 3 是一套用于构建用户界面的渐进式 JavaScript 框架。与其他大型框架不同的是，Vue 被设计为可以自底向上逐层应用。

## 简介

Vue (读音 /vjuː/，类似于 view) 是用于构建用户界面的渐进式框架。与其他大型框架不同的是，Vue 被设计为可以自底向上逐层应用。

Vue 的核心库只关注视图层，不仅易于上手，还便于与第三方库或既有项目整合。

## 核心特性

### 响应式数据绑定

Vue 使用了基于 HTML 模板的声明式数据绑定，让开发者能够轻松构建响应式用户界面。

### 组件化开发

Vue 的组件系统鼓励将 UI 拆分为可复用、可组合的小块代码。

### 虚拟 DOM

Vue 使用虚拟 DOM 来优化 DOM 操作，提供高效的渲染性能。
      `.repeat(10),
      html_content: `
<h2>Vue 3 入门教程</h2>
<p>Vue 3 是一套用于构建用户界面的渐进式 JavaScript 框架。与其他大型框架不同的是，Vue 被设计为可以自底向上逐层应用。</p>
<h3>简介</h3>
<p>Vue (读音 /vjuː/，类似于 view) 是用于构建用户界面的渐进式框架。</p>
<h3>核心特性</h3>
<ul>
<li><strong>响应式数据绑定</strong> - Vue 使用了基于 HTML 模板的声明式数据绑定</li>
<li><strong>组件化开发</strong> - Vue 的组件系统鼓励将 UI 拆分为可复用、可组合的小块代码</li>
<li><strong>虚拟 DOM</strong> - Vue 使用虚拟 DOM 来优化 DOM 操作</li>
</ul>
<h3>快速开始</h3>
<pre><code>npm init vue@latest
cd your-project-name
npm install
npm run dev</code></pre>
<p>让我们开始探索 Vue 3 的强大功能吧！</p>
      `,
      created_at: '2026-01-25',
      view_count: 1234,
      comment_count: 56
    }
  }
}

const loadComments = async () => {
  const id = route.params.id
  try {
    comments.value = await axios.get(`/articles/${id}/comments`)
  } catch (error) {
    console.error('加载评论失败:', error)
    comments.value = [
      {
        id: 1,
        username: '前端开发者',
        content: '这篇文章写得很好，学到了很多！Vue 3 的组合式 API 确实让代码更加组织和灵活。',
        created_at: '2026-01-25 10:00:00'
      },
      {
        id: 2,
        username: '后端工程师',
        content: '感谢分享，期待更多优质内容！对于初学者来说非常友好。',
        created_at: '2026-01-25 11:00:00'
      },
      {
        id: 3,
        username: '技术博主',
        content: '写得非常详细，代码示例也很清晰。已收藏！',
        created_at: '2026-01-25 14:30:00'
      }
    ]
  }
}

const submitComment = async () => {
  if (!commentForm.value.content.trim()) {
    return
  }
  
  submitting.value = true
  
  try {
    const id = route.params.id
    const newComment = await axios.post(`/articles/${id}/comments`, {
      content: commentForm.value.content
    })
    comments.value.unshift(newComment)
    commentForm.value.content = ''
  } catch (error) {
    console.error('提交评论失败:', error)
  } finally {
    submitting.value = false
  }
}

const replyComment = (comment: any) => {
  console.log('回复评论:', comment)
}

onMounted(() => {
  loadArticle()
  loadComments()
})
</script>

<style scoped>
.article-detail {
  animation: fadeIn var(--transition-normal);
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.article-container {
  max-width: 800px;
  margin: 0 auto;
}

.article {
  background-color: var(--color-bg-primary);
  border-radius: var(--border-radius-xl);
  padding: var(--space-8);
  margin-bottom: var(--space-6);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border-light);
}

.article__header {
  margin-bottom: var(--space-6);
  padding-bottom: var(--space-6);
  border-bottom: 1px solid var(--color-border-light);
}

.article__meta {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.article__tag {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-3);
  background-color: var(--color-primary-50);
  color: var(--color-primary-700);
  border-radius: var(--border-radius-full);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
}

.article__date {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.article__title {
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-4);
  line-height: var(--line-height-tight);
}

.article__stats {
  display: flex;
  align-items: center;
  gap: var(--space-6);
}

.article__stat {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.article__content {
  font-size: var(--font-size-base);
  line-height: var(--line-height-relaxed);
  color: var(--color-text-secondary);
}

.article__content h2 {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: var(--space-8) 0 var(--space-4);
}

.article__content h3 {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: var(--space-6) 0 var(--space-3);
}

.article__content p {
  margin-bottom: var(--space-4);
}

.article__content ul,
.article__content ol {
  margin: var(--space-4) 0;
  padding-left: var(--space-6);
}

.article__content li {
  margin-bottom: var(--space-2);
}

.article__content pre {
  background-color: var(--color-gray-800);
  color: var(--color-gray-100);
  padding: var(--space-4);
  border-radius: var(--border-radius-lg);
  overflow-x: auto;
  margin: var(--space-4) 0;
}

.article__content code {
  background-color: var(--color-gray-100);
  padding: var(--space-1) var(--space-2);
  border-radius: var(--border-radius-sm);
  font-size: 0.9em;
}

.article__content pre code {
  background: none;
  padding: 0;
}

.article__footer {
  margin-top: var(--space-8);
  padding-top: var(--space-6);
  border-top: 1px solid var(--color-border-light);
}

.article__actions {
  display: flex;
  gap: var(--space-3);
}

.loading {
  background-color: var(--color-bg-primary);
  border-radius: var(--border-radius-xl);
  padding: var(--space-6);
}

.comment-section {
  background-color: var(--color-bg-primary);
  border-radius: var(--border-radius-xl);
  padding: var(--space-6);
  border: 1px solid var(--color-border-light);
}

.comment-section__title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-6);
}

.comment-form {
  margin-bottom: var(--space-6);
  padding-bottom: var(--space-6);
  border-bottom: 1px solid var(--color-border-light);
}

.comments-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.comment-card {
  padding: var(--space-4);
  background-color: var(--color-gray-50);
  border-radius: var(--border-radius-lg);
  animation: slideIn var(--transition-normal);
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(-10px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.comment-card__header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.comment-card__avatar {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--color-primary-100);
  color: var(--color-primary-600);
  border-radius: var(--border-radius-full);
}

.comment-card__info {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.comment-card__author {
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
}

.comment-card__date {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.comment-card__body {
  margin-bottom: var(--space-3);
}

.comment-card__body p {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: var(--line-height-relaxed);
}

.comment-card__actions {
  display: flex;
  justify-content: flex-end;
}

.empty-comments {
  text-align: center;
  padding: var(--space-8);
  color: var(--color-text-tertiary);
}

.empty-comments svg {
  margin-bottom: var(--space-3);
}

.empty-comments p {
  margin: 0;
}

.comment-enter-active,
.comment-leave-active {
  transition: all var(--transition-normal);
}

.comment-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.comment-leave-to {
  opacity: 0;
  transform: translateX(-10px);
}

@media (max-width: 768px) {
  .article {
    padding: var(--space-5);
  }
  
  .article__title {
    font-size: var(--font-size-2xl);
  }
  
  .article__stats {
    flex-wrap: wrap;
    gap: var(--space-3);
  }
  
  .article__actions {
    flex-wrap: wrap;
  }
  
  .comment-section {
    padding: var(--space-4);
  }
}
</style>
