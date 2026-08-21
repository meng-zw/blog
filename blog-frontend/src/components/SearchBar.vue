<template>
  <div class="search-bar">
    <el-input
      v-model="localKeyword"
      :placeholder="placeholder"
      clearable
      size="large"
      @keyup.enter="handleSearch"
      @clear="handleClear"
    >
      <template #prefix>
        <Icon name="search" size="sm" />
      </template>
    </el-input>
    <el-button type="primary" size="large" @click="handleSearch" :loading="searching">
      搜索
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import Icon from './Icon.vue'

interface Props {
  keyword?: string
  placeholder?: string
}

const props = withDefaults(defineProps<Props>(), {
  keyword: '',
  placeholder: '搜索文章或工具...'
})

const emit = defineEmits<{
  (e: 'search', keyword: string): void
  (e: 'clear'): void
}>()

const localKeyword = ref(props.keyword)
const searching = ref(false)

watch(() => props.keyword, (newVal) => {
  localKeyword.value = newVal
})

const handleSearch = () => {
  if (!localKeyword.value.trim()) return
  searching.value = true
  emit('search', localKeyword.value.trim())
  setTimeout(() => {
    searching.value = false
  }, 300)
}

const handleClear = () => {
  localKeyword.value = ''
  emit('clear')
}
</script>

<style scoped>
.search-bar {
  display: flex;
  gap: var(--space-3);
  max-width: 500px;
}

@media (max-width: 640px) {
  .search-bar {
    max-width: 100%;
  }
}
</style>
