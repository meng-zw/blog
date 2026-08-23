<template>
  <div ref="root" class="rich-content" v-html="html"></div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

const props = defineProps<{ html: string }>()
const root = ref<HTMLElement | null>(null)

function hardenLinks(): void {
  const origin = window.location.origin
  root.value?.querySelectorAll<HTMLAnchorElement>('a[href]').forEach((anchor) => {
    let url: URL
    try {
      url = new URL(anchor.getAttribute('href') ?? '', window.location.href)
    } catch {
      return
    }
    if (!['http:', 'https:'].includes(url.protocol) || url.origin === origin) return
    anchor.target = '_blank'
    anchor.rel = 'noopener noreferrer'
  })
}

onMounted(hardenLinks)
watch(() => props.html, hardenLinks, { flush: 'post' })
</script>

<style scoped>
.rich-content {
  min-width: 0;
  color: var(--ink);
  font-size: 17px;
  line-height: 1.9;
  overflow-wrap: anywhere;
}

.rich-content :deep(h2),
.rich-content :deep(h3) {
  scroll-margin-top: calc(var(--header-height) + 24px);
  line-height: 1.45;
}

.rich-content :deep(h2) { margin: 2.5em 0 0.8em; font-size: 1.65em; }
.rich-content :deep(h3) { margin: 2em 0 0.7em; font-size: 1.3em; }
.rich-content :deep(a) { color: var(--accent-dark); text-decoration: underline; text-underline-offset: 0.2em; }
.rich-content :deep(img) { max-width: 100%; height: auto; margin: 1.5em auto; }
.rich-content :deep(pre) { max-width: 100%; padding: 1.1em; overflow-x: auto; background: #2d2925; color: #f8f3eb; }
.rich-content :deep(code) { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; overflow-wrap: normal; }
.rich-content :deep(:not(pre) > code) { padding: 0.12em 0.32em; background: #ebe1d4; }
.rich-content :deep(table) { display: block; width: 100%; max-width: 100%; overflow-x: auto; border-collapse: collapse; }
.rich-content :deep(th),
.rich-content :deep(td) { padding: 0.55em 0.7em; border: 1px solid var(--border); white-space: nowrap; }
.rich-content :deep(blockquote) { margin-inline: 0; padding-left: 1.2em; border-left: 3px solid var(--accent); color: var(--ink-secondary); }
</style>
