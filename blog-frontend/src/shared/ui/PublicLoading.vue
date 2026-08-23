<template>
  <div class="public-loading" role="status" aria-live="polite">
    <span class="sr-only">{{ label }}</span>
    <span v-for="index in 3" :key="index" aria-hidden="true"></span>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ label?: string }>(), { label: '正在加载内容' })
</script>
