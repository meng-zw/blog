<template>
  <nav v-if="items.length" class="article-toc" aria-label="文章目录">
    <p>目录</p>
    <ol>
      <li v-for="item in items" :key="item.id" :class="`article-toc__level-${item.level}`">
        <a :href="`#${item.id}`">{{ item.text }}</a>
      </li>
    </ol>
  </nav>
</template>

<script setup lang="ts">
import type { TocItem } from '../../../shared/lib/rich-content'

defineProps<{ items: TocItem[] }>()
</script>
