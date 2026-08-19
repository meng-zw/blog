<template>
  <div class="article-list">
    <div class="page-header">
      <div class="page-header__content">
        <h1 class="page-title">全部文章</h1>
        <p class="page-subtitle">精选技术文章，助力开发者成长</p>
      </div>
    </div>

    <div class="article-grid" v-if="articles.length > 0">
      <el-card
        v-for="(article, index) in articles"
        :key="article.id"
        class="article-card"
        :style="{ animationDelay: `${index * 80}ms` }"
        shadow="hover"
      >
        <div class="article-card__meta">
          <span class="article-card__tag" @click="goHome">
            <Icon name="tag" size="xs" />
            {{ article.category?.name || '技术' }}
          </span>
          <span class="article-card__date">
            <Icon name="calendar" size="xs" />
            {{ formatDate(article.created_at) }}
          </span>
        </div>
        <h3 class="article-card__title" @click="goToArticle(article.id)">
          {{ article.title }}
        </h3>
        <p class="article-card__excerpt">{{ excerpt(article.content) }}</p>
        <div class="article-card__footer">
          <div class="article-card__stats">
            <span>
              <Icon name="eye" size="xs" />
              {{ article.view_count || 0 }}
            </span>
            <span>
              <Icon name="comment" size="xs" />
              {{ article.comment_count || 0 }}
            </span>
          </div>
          <el-button type="primary" size="small" @click="goToArticle(article.id)">
            阅读全文
            <Icon name="arrow-right" size="xs" />
          </el-button>
        </div>
      </el-card>
    </div>

    <div v-if="!loading && articles.length === 0" class="empty-state">
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

    <div class="pagination-wrapper" v-if="total > size">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadArticles"
      />
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
const page = ref(1)
const size = ref(9)
const total = ref(0)
const loading = ref(false)

const goToArticle = (id: number) => {
  router.push(`/article/${id}`)
}

const goToWrite = () => {
  router.push('/write')
}

const goHome = () => {
  router.push('/')
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  if (isNaN(date.getTime())) return dateStr
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}

const excerpt = (content: string) => {
  if (!content) return ''
  return content.length > 120 ? `${content.substring(0, 120)}...` : content
}

const loadArticles = async () => {
  loading.value = true
  try {
    const res = await axios.get('/articles', {
      params: { page: page.value - 1, size: size.value }
    })
    articles.value = res.articles || []
    total.value = res.total || 0
  } catch (error) {
    console.error('获取文章列表失败:', error)
    articles.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadArticles()
})
</script>

<style scoped>
.article-list {
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

/* 两列等宽卡片（参考图版式） */
.article-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-6);
}

.article-card {
  border-radius: var(--border-radius-lg);
  animation: none;
  border: 1px solid var(--color-border-light);
  transition: border-color var(--transition-normal);
  overflow: hidden;
}

.article-card:hover {
  border-color: var(--color-border-default);
}

.article-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 0 !important;
}

/* 卡片顶部暖色封面区 */
.article-card :deep(.el-card__body)::before {
  content: "";
  display: block;
  height: 150px;
  flex-shrink: 0;
  background: linear-gradient(
    135deg,
    var(--color-primary-100) 0%,
    var(--color-primary-200) 55%,
    var(--color-accent-100) 100%
  );
  border-bottom: 1px solid var(--color-border-light);
}

.article-card__meta,
.article-card__title,
.article-card__excerpt,
.article-card__footer {
  margin-left: var(--space-5);
  margin-right: var(--space-5);
}

.article-card__meta,
.article-card__title {
  margin-top: var(--space-4);
}

.article-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

/* 橙色圆角分类标签 */
.article-card__tag {
  display: inline-flex;
  align-items: center;
  padding: 2px var(--space-3);
  background-color: var(--color-accent-400);
  color: #ffffff;
  border-radius: var(--border-radius-full);
  font-size: var(--font-size-xs);
  font-family: var(--font-family-ui);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  transition: background-color var(--transition-fast);
}

.article-card__tag :deep(svg) {
  display: none;
}

.article-card__tag:hover {
  background-color: var(--color-accent-500);
  color: #ffffff;
}

.article-card__date {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.article-card__title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--space-2);
  cursor: pointer;
  transition: color var(--transition-fast);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-card__title:hover {
  color: var(--color-primary-600);
}

.article-card__excerpt {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: var(--line-height-relaxed);
  margin-bottom: var(--space-4);
  flex: 1;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding: var(--space-3) 0 var(--space-4);
  border-top: 1px solid var(--color-border-light);
}

.article-card__stats {
  display: flex;
  gap: var(--space-4);
}

.article-card__stats span {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
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

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: var(--space-8);
}

@media (max-width: 768px) {
  .page-header {
    padding: var(--space-6) var(--space-4);
  }

  .page-title {
    font-size: var(--font-size-lg);
  }

  .article-grid {
    grid-template-columns: 1fr;
  }
}
</style>
