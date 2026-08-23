<template>
  <div class="public-page public-container">
    <PublicLoading v-if="loading" label="正在加载专题" />
    <section v-else-if="error?.status === 404" class="not-found-state"><p class="eyebrow">404</p><h1>专题未找到</h1><RouterLink to="/topics">返回专题列表</RouterLink></section>
    <AppError v-else-if="error" :title="error.title" :detail="errorDetail" retry-label="重新加载" @retry="requestTopic" />
    <article v-else-if="topic" class="topic-detail">
      <header class="public-page__header"><p class="eyebrow">专题</p><h1>{{ topic.name }}</h1><p v-if="topic.description">{{ topic.description }}</p></header>
      <img v-if="topic.coverUrl" class="topic-detail__cover" :src="topic.coverUrl" :alt="`${topic.name}封面`" width="1280" height="560">
      <AppEmpty v-if="topic.articles.length === 0" title="这个专题还没有公开文章" />
      <ol v-else class="ordered-articles">
        <li v-for="article in topic.articles" :key="article.id"><span aria-hidden="true">{{ String(topic.articles.indexOf(article) + 1).padStart(2, '0') }}</span><ArticleCard :article="article" /></li>
      </ol>
    </article>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import type { TopicDetailResponse } from '../../../shared/api/contracts'
import { usePublicRequest } from '../../../shared/lib/public-request'
import { useSeo } from '../../../shared/lib/seo'
import AppEmpty from '../../../shared/ui/AppEmpty.vue'
import AppError from '../../../shared/ui/AppError.vue'
import PublicLoading from '../../../shared/ui/PublicLoading.vue'
import ArticleCard from '../../articles/components/ArticleCard.vue'
import { loadTopic } from '../api'

const route = useRoute()
const slug = computed(() => String(route.params.slug ?? ''))
const request = usePublicRequest<TopicDetailResponse>()
const { data: topic, loading, error } = request
const errorDetail = computed(() => error.value?.traceId ? `${error.value.detail}（追踪编号：${error.value.traceId}）` : error.value?.detail)
function requestTopic(): Promise<void> { return request.run((signal) => loadTopic(slug.value, signal)) }
useSeo(() => ({ title: topic.value ? `${topic.value.name} · 专题 · 小M的思与行` : '专题 · 小M的思与行', description: topic.value?.description || '阅读小M的系列专题。', path: `/topics/${encodeURIComponent(topic.value?.slug ?? slug.value)}`, image: topic.value?.coverUrl }))
watch(slug, () => { void requestTopic() }, { immediate: true })
</script>
