<template>
  <div class="tag-cloud-view">
    <div class="page-header">
      <h1 class="page-title">标签云</h1>
      <p class="page-subtitle">通过标签快速找到感兴趣的文章</p>
    </div>

    <div v-if="loading" class="loading">
      <el-skeleton :rows="4" animated />
    </div>

    <div v-else-if="tags.length > 0" class="tag-cloud">
      <span
        v-for="tag in tags"
        :key="tag.id"
        class="tag-cloud__item"
        :style="{ fontSize: tagFontSize(tag.article_count) }"
        @click="goToTag(tag.id)"
      >
        {{ tag.name }}
        <span class="tag-cloud__count">{{ tag.article_count }}</span>
      </span>
    </div>

    <div v-else class="empty-state">
      <div class="empty-state__icon">
        <Icon name="tag" size="xl" />
      </div>
      <h3 class="empty-state__title">暂无标签</h3>
      <p class="empty-state__description">写文章时添加标签，这里将展示标签云</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from '../utils/axios'
import Icon from '../components/Icon.vue'

const router = useRouter()
const tags = ref<any[]>([])
const loading = ref(false)

const goToTag = (tagId: number) => {
  router.push({ path: '/article', query: { tag_id: String(tagId) } })
}

const tagFontSize = (count: number) => {
  const c = Number(count) || 0
  // 文章数越多字号越大：13px ~ 30px
  const size = Math.min(30, 13 + c * 2)
  return `${size}px`
}

const loadTags = async () => {
  loading.value = true
  try {
    tags.value = await axios.get('/tags/stats')
  } catch (error) {
    console.error('加载标签云失败:', error)
    tags.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTags()
})
</script>

<style scoped>
.tag-cloud-view {
  animation: none;
}

.page-header {
  background-color: transparent;
  border: none;
  border-top: 1px solid var(--color-border-default);
  border-bottom: 1px solid var(--color-border-default);
  border-radius: 0;
  padding: var(--space-8) var(--space-5);
  margin-bottom: var(--space-8);
  text-align: center;
}

.page-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-5);
  font-family: var(--font-family-display);
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2);
  letter-spacing: var(--letter-spacing-wide);
}

.page-title::before,
.page-title::after {
  content: "";
  width: 48px;
  height: 1px;
  background: var(--color-border-strong);
  flex-shrink: 0;
}

.page-subtitle {
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
  margin: 0;
}

.loading {
  margin-bottom: 20px;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: var(--space-4) var(--space-5);
  padding: var(--space-10) var(--space-6);
  background-color: var(--color-bg-primary);
  border-radius: var(--border-radius-lg);
  border: 1px solid var(--color-border-light);
  min-height: 240px;
  align-content: center;
}

.tag-cloud__item {
  display: inline-flex;
  align-items: baseline;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-3);
  color: var(--color-primary-700);
  background-color: var(--color-primary-50);
  border-radius: var(--border-radius-full);
  cursor: pointer;
  line-height: 1.6;
  transition: color var(--transition-fast), background-color var(--transition-fast), transform var(--transition-fast);
}

.tag-cloud__item:hover {
  color: #ffffff;
  background-color: var(--color-primary-600);
  transform: translateY(-2px);
}

.tag-cloud__count {
  font-size: 12px;
  color: var(--color-text-tertiary);
  transition: color var(--transition-fast);
}

.tag-cloud__item:hover .tag-cloud__count {
  color: rgba(255, 255, 255, 0.8);
}

.empty-state {
  text-align: center;
  padding: var(--space-12);
  background-color: var(--color-bg-primary);
  border-radius: var(--border-radius-lg);
  border: 1px dashed var(--color-border-default);
}

.empty-state__icon {
  width: 72px;
  height: 72px;
  margin: 0 auto var(--space-4);
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--color-bg-tertiary);
  border-radius: var(--border-radius-full);
  color: var(--color-text-tertiary);
}

.empty-state__title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2);
}

.empty-state__description {
  color: var(--color-text-secondary);
  margin: 0;
}

@media (max-width: 768px) {
  .page-header {
    padding: var(--space-6) var(--space-4);
  }

  .page-title {
    font-size: var(--font-size-lg);
  }
}
</style>
