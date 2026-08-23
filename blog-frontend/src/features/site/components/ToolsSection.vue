<template>
  <section class="tools-section" data-section="tools" aria-labelledby="tools-title">
    <div class="section-heading section-heading--row">
      <div>
        <p class="eyebrow">小M 在用</p>
        <h2 id="tools-title">推荐工具</h2>
      </div>
      <a href="/tools">工具库 <span aria-hidden="true">→</span></a>
    </div>
    <div class="tool-grid">
      <article v-for="tool in tools" :key="tool.id">
        <p>{{ tool.category?.name ?? '效率工具' }}</p>
        <h3><a :href="`/tools/${tool.slug}`">{{ tool.name }}</a></h3>
        <span>{{ tool.summary }}</span>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { ToolSummaryResponse } from '../../../shared/api/contracts'

defineProps<{ tools: ToolSummaryResponse[] }>()
</script>
