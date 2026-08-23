<template>
  <footer class="site-footer">
    <div class="site-footer__inner public-container">
      <div>
        <p class="site-footer__brand">{{ profile.siteTitle }}</p>
        <p class="site-footer__tagline">{{ profile.subtitle }}</p>
      </div>
      <nav aria-label="页脚导航">
        <a href="/articles">文章</a>
        <a href="/topics">专题</a>
        <a href="/tools">工具</a>
        <a href="/about">关于</a>
      </nav>
      <p class="site-footer__copyright">&copy; {{ new Date().getFullYear() }} {{ profile.nickname }}</p>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { usePublicProfile } from '../public-profile'

const { profile } = usePublicProfile()
</script>
