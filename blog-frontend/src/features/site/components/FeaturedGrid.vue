<template>
  <section class="editorial-lead-grid" aria-label="首页文章">
    <div class="feature-column">
      <div class="section-heading">
        <p class="eyebrow">编辑选读</p>
        <h2>本周精选</h2>
      </div>
      <article v-if="featured" class="featured-story">
        <img v-if="featured.coverUrl" :src="featured.coverUrl" :alt="featured.title" loading="lazy">
        <p v-if="featured.category" class="story-meta">{{ featured.category.name }}</p>
        <h3><a :href="`/articles/${featured.slug}`">{{ featured.title }}</a></h3>
        <p>{{ featured.summary }}</p>
        <a class="text-link" :href="`/articles/${featured.slug}`">阅读文章 <span aria-hidden="true">→</span></a>
        <time :datetime="featured.publishedAt">{{ formatDate(featured.publishedAt) }}</time>
      </article>
      <div v-else class="content-empty" role="status">
        <p>还没有公开文章</p>
        <span>新的思考正在酽酿中。</span>
      </div>
    </div>

    <div class="latest-column">
      <div class="section-heading section-heading--row">
        <h2>最新文章</h2>
        <a v-if="articles.length" href="/articles">查看全部 <span aria-hidden="true">→</span></a>
      </div>
      <div v-if="articles.length" class="latest-grid">
        <article v-for="article in articles" :key="article.id" class="story-card">
          <img v-if="article.coverUrl" :src="article.coverUrl" :alt="article.title" loading="lazy">
          <div class="story-card__body">
            <p class="story-meta">{{ article.category?.name ?? (article.contentType === 'NOTE' ? '随笔' : '文章') }}</p>
            <h3><a :href="`/articles/${article.slug}`">{{ article.title }}</a></h3>
            <p>{{ article.summary }}</p>
            <time :datetime="article.publishedAt">{{ formatDate(article.publishedAt) }}</time>
          </div>
        </article>
      </div>
      <div v-else class="content-empty content-empty--compact" role="status">
        <p>暂无更多文章</p>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { ArticleSummaryResponse } from '../../../shared/api/contracts'

defineProps<{
  featured: ArticleSummaryResponse | null
  articles: ArticleSummaryResponse[]
}>()

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric', month: '2-digit', day: '2-digit'
})

function formatDate(value: string): string {
  return dateFormatter.format(new Date(value)).replaceAll('/', ' / ')
}
</script>
