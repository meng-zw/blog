<template>
  <section class="public-page public-container not-found-state">
    <p class="eyebrow">404</p><h1>页面未找到</h1><p>你访问的页面不存在，或者已经移动。</p><RouterLink class="button-link" to="/">返回首页</RouterLink>
  </section>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useSeo } from '../../shared/lib/seo'

useSeo(() => ({ title: '页面未找到 · 小M的思与行', description: '你访问的页面不存在。', path: window.location.pathname }))
</script>
