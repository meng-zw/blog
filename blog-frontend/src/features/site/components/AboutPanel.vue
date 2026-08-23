<template>
  <section class="about-panel" aria-labelledby="about-title">
    <img class="about-panel__avatar" :src="site.avatarUrl" :alt="`${site.nickname}的个人标识`" width="144" height="144" loading="lazy">
    <div class="about-panel__copy">
      <p class="eyebrow">记录者</p>
      <h2 id="about-title">关于{{ site.nickname }}</h2>
      <p>{{ site.bio }}</p>
    </div>
    <a class="github-link" :href="site.githubUrl" target="_blank" rel="noopener noreferrer">
      <span>GitHub</span>
      <strong>{{ githubHandle }}</strong>
      <span aria-hidden="true">↗</span>
    </a>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { SiteProfileResponse } from '../../../shared/api/contracts'

const props = defineProps<{ site: SiteProfileResponse }>()

const githubHandle = computed(() => {
  try {
    const handle = new URL(props.site.githubUrl).pathname.split('/').filter(Boolean).at(0)
    return handle ? `@${handle}` : 'GitHub 主页'
  } catch {
    return 'GitHub 主页'
  }
})
</script>
