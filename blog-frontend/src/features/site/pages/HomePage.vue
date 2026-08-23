<template>
  <div class="home-page">
    <div v-if="loading" class="home-loading public-container" role="status" aria-label="正在加载首页" aria-live="polite">
      <span class="sr-only">正在加载首页</span>
      <div class="skeleton skeleton--hero" data-skeleton></div>
      <div class="skeleton-grid">
        <div class="skeleton skeleton--feature" data-skeleton></div>
        <div class="skeleton skeleton--card" data-skeleton></div>
        <div class="skeleton skeleton--card" data-skeleton></div>
      </div>
    </div>

    <section v-else-if="error" class="home-error public-container" role="alert">
      <p class="eyebrow">连接暂时中断</p>
      <h1>{{ error.title }}</h1>
      <p>{{ error.detail }}</p>
      <small v-if="error.traceId">追踪编号：{{ error.traceId }}</small>
      <button type="button" @click="requestHome">重新加载</button>
    </section>

    <template v-else-if="home">
      <HeroSection />
      <div class="home-content public-container">
        <FeaturedGrid :featured="home.featuredArticle" :articles="home.latestArticles" />
        <ToolsSection v-if="home.featuredTools.length" :tools="home.featuredTools" />
        <TopicStrip v-if="home.topics.length" :topics="home.topics" />
        <AboutPanel :site="home.site" />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

import type { HomeResponse } from '../../../shared/api/contracts'
import { ApiProblem } from '../../../shared/api/problem'
import AboutPanel from '../components/AboutPanel.vue'
import FeaturedGrid from '../components/FeaturedGrid.vue'
import HeroSection from '../components/HeroSection.vue'
import ToolsSection from '../components/ToolsSection.vue'
import TopicStrip from '../components/TopicStrip.vue'
import { loadHome } from '../api'
import { usePublicProfile } from '../public-profile'

interface DisplayError {
  title: string
  detail: string
  traceId?: string
}

const home = ref<HomeResponse | null>(null)
const loading = ref(true)
const error = ref<DisplayError | null>(null)
const publicProfile = usePublicProfile()
let controller: AbortController | null = null
let requestId = 0

function updateMetadata(data: HomeResponse): void {
  document.title = `${data.site.siteTitle} · ${data.site.subtitle}`
  let description = document.head.querySelector<HTMLMetaElement>('meta[name="description"]')
  if (!description) {
    description = document.createElement('meta')
    description.name = 'description'
    document.head.append(description)
  }
  description.content = '在思考中前行，在记录中成长。记录技术、创造、生活与学习。'
}

function displayError(cause: unknown): DisplayError {
  if (cause instanceof ApiProblem) {
    return {
      title: cause.title || '加载失败',
      detail: cause.detail || '首页内容暂时无法加载。',
      traceId: cause.traceId
    }
  }
  return { title: '加载失败', detail: '首页内容暂时无法加载，请稍后重试。' }
}

async function requestHome(): Promise<void> {
  controller?.abort()
  const activeController = new AbortController()
  controller = activeController
  const currentRequest = ++requestId
  loading.value = true
  error.value = null

  try {
    const response = await loadHome(activeController.signal)
    if (currentRequest !== requestId || activeController.signal.aborted) return
    home.value = response
    publicProfile.update(response.site)
    updateMetadata(response)
  } catch (cause: unknown) {
    if (currentRequest !== requestId || activeController.signal.aborted) return
    home.value = null
    error.value = displayError(cause)
  } finally {
    if (currentRequest === requestId && !activeController.signal.aborted) loading.value = false
  }
}

onMounted(() => {
  void requestHome()
})

onBeforeUnmount(() => {
  requestId += 1
  controller?.abort()
})
</script>
