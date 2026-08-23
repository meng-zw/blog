<template>
  <div class="public-page public-container">
    <header class="public-page__header">
      <p class="eyebrow">全站查找</p><h1>搜索</h1><p>在文章、随笔、专题和工具中寻找内容。</p>
    </header>
    <form class="search-form" role="search" aria-label="全站搜索" @submit.prevent="submitSearch">
      <label class="sr-only" for="site-search-input">搜索关键词</label>
      <input id="site-search-input" v-model="input" name="q" type="search" maxlength="100" placeholder="输入关键词">
      <button type="submit">搜索</button>
    </form>

    <AppEmpty v-if="!query" title="输入关键词" description="搜索文章、随笔、专题和提效工具。" />
    <PublicLoading v-else-if="loading" label="正在搜索" />
    <AppError v-else-if="error" :title="error.title" :detail="errorDetail" retry-label="重新搜索" @retry="requestSearch" />
    <AppEmpty v-else-if="results && results.items.length === 0" title="没有找到匹配内容" description="换一个关键词试试。" />
    <section v-else-if="results" class="search-results" aria-labelledby="search-results-title">
      <h2 id="search-results-title">“{{ query }}”的搜索结果</h2>
      <article v-for="item in results.items" :key="`${item.type}-${item.id}`">
        <p class="story-meta">{{ typeLabel(item.type) }}</p>
        <h3><RouterLink :to="resultPath(item.type, item.slug)">{{ item.title }}</RouterLink></h3>
        <p v-if="item.summary">{{ item.summary }}</p>
        <time v-if="item.publishedAt" :datetime="item.publishedAt">{{ formatPublicDate(item.publishedAt) }}</time>
      </article>
    </section>
    <PublicPagination v-if="results" :page="results.page" :total-pages="results.totalPages" label="搜索结果分页" @change="changePage" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import type { PageResponse, SearchResultResponse, SearchResultType } from '../../../shared/api/contracts'
import { formatPublicDate } from '../../../shared/lib/date'
import { boundedQueryInt, cleanQueryText, compactQuery, queryPath, sameQuery } from '../../../shared/lib/public-query'
import { usePublicRequest } from '../../../shared/lib/public-request'
import { useSeo } from '../../../shared/lib/seo'
import AppEmpty from '../../../shared/ui/AppEmpty.vue'
import AppError from '../../../shared/ui/AppError.vue'
import PublicLoading from '../../../shared/ui/PublicLoading.vue'
import PublicPagination from '../../../shared/ui/PublicPagination.vue'
import { searchPublic } from '../api'

const route = useRoute()
const router = useRouter()
const input = ref('')
const request = usePublicRequest<PageResponse<SearchResultResponse>>()
const { data: results, loading, error } = request
const query = computed(() => cleanQueryText(route.query.q, 100))
const page = computed(() => boundedQueryInt(route.query.page, 0, 0, 1_000_000))
const size = computed(() => boundedQueryInt(route.query.size, 20, 1, 50))
const errorDetail = computed(() => error.value?.traceId ? `${error.value.detail}（追踪编号：${error.value.traceId}）` : error.value?.detail)

function normalizedQuery() {
  if (!query.value) return compactQuery({})
  return compactQuery({ q: query.value, page: page.value, size: size.value })
}

async function requestSearch(): Promise<void> {
  input.value = query.value
  const normalized = normalizedQuery()
  if (!sameQuery(route.query, normalized)) {
    await router.replace({ path: '/search', query: normalized })
    return
  }
  if (!query.value) {
    request.cancel(true)
    return
  }
  await request.run((signal) => searchPublic({ q: query.value, page: page.value, size: size.value }, signal))
}

function submitSearch(): void {
  const q = input.value.normalize('NFKC').trim().slice(0, 100)
  void router.push({ path: '/search', query: compactQuery({ q, page: 0, size: size.value }) })
}

function changePage(nextPage: number): void {
  void router.push({ path: '/search', query: compactQuery({ q: query.value, page: nextPage, size: size.value }) })
}

function resultPath(type: SearchResultType, slug: string): string {
  if (type === 'TOPIC') return `/topics/${slug}`
  if (type === 'TOOL') return `/tools/${slug}`
  return `/articles/${slug}`
}

function typeLabel(type: SearchResultType): string {
  return { ARTICLE: '文章', NOTE: '随笔', TOPIC: '专题', TOOL: '工具' }[type]
}

useSeo(() => ({
  title: query.value ? `搜索：${query.value} · 小M的思与行` : '搜索 · 小M的思与行',
  description: query.value ? `小M的思与行中与“${query.value}”相关的公开内容。` : '搜索小M的文章、随笔、专题和工具。',
  path: queryPath('/search', normalizedQuery())
}))

watch(() => route.fullPath, () => { void requestSearch() }, { immediate: true })
</script>
