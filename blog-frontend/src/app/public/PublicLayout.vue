<template>
  <div class="public-layout">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <SiteHeader />
    <main id="main-content" tabindex="-1">
      <RouterView />
    </main>
    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
import { provide } from 'vue'
import { RouterView } from 'vue-router'
import SiteFooter from '../../features/site/components/SiteFooter.vue'
import SiteHeader from '../../features/site/components/SiteHeader.vue'
import { createPublicProfileContext, publicProfileKey } from '../../features/site/public-profile'

provide(publicProfileKey, createPublicProfileContext())
</script>
