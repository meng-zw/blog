<template>
  <div class="public-page public-container">
    <PublicLoading v-if="loading" label="正在加载工具" />
    <section v-else-if="error?.status === 404" class="not-found-state"><p class="eyebrow">404</p><h1>工具未找到</h1><RouterLink to="/tools">返回工具列表</RouterLink></section>
    <AppError v-else-if="error" :title="error.title" :detail="errorDetail" retry-label="重新加载" @retry="requestTool" />
    <article v-else-if="tool" class="tool-detail">
      <header class="public-page__header">
        <p class="eyebrow">{{ tool.category?.name || '效率工具' }}</p><h1>{{ tool.name }}</h1><p>{{ tool.summary }}</p>
        <a v-if="officialUrl" class="button-link" :href="officialUrl" target="_blank" rel="noopener noreferrer">访问官方网站 <span aria-hidden="true">↗</span></a>
      </header>
      <img v-if="tool.coverUrl" class="tool-detail__cover" :src="tool.coverUrl" :alt="`${tool.name}封面`" width="1280" height="640">
      <ul v-if="tool.tags.length" class="tag-list" aria-label="标签"><li v-for="tag in tool.tags" :key="tag.id">{{ tag.name }}</li></ul>
      <SafeRichContent :html="tool.renderedHtml" />
    </article>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import type { ToolDetailResponse } from '../../../shared/api/contracts'
import { safeHttpsExternalUrl } from '../../../shared/lib/external-url'
import { usePublicRequest } from '../../../shared/lib/public-request'
import { useSeo } from '../../../shared/lib/seo'
import AppError from '../../../shared/ui/AppError.vue'
import PublicLoading from '../../../shared/ui/PublicLoading.vue'
import SafeRichContent from '../../../shared/ui/SafeRichContent.vue'
import { loadTool } from '../api'

const route = useRoute()
const slug = computed(() => String(route.params.slug ?? ''))
const request = usePublicRequest<ToolDetailResponse>()
const { data: tool, loading, error } = request
const errorDetail = computed(() => error.value?.traceId ? `${error.value.detail}（追踪编号：${error.value.traceId}）` : error.value?.detail)
const officialUrl = computed(() => tool.value ? safeHttpsExternalUrl(tool.value.officialUrl) : null)
function requestTool(): Promise<void> { return request.run((signal) => loadTool(slug.value, signal)) }
useSeo(() => ({ title: tool.value ? `${tool.value.name} · 工具 · 小M的思与行` : '工具 · 小M的思与行', description: tool.value?.summary || '小M推荐的提效工具。', path: `/tools/${encodeURIComponent(tool.value?.slug ?? slug.value)}`, image: tool.value?.coverUrl }))
watch(slug, () => { void requestTool() }, { immediate: true })
</script>
