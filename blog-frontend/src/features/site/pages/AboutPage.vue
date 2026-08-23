<template>
  <div class="public-page public-container">
    <PublicLoading v-if="loading" label="正在加载关于页面" />
    <AppError v-else-if="error" :title="error.title" :detail="errorDetail" retry-label="重新加载" @retry="requestProfile" />
    <article v-else-if="profile" class="about-page">
      <img class="about-page__badge" :src="profile.avatarUrl" :alt="`${profile.nickname}的个人标识`" width="240" height="240">
      <div>
        <p class="eyebrow">关于作者</p><h1>关于{{ profile.nickname }}</h1><p class="about-page__bio">{{ profile.bio }}</p><p>{{ profile.subtitle }}</p>
        <a v-if="githubUrl" class="github-link about-page__github" :href="githubUrl" target="_blank" rel="noopener noreferrer"><span>GitHub</span><strong>{{ githubHandle }}</strong><span aria-hidden="true">↗</span></a>
      </div>
    </article>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'

import type { SiteProfileResponse } from '../../../shared/api/contracts'
import { safeHttpsExternalUrl } from '../../../shared/lib/external-url'
import { usePublicRequest } from '../../../shared/lib/public-request'
import { useSeo } from '../../../shared/lib/seo'
import AppError from '../../../shared/ui/AppError.vue'
import PublicLoading from '../../../shared/ui/PublicLoading.vue'
import { loadSiteProfile } from '../api'
import { usePublicProfile } from '../public-profile'

const request = usePublicRequest<SiteProfileResponse>()
const { data: profile, loading, error } = request
const context = usePublicProfile()
const errorDetail = computed(() => error.value?.traceId ? `${error.value.detail}（追踪编号：${error.value.traceId}）` : error.value?.detail)
const githubUrl = computed(() => profile.value ? safeHttpsExternalUrl(profile.value.githubUrl) : null)
const githubHandle = computed(() => githubUrl.value ? `@${new URL(githubUrl.value).pathname.split('/').filter(Boolean)[0] ?? 'GitHub'}` : 'GitHub')
async function requestProfile(): Promise<void> {
  await request.run((signal) => loadSiteProfile(signal))
  if (profile.value) context.update(profile.value)
}
useSeo(() => ({ title: profile.value ? `关于${profile.value.nickname} · ${profile.value.siteTitle}` : '关于小M · 小M的思与行', description: profile.value?.bio || '关于小M与小M的思与行。', path: '/about', image: profile.value?.avatarUrl }))
onMounted(() => { void requestProfile() })
</script>
