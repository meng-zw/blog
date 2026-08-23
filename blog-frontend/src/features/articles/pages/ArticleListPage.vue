<template>
  <div class="public-page public-container">
    <header class="public-page__header">
      <p class="eyebrow">{{ isNotes ? '片段与日常' : '持续写作' }}</p>
      <h1>{{ isNotes ? '随笔' : '文章' }}</h1>
      <p>{{ isNotes ? '记录尚未长成体系的念头。' : '关于技术、工作方法与长期思考。' }}</p>
    </header>

    <form class="filter-form" aria-label="内容筛选" @submit.prevent="submitFilters">
      <label>关键词<input v-model="form.q" name="q" maxlength="100"></label>
      <label>分类<input v-model="form.category" name="category" maxlength="160"></label>
      <label>标签<input v-model="form.tag" name="tag" maxlength="160"></label>
      <button type="submit">筛选</button>
      <button v-if="hasFilters" type="button" @click="clearFilters">清除</button>
    </form>

    <PublicLoading v-if="loading" :label="`正在加载${isNotes ? '随笔' : '文章'}`" />
    <AppError v-else-if="error" :title="error.title" :detail="errorDetail" retry-label="重新加载" @retry="requestList" />
    <AppEmpty v-else-if="pageData && pageData.items.length === 0" :title="isNotes ? '还没有公开随笔' : '还没有匹配的文章'" description="可以调整筛选条件后再试。" />
    <section v-else-if="pageData" class="article-list" :aria-label="isNotes ? '随笔列表' : '文章列表'">
      <ArticleCard v-for="article in pageData.items" :key="article.id" :article="article" />
    </section>
    <PublicPagination v-if="pageData" :page="pageData.page" :total-pages="pageData.totalPages" @change="changePage" />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { ContentType, PageResponse, ArticleSummaryResponse } from '../../../shared/api/contracts'
import { boundedQueryInt, cleanQueryText, compactQuery, queryPath, sameQuery } from '../../../shared/lib/public-query'
import { usePublicRequest } from '../../../shared/lib/public-request'
import { useSeo } from '../../../shared/lib/seo'
import AppEmpty from '../../../shared/ui/AppEmpty.vue'
import AppError from '../../../shared/ui/AppError.vue'
import PublicLoading from '../../../shared/ui/PublicLoading.vue'
import PublicPagination from '../../../shared/ui/PublicPagination.vue'
import ArticleCard from '../components/ArticleCard.vue'
import { listArticles } from '../api'

const route = useRoute()
const router = useRouter()
const isNotes = computed(() => route.path === '/notes')
const contentType = computed<ContentType>(() => isNotes.value ? 'NOTE' : 'ARTICLE')
const form = reactive({ q: '', category: '', tag: '' })
const request = usePublicRequest<PageResponse<ArticleSummaryResponse>>()
const { data: pageData, loading, error } = request
const errorDetail = computed(() => error.value?.traceId ? `${error.value.detail}（追踪编号：${error.value.traceId}）` : error.value?.detail)
const hasFilters = computed(() => Boolean(form.q || form.category || form.tag))

function routeState() {
  return {
    q: cleanQueryText(route.query.q, 100),
    category: cleanQueryText(route.query.category, 160),
    tag: cleanQueryText(route.query.tag, 160),
    page: boundedQueryInt(route.query.page, 0, 0, 1_000_000),
    size: boundedQueryInt(route.query.size, 20, 1, 50)
  }
}

function normalizedQuery(state = routeState()) {
  return compactQuery({ category: state.category, tag: state.tag, q: state.q, page: state.page, size: state.size })
}

async function requestList(): Promise<void> {
  const state = routeState()
  form.q = state.q
  form.category = state.category
  form.tag = state.tag
  const query = normalizedQuery(state)
  if (!sameQuery(route.query, query)) {
    await router.replace({ path: route.path, query })
    return
  }
  await request.run((signal) => listArticles({ ...state, contentType: contentType.value }, signal))
}

function submitFilters(): void {
  void router.push({ path: route.path, query: compactQuery({
    category: form.category.normalize('NFKC').trim().slice(0, 160),
    tag: form.tag.normalize('NFKC').trim().slice(0, 160),
    q: form.q.normalize('NFKC').trim().slice(0, 100), page: 0, size: routeState().size
  }) })
}

function clearFilters(): void {
  Object.assign(form, { q: '', category: '', tag: '' })
  submitFilters()
}

function changePage(page: number): void {
  const state = routeState()
  void router.push({ path: route.path, query: compactQuery({ ...state, page }) })
}

useSeo(() => ({
  title: `${isNotes.value ? '随笔' : '文章'} · 小M的思与行`,
  description: isNotes.value ? '小M的随笔与日常思考。' : '小M关于技术、工作方法与长期思考的文章。',
  path: queryPath(route.path, normalizedQuery())
}))

watch(() => route.fullPath, () => { void requestList() }, { immediate: true })
</script>
