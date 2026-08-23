<template>
  <article class="article-card">
    <img v-if="article.coverUrl" :src="article.coverUrl" :alt="`${article.title}封面`" width="640" height="360" loading="lazy">
    <div class="article-card__body">
      <p class="story-meta">{{ typeLabel }}<template v-if="article.category"> · {{ article.category.name }}</template></p>
      <h2><RouterLink :to="`/articles/${article.slug}`">{{ article.title }}</RouterLink></h2>
      <p>{{ article.summary }}</p>
      <ul v-if="article.tags.length" class="tag-list" aria-label="标签">
        <li v-for="tag in article.tags" :key="tag.id">{{ tag.name }}</li>
      </ul>
      <time :datetime="article.publishedAt">{{ formatPublicDate(article.publishedAt) }}</time>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import type { ArticleSummaryResponse } from '../../../shared/api/contracts'
import { formatPublicDate } from '../../../shared/lib/date'

const props = defineProps<{ article: ArticleSummaryResponse }>()
const typeLabel = computed(() => props.article.contentType === 'NOTE' ? '随笔' : '文章')
</script>
