<template>
  <div class="public-page public-container">
    <header class="public-page__header"><p class="eyebrow">少而精的选择</p><h1>工具</h1><p>经实际使用后留下的提效工具与方法。</p></header>
    <form class="filter-form" aria-label="工具筛选" @submit.prevent="submitFilters">
      <label>关键词<input v-model="form.q" name="q" maxlength="100"></label>
      <label>分类<input v-model="form.category" name="category" maxlength="160"></label>
      <label>标签<input v-model="form.tag" name="tag" maxlength="160"></label>
      <button type="submit">筛选</button><button v-if="hasFilters" type="button" @click="clearFilters">清除</button>
    </form>
    <PublicLoading v-if="loading" label="正在加载工具" />
    <AppError v-else-if="error" :title="error.title" :detail="errorDetail" retry-label="重新加载" @retry="requestTools" />
    <AppEmpty v-else-if="pageData && pageData.items.length === 0" title="还没有匹配的工具" description="可以调整筛选条件后再试。" />
    <section v-else-if="pageData" class="tool-list" aria-label="工具列表">
      <article v-for="tool in pageData.items" :key="tool.id">
        <img v-if="tool.coverUrl" :src="tool.coverUrl" :alt="`${tool.name}封面`" width="640" height="360" loading="lazy">
        <p class="story-meta">{{ tool.category?.name || '效率工具' }}</p>
        <h2><RouterLink :to="`/tools/${tool.slug}`">{{ tool.name }}</RouterLink></h2>
        <p>{{ tool.summary }}</p>
        <ul v-if="tool.tags.length" class="tag-list" aria-label="标签"><li v-for="tag in tool.tags" :key="tag.id">{{ tag.name }}</li></ul>
      </article>
    </section>
    <PublicPagination v-if="pageData" :page="pageData.page" :total-pages="pageData.totalPages" @change="changePage" />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import type { PageResponse, ToolSummaryResponse } from '../../../shared/api/contracts'
import { boundedQueryInt, cleanQueryText, compactQuery, queryPath, sameQuery } from '../../../shared/lib/public-query'
import { usePublicRequest } from '../../../shared/lib/public-request'
import { useSeo } from '../../../shared/lib/seo'
import AppEmpty from '../../../shared/ui/AppEmpty.vue'
import AppError from '../../../shared/ui/AppError.vue'
import PublicLoading from '../../../shared/ui/PublicLoading.vue'
import PublicPagination from '../../../shared/ui/PublicPagination.vue'
import { listTools } from '../api'

const route = useRoute()
const router = useRouter()
const form = reactive({ q: '', category: '', tag: '' })
const request = usePublicRequest<PageResponse<ToolSummaryResponse>>()
const { data: pageData, loading, error } = request
const errorDetail = computed(() => error.value?.traceId ? `${error.value.detail}（追踪编号：${error.value.traceId}）` : error.value?.detail)
const hasFilters = computed(() => Boolean(form.q || form.category || form.tag))

function routeState() {
  return {
    q: cleanQueryText(route.query.q, 100), category: cleanQueryText(route.query.category, 160), tag: cleanQueryText(route.query.tag, 160),
    page: boundedQueryInt(route.query.page, 0, 0, 1_000_000), size: boundedQueryInt(route.query.size, 20, 1, 50)
  }
}
function normalizedQuery(state = routeState()) { return compactQuery({ category: state.category, tag: state.tag, q: state.q, page: state.page, size: state.size }) }
async function requestTools(): Promise<void> {
  const state = routeState()
  Object.assign(form, { q: state.q, category: state.category, tag: state.tag })
  const query = normalizedQuery(state)
  if (!sameQuery(route.query, query)) { await router.replace({ path: '/tools', query }); return }
  await request.run((signal) => listTools(state, signal))
}
function submitFilters(): void {
  void router.push({ path: '/tools', query: compactQuery({ q: form.q.normalize('NFKC').trim().slice(0, 100), category: form.category.normalize('NFKC').trim().slice(0, 160), tag: form.tag.normalize('NFKC').trim().slice(0, 160), page: 0, size: routeState().size }) })
}
function clearFilters(): void { Object.assign(form, { q: '', category: '', tag: '' }); submitFilters() }
function changePage(page: number): void { void router.push({ path: '/tools', query: compactQuery({ ...routeState(), page }) }) }
useSeo(() => ({ title: '工具 · 小M的思与行', description: '小M实际使用并推荐的提效工具。', path: queryPath('/tools', normalizedQuery()) }))
watch(() => route.fullPath, () => { void requestTools() }, { immediate: true })
</script>
