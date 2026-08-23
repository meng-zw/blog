<template>
  <div class="public-page public-container">
    <header class="public-page__header"><p class="eyebrow">成系列地阅读</p><h1>专题</h1><p>沿着一个主题，按清晰次序深入阅读。</p></header>
    <PublicLoading v-if="loading" label="正在加载专题" />
    <AppError v-else-if="error" :title="error.title" :detail="errorDetail" retry-label="重新加载" @retry="requestTopics" />
    <AppEmpty v-else-if="pageData && pageData.items.length === 0" title="还没有公开专题" description="新的系列正在整理中。" />
    <section v-else-if="pageData" class="topic-list" aria-label="专题列表">
      <article v-for="(topic, index) in pageData.items" :key="topic.id">
        <img v-if="topic.coverUrl" :src="topic.coverUrl" :alt="`${topic.name}封面`" width="720" height="405" loading="lazy">
        <p class="story-meta">专题 {{ String(index + 1).padStart(2, '0') }}</p>
        <h2><RouterLink :to="`/topics/${topic.slug}`">{{ topic.name }}</RouterLink></h2>
        <p v-if="topic.description">{{ topic.description }}</p>
      </article>
    </section>
    <PublicPagination v-if="pageData" :page="pageData.page" :total-pages="pageData.totalPages" label="专题分页" @change="changePage" />
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import type { PageResponse, PublicTopicSummaryResponse } from '../../../shared/api/contracts'
import { boundedQueryInt, compactQuery, queryPath, sameQuery } from '../../../shared/lib/public-query'
import { usePublicRequest } from '../../../shared/lib/public-request'
import { useSeo } from '../../../shared/lib/seo'
import AppEmpty from '../../../shared/ui/AppEmpty.vue'
import AppError from '../../../shared/ui/AppError.vue'
import PublicLoading from '../../../shared/ui/PublicLoading.vue'
import PublicPagination from '../../../shared/ui/PublicPagination.vue'
import { listTopics } from '../api'

const route = useRoute()
const router = useRouter()
const request = usePublicRequest<PageResponse<PublicTopicSummaryResponse>>()
const { data: pageData, loading, error } = request
const errorDetail = computed(() => error.value?.traceId ? `${error.value.detail}（追踪编号：${error.value.traceId}）` : error.value?.detail)

function routeState() {
  return {
    page: boundedQueryInt(route.query.page, 0, 0, 1_000_000),
    size: boundedQueryInt(route.query.size, 20, 1, 50)
  }
}

function normalizedQuery(state = routeState()) {
  return compactQuery({ page: state.page, size: state.size })
}

async function requestTopics(): Promise<void> {
  const state = routeState()
  const query = normalizedQuery(state)
  if (!sameQuery(route.query, query)) {
    await router.replace({ path: '/topics', query })
    return
  }
  await request.run((signal) => listTopics(state, signal))
}

function changePage(page: number): void {
  void router.push({ path: '/topics', query: compactQuery({ ...routeState(), page }) })
}

useSeo(() => ({
  title: '专题 · 小M的思与行',
  description: '按主题有序阅读小M的系列文章。',
  path: queryPath('/topics', normalizedQuery())
}))
watch(() => route.fullPath, () => { void requestTopics() }, { immediate: true })
</script>
