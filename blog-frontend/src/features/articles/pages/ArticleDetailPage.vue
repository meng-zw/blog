<template>
  <div class="public-page public-container">
    <PublicLoading v-if="loading" label="正在加载文章" />
    <section v-else-if="error?.status === 404" class="not-found-state">
      <p class="eyebrow">404</p><h1>文章未找到</h1><p>这篇内容可能已移动或不再公开。</p><RouterLink to="/articles">返回文章列表</RouterLink>
    </section>
    <AppError v-else-if="error" :title="error.title" :detail="errorDetail" retry-label="重新加载" @retry="requestArticle" />
    <article v-else-if="article" class="article-detail">
      <header class="article-detail__header">
        <p class="story-meta">{{ article.contentType === 'NOTE' ? '随笔' : '文章' }}<template v-if="article.category"> · {{ article.category.name }}</template></p>
        <h1>{{ article.title }}</h1>
        <p class="article-detail__summary">{{ article.summary }}</p>
        <time :datetime="article.publishedAt">{{ formatPublicDate(article.publishedAt) }}</time>
        <ul v-if="article.tags.length" class="tag-list" aria-label="标签"><li v-for="tag in article.tags" :key="tag.id">{{ tag.name }}</li></ul>
        <RouterLink v-if="article.topic" class="topic-link" :to="`/topics/${article.topic.slug}`">专题：{{ article.topic.name }}</RouterLink>
      </header>
      <img v-if="article.coverUrl" class="article-detail__cover" :src="article.coverUrl" :alt="`${article.title}封面`" width="1280" height="720">
      <div class="article-detail__grid">
        <ArticleToc :items="prepared.toc" />
        <SafeRichContent :html="prepared.html" />
      </div>
      <section v-if="article.attachments.length" class="article-attachments" aria-label="文章附件"><h2>文章附件</h2><ul><li v-for="attachment in article.attachments" :key="attachment.mediaId"><div><strong>{{ attachment.displayName }}</strong><small>{{ formatFileSize(attachment.byteSize) }} · {{ attachment.contentType }}</small></div><a :href="attachment.downloadUrl" :download="attachment.displayName" :aria-label="`下载附件：${attachment.displayName}`">下载</a></li></ul></section>
      <nav v-if="article.previous || article.next" class="adjacent-nav" aria-label="相邻文章">
        <RouterLink v-if="article.previous" :to="`/articles/${article.previous.slug}`"><span>上一篇</span>{{ article.previous.title }}</RouterLink>
        <span v-else></span>
        <RouterLink v-if="article.next" :to="`/articles/${article.next.slug}`"><span>下一篇</span>{{ article.next.title }}</RouterLink>
      </nav>
    </article>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import type { ArticleDetailResponse } from '../../../shared/api/contracts'
import { formatPublicDate } from '../../../shared/lib/date'
import { prepareRichContent } from '../../../shared/lib/rich-content'
import { usePublicRequest } from '../../../shared/lib/public-request'
import { useSeo } from '../../../shared/lib/seo'
import AppError from '../../../shared/ui/AppError.vue'
import PublicLoading from '../../../shared/ui/PublicLoading.vue'
import SafeRichContent from '../../../shared/ui/SafeRichContent.vue'
import ArticleToc from '../components/ArticleToc.vue'
import { loadArticle } from '../api'

const route = useRoute()
const request = usePublicRequest<ArticleDetailResponse>()
const { data: article, loading, error } = request
const slug = computed(() => String(route.params.slug ?? ''))
const prepared = computed(() => prepareRichContent(article.value?.renderedHtml ?? ''))
const errorDetail = computed(() => error.value?.traceId ? `${error.value.detail}（追踪编号：${error.value.traceId}）` : error.value?.detail)

function requestArticle(): Promise<void> {
  article.value = null
  return request.run((signal) => loadArticle(slug.value, signal))
}

function formatFileSize(byteSize: number): string {
  if (byteSize < 1024) return `${byteSize} B`
  if (byteSize < 1024 * 1024) return `${(byteSize / 1024).toFixed(1)} KiB`
  return `${(byteSize / (1024 * 1024)).toFixed(1)} MiB`
}

useSeo(() => {
  const data = article.value
  if (!data) return { title: '文章 · 小M的思与行', description: '阅读小M的文章。', path: `/articles/${encodeURIComponent(slug.value)}` }
  const title = data.seoTitle || `${data.title} · 小M的思与行`
  const description = data.seoDescription || data.summary
  return {
    title, description, path: `/articles/${encodeURIComponent(data.slug)}`, type: 'article', image: data.coverUrl,
    article: { headline: data.title, description, datePublished: data.publishedAt, image: data.coverUrl }
  }
})

watch(slug, () => { void requestArticle() }, { immediate: true })
</script>
