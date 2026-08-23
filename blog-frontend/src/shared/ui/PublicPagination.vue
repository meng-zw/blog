<template>
  <nav v-if="totalPages > 1" class="public-pagination" :aria-label="label">
    <button type="button" :disabled="page <= 0" @click="emit('change', page - 1)">上一页</button>
    <span aria-current="page" aria-live="polite">第 {{ page + 1 }} / {{ totalPages }} 页</span>
    <button type="button" :disabled="page >= totalPages - 1" @click="emit('change', page + 1)">下一页</button>
  </nav>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ page: number; totalPages: number; label?: string }>(), { label: '分页导航' })
const emit = defineEmits<{ change: [page: number] }>()
</script>
