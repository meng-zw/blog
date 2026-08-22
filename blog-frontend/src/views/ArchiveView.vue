<template>
  <div class="archive-view">
    <div class="page-header">
      <h1 class="page-title">文章归档</h1>
      <p class="page-subtitle">按月浏览全部已发布文章</p>
    </div>

    <div v-if="loading" class="loading">
      <el-skeleton :rows="8" animated />
    </div>

    <div v-else-if="archive.length > 0" class="archive-list">
      <section
        v-for="group in archive"
        :key="group.month"
        class="archive-group"
      >
        <h2 class="archive-group__month">
          {{ formatMonth(group.month) }}
          <span class="archive-group__count">{{ group.articles.length }} 篇</span>
        </h2>
        <div class="archive-group__items">
          <div
            v-for="article in group.articles"
            :key="article.id"
            class="archive-item"
            @click="goToArticle(article.id)"
          >
            <span class="archive-item__date">{{ formatDay(article.time) }}</span>
            <span class="archive-item__title">{{ article.title }}</span>
            <span class="archive-item__category">{{ article.category || '技术' }}</span>
            <span class="archive-item__stats">
              <Icon name="eye" size="xs" /> {{ article.view_count || 0 }}
              <Icon name="comment" size="xs" /> {{ article.comment_count || 0 }}
            </span>
          </div>
        </div>
      </section>
    </div>

    <div v-else class="empty-state">
      <div class="empty-state__icon">
        <Icon name="archive" size="xl" />
      </div>
      <h3 class="empty-state__title">暂无归档</h3>
      <p class="empty-state__description">发布第一篇文章后，这里将按月展示</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from '../utils/axios'
import Icon from '../components/Icon.vue'

const router = useRouter()
const archive = ref<any[]>([])
const loading = ref(false)

const goToArticle = (id: number) => {
  router.push(`/article/${id}`)
}

const formatMonth = (month: string) => {
  const [year, m] = month.split('-')
  return `${year} 年 ${Number(m)} 月`
}

const formatDay = (time: string) => {
  if (!time) return ''
  const date = new Date(time)
  if (isNaN(date.getTime())) return time
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

const loadArchive = async () => {
  loading.value = true
  try {
    archive.value = await axios.get('/articles/archive')
  } catch (error) {
    console.error('加载归档失败:', error)
    archive.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadArchive()
})
</script>

<style scoped>
.archive-view {
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

.loading {
  margin-bottom: 20px;
}

.archive-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-8);
}

.archive-group__month {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  font-family: var(--font-family-display);
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  letter-spacing: var(--letter-spacing-wide);
  margin: 0 0 var(--space-4);
  padding-bottom: var(--space-2);
  border-bottom: 2px solid var(--color-border-default);
}

.archive-group__count {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-normal);
  color: var(--color-text-tertiary);
}

.archive-group__items {
  display: flex;
  flex-direction: column;
}

.archive-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--border-radius-md);
  cursor: pointer;
  transition: background-color var(--transition-fast);
}

.archive-item:hover {
  background-color: var(--color-bg-tertiary);
}

.archive-item__date {
  flex-shrink: 0;
  width: 48px;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.archive-item__title {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color var(--transition-fast);
}

.archive-item:hover .archive-item__title {
  color: var(--color-primary-600);
}

.archive-item__category {
  flex-shrink: 0;
  padding: 2px var(--space-3);
  background-color: var(--color-accent-400);
  color: #ffffff;
  border-radius: var(--border-radius-full);
  font-size: var(--font-size-xs);
}

.archive-item__stats {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.archive-item__stats .el-icon {
  margin-left: var(--space-2);
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
  margin: 0;
}

@media (max-width: 768px) {
  .page-header {
    padding: var(--space-6) var(--space-4);
  }

  .page-title {
    font-size: var(--font-size-lg);
  }

  .archive-item {
    flex-wrap: wrap;
    gap: var(--space-2);
  }

  .archive-item__stats {
    margin-left: auto;
  }
}
</style>
