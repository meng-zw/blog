<template>
  <section class="topic-section" data-section="topics" aria-labelledby="topic-strip-title">
    <div class="section-heading section-heading--row">
      <h2 id="topic-strip-title">探索更多主题</h2>
      <a href="/topics">全部专题 <span aria-hidden="true">→</span></a>
    </div>
    <div class="topic-strip">
      <a v-for="(topic, index) in topics" :key="topic.id" :href="`/topics/${topic.slug}`">
        <span class="topic-strip__mark" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
        <span>
          <strong>{{ topic.name }}</strong>
          <small v-if="topic.description">{{ topic.description }}</small>
        </span>
        <span aria-hidden="true">↗</span>
      </a>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { PublicTopicSummaryResponse } from '../../../shared/api/contracts'

defineProps<{ topics: PublicTopicSummaryResponse[] }>()
</script>
